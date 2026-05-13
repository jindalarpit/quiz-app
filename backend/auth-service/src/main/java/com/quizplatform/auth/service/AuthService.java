package com.quizplatform.auth.service;

import com.quizplatform.auth.dto.AuthResponse;
import com.quizplatform.auth.dto.LoginRequest;
import com.quizplatform.auth.dto.RefreshRequest;
import com.quizplatform.auth.dto.RegisterRequest;
import com.quizplatform.auth.model.RefreshToken;
import com.quizplatform.auth.model.User;
import com.quizplatform.auth.model.UserRole;
import com.quizplatform.auth.repository.RefreshTokenRepository;
import com.quizplatform.auth.repository.UserRepository;
import com.quizplatform.common.dto.UserDTO;
import com.quizplatform.common.exception.DuplicateResourceException;
import com.quizplatform.common.exception.ResourceNotFoundException;
import com.quizplatform.common.exception.UnauthorizedException;
import com.quizplatform.common.exception.ValidationException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration-ms}")
    private long jwtExpirationMs;

    @Value("${jwt.refresh-expiration-days}")
    private int refreshExpirationDays;

    /**
     * Registers a new user with email/password authentication.
     *
     * @param request the registration request containing email, password, and display name
     * @return AuthResponse with access token, refresh token, and user info
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Check if email is already registered
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("User", request.getEmail());
        }

        // Validate password strength
        List<String> passwordErrors = PasswordValidator.validate(request.getPassword());
        if (!passwordErrors.isEmpty()) {
            throw new ValidationException("Password does not meet requirements", passwordErrors);
        }

        // Hash password with BCrypt (cost factor 12 configured in SecurityConfig)
        String passwordHash = passwordEncoder.encode(request.getPassword());

        // Create user entity with HOST role
        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordHash)
                .displayName(request.getDisplayName())
                .role(UserRole.HOST)
                .build();

        user = userRepository.save(user);

        // Generate JWT access token
        String accessToken = generateAccessToken(user);

        // Generate refresh token, hash with SHA-256, and store in DB
        String refreshToken = UUID.randomUUID().toString();
        String refreshTokenHash = hashToken(refreshToken);

        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .user(user)
                .tokenHash(refreshTokenHash)
                .expiresAt(Instant.now().plus(refreshExpirationDays, ChronoUnit.DAYS))
                .build();

        refreshTokenRepository.save(refreshTokenEntity);

        // Build response
        UserDTO userDTO = UserDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .role(user.getRole().name())
                .build();

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(userDTO)
                .build();
    }

    /**
     * Authenticates a user with email and password.
     *
     * @param request the login request containing email and password
     * @return AuthResponse with access token, refresh token, and user info
     * @throws UnauthorizedException if credentials are invalid or user is soft-deleted
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        // Find user by email — generic error to prevent user enumeration
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        // Verify password matches hash
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid credentials");
        }

        // Check user is not soft-deleted
        if (user.getDeletedAt() != null) {
            throw new UnauthorizedException("Invalid credentials");
        }

        // Generate JWT access token
        String accessToken = generateAccessToken(user);

        // Generate refresh token, hash with SHA-256, and store in DB
        String refreshToken = UUID.randomUUID().toString();
        String refreshTokenHash = hashToken(refreshToken);

        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .user(user)
                .tokenHash(refreshTokenHash)
                .expiresAt(Instant.now().plus(refreshExpirationDays, ChronoUnit.DAYS))
                .build();

        refreshTokenRepository.save(refreshTokenEntity);

        // Build response
        UserDTO userDTO = UserDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .role(user.getRole().name())
                .build();

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(userDTO)
                .build();
    }

    /**
     * Refreshes an access token using a valid refresh token.
     * Implements token rotation: the old refresh token is revoked and a new one is issued.
     *
     * @param request the refresh request containing the refresh token
     * @return AuthResponse with new access token, new refresh token, and user info
     * @throws UnauthorizedException if the refresh token is invalid, expired, or revoked
     */
    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        String tokenHash = hashToken(request.getRefreshToken());

        // Look up the refresh token by its SHA-256 hash
        RefreshToken existingToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired refresh token"));

        // Check if already revoked
        if (existingToken.getRevokedAt() != null) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        // Check if expired
        if (existingToken.getExpiresAt().isBefore(Instant.now())) {
            // Revoke the expired token
            existingToken.setRevokedAt(Instant.now());
            refreshTokenRepository.save(existingToken);
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        // Revoke the old token (rotation)
        existingToken.setRevokedAt(Instant.now());
        refreshTokenRepository.save(existingToken);

        User user = existingToken.getUser();

        // Generate new access token
        String accessToken = generateAccessToken(user);

        // Generate new refresh token, hash with SHA-256, and store in DB (rotation)
        String newRefreshToken = UUID.randomUUID().toString();
        String newRefreshTokenHash = hashToken(newRefreshToken);

        RefreshToken newRefreshTokenEntity = RefreshToken.builder()
                .user(user)
                .tokenHash(newRefreshTokenHash)
                .expiresAt(Instant.now().plus(refreshExpirationDays, ChronoUnit.DAYS))
                .build();

        refreshTokenRepository.save(newRefreshTokenEntity);

        // Build response
        UserDTO userDTO = UserDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .role(user.getRole().name())
                .build();

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(newRefreshToken)
                .user(userDTO)
                .build();
    }

    /**
     * Retrieves the current authenticated user's profile.
     *
     * @param userId the UUID of the authenticated user
     * @return UserDTO with id, email, displayName, role, and createdAt
     * @throws ResourceNotFoundException if the user does not exist
     */
    public UserDTO getMe(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));

        return UserDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .role(user.getRole().name())
                .createdAt(user.getCreatedAt())
                .build();
    }

    /**
     * GDPR deletion request: soft-deletes the user and revokes all active refresh tokens.
     * The user will be permanently deleted after 30 days by the scheduled DeletionService.
     *
     * @param userId the UUID of the user requesting deletion
     * @throws ResourceNotFoundException if the user does not exist
     */
    @Transactional
    public void requestDeletion(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));

        // Soft-delete: set deletedAt timestamp
        user.setDeletedAt(Instant.now());
        userRepository.save(user);

        // Revoke all active refresh tokens for this user
        List<RefreshToken> activeTokens = refreshTokenRepository.findByUserAndRevokedAtIsNull(user);
        activeTokens.forEach(token -> token.setRevokedAt(Instant.now()));
        refreshTokenRepository.saveAll(activeTokens);
    }

    private String generateAccessToken(User user) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(jwtExpirationMs);

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    /**
     * Hashes a token using SHA-256 and returns the hex digest.
     * SHA-256 is appropriate for refresh tokens since they are high-entropy random values,
     * unlike passwords which require slow hashing (BCrypt). This enables direct DB lookup.
     *
     * @param token the raw token string
     * @return the SHA-256 hex digest of the token
     */
    String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
