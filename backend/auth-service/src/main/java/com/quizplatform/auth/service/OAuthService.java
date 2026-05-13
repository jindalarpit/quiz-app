package com.quizplatform.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizplatform.auth.config.OAuthProperties;
import com.quizplatform.auth.dto.AuthResponse;
import com.quizplatform.auth.model.RefreshToken;
import com.quizplatform.auth.model.User;
import com.quizplatform.auth.model.UserRole;
import com.quizplatform.auth.repository.RefreshTokenRepository;
import com.quizplatform.auth.repository.UserRepository;
import com.quizplatform.common.dto.UserDTO;
import com.quizplatform.common.exception.UnauthorizedException;
import com.quizplatform.common.exception.ValidationException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OAuthProperties oAuthProperties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration-ms}")
    private long jwtExpirationMs;

    @Value("${jwt.refresh-expiration-days}")
    private int refreshExpirationDays;

    /**
     * Generates the OAuth authorization URL for the given provider.
     *
     * @param provider the OAuth provider (google or github)
     * @return the authorization URL to redirect the user to
     */
    public String getAuthorizationUrl(String provider) {
        OAuthProperties.ProviderConfig config = oAuthProperties.getProvider(provider);

        String url = config.getAuthUri()
                + "?client_id=" + config.getClientId()
                + "&redirect_uri=" + config.getRedirectUri()
                + "&scope=" + config.getScope()
                + "&response_type=code"
                + "&state=" + provider;

        if ("google".equalsIgnoreCase(provider)) {
            url += "&access_type=offline&prompt=consent";
        }

        return url;
    }

    /**
     * Handles the OAuth callback by exchanging the authorization code for an access token,
     * fetching the user profile, and creating or linking the user account.
     *
     * @param provider    the OAuth provider (google or github)
     * @param code        the authorization code from the OAuth provider
     * @param redirectUri the redirect URI used in the authorization request (optional override)
     * @return AuthResponse with access token, refresh token, and user info
     */
    @Transactional
    public AuthResponse handleOAuthCallback(String provider, String code, String redirectUri) {
        OAuthProperties.ProviderConfig config = oAuthProperties.getProvider(provider);

        String effectiveRedirectUri = (redirectUri != null && !redirectUri.isBlank())
                ? redirectUri
                : config.getRedirectUri();

        // Exchange authorization code for access token
        String accessToken = exchangeCodeForToken(provider, code, effectiveRedirectUri, config);

        // Fetch user profile from provider
        OAuthUserProfile profile = fetchUserProfile(provider, accessToken, config);

        // Create or link user account
        User user = findOrCreateUser(provider, profile);

        // Generate JWT access token
        String jwtAccessToken = generateAccessToken(user);

        // Generate refresh token
        String refreshToken = UUID.randomUUID().toString();
        String refreshTokenHash = hashToken(refreshToken);

        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .user(user)
                .tokenHash(refreshTokenHash)
                .expiresAt(Instant.now().plus(refreshExpirationDays, ChronoUnit.DAYS))
                .build();

        refreshTokenRepository.save(refreshTokenEntity);

        UserDTO userDTO = UserDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .role(user.getRole().name())
                .build();

        return AuthResponse.builder()
                .accessToken(jwtAccessToken)
                .refreshToken(refreshToken)
                .user(userDTO)
                .build();
    }

    /**
     * Exchanges the authorization code for an access token from the OAuth provider.
     */
    private String exchangeCodeForToken(String provider, String code, String redirectUri,
                                         OAuthProperties.ProviderConfig config) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        if ("github".equalsIgnoreCase(provider)) {
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        }

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", config.getClientId());
        body.add("client_secret", config.getClientSecret());
        body.add("code", code);
        body.add("redirect_uri", redirectUri);
        body.add("grant_type", "authorization_code");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    config.getTokenUri(),
                    HttpMethod.POST,
                    request,
                    String.class
            );

            JsonNode jsonNode = objectMapper.readTree(response.getBody());

            if (jsonNode.has("error")) {
                String error = jsonNode.get("error").asText();
                String description = jsonNode.has("error_description")
                        ? jsonNode.get("error_description").asText()
                        : "Unknown error";
                log.error("OAuth token exchange failed for provider {}: {} - {}", provider, error, description);
                throw new UnauthorizedException("OAuth authentication failed: " + description);
            }

            return jsonNode.get("access_token").asText();
        } catch (UnauthorizedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to exchange OAuth code for token with provider {}", provider, e);
            throw new UnauthorizedException("OAuth authentication failed: unable to exchange code for token");
        }
    }

    /**
     * Fetches the user profile from the OAuth provider using the access token.
     */
    private OAuthUserProfile fetchUserProfile(String provider, String accessToken,
                                               OAuthProperties.ProviderConfig config) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    config.getUserInfoUri(),
                    HttpMethod.GET,
                    request,
                    String.class
            );

            JsonNode jsonNode = objectMapper.readTree(response.getBody());

            return switch (provider.toLowerCase()) {
                case "google" -> parseGoogleProfile(jsonNode);
                case "github" -> parseGitHubProfile(jsonNode, accessToken);
                default -> throw new IllegalArgumentException("Unsupported provider: " + provider);
            };
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to fetch user profile from provider {}", provider, e);
            throw new UnauthorizedException("OAuth authentication failed: unable to fetch user profile");
        }
    }

    private OAuthUserProfile parseGoogleProfile(JsonNode jsonNode) {
        String providerId = jsonNode.get("sub").asText();
        String email = jsonNode.get("email").asText();
        String name = jsonNode.has("name") ? jsonNode.get("name").asText() : email.split("@")[0];

        return new OAuthUserProfile(providerId, email, name);
    }

    private OAuthUserProfile parseGitHubProfile(JsonNode jsonNode, String accessToken) {
        String providerId = String.valueOf(jsonNode.get("id").asLong());
        String name = jsonNode.has("name") && !jsonNode.get("name").isNull()
                ? jsonNode.get("name").asText()
                : jsonNode.get("login").asText();

        // GitHub may not return email in the user endpoint; fetch from emails API
        String email = null;
        if (jsonNode.has("email") && !jsonNode.get("email").isNull()) {
            email = jsonNode.get("email").asText();
        }

        if (email == null) {
            email = fetchGitHubEmail(accessToken);
        }

        if (email == null) {
            throw new UnauthorizedException("OAuth authentication failed: unable to retrieve email from GitHub. " +
                    "Please ensure your GitHub email is public or grant the user:email scope.");
        }

        return new OAuthUserProfile(providerId, email, name);
    }

    private String fetchGitHubEmail(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    "https://api.github.com/user/emails",
                    HttpMethod.GET,
                    request,
                    String.class
            );

            JsonNode emails = objectMapper.readTree(response.getBody());
            for (JsonNode emailNode : emails) {
                if (emailNode.get("primary").asBoolean() && emailNode.get("verified").asBoolean()) {
                    return emailNode.get("email").asText();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch GitHub emails", e);
        }

        return null;
    }

    /**
     * Finds an existing user or creates a new one based on the OAuth profile.
     * Implements account linking logic:
     * - If a user with the same OAuth provider+ID exists, return that user
     * - If a user with the same email exists (registered via email/password), link the OAuth provider
     * - If the OAuth provider is already linked to a different account, throw an error
     * - If no user exists, create a new user with the OAuth provider info
     */
    private User findOrCreateUser(String provider, OAuthUserProfile profile) {
        // Check if user already exists with this OAuth provider and ID
        Optional<User> existingOAuthUser = userRepository.findByOauthProviderAndOauthProviderId(
                provider, profile.providerId());

        if (existingOAuthUser.isPresent()) {
            User user = existingOAuthUser.get();
            if (user.getDeletedAt() != null) {
                throw new UnauthorizedException("Account has been deactivated");
            }
            return user;
        }

        // Check if a user with the same email exists
        Optional<User> existingEmailUser = userRepository.findByEmail(profile.email());

        if (existingEmailUser.isPresent()) {
            User user = existingEmailUser.get();

            if (user.getDeletedAt() != null) {
                throw new UnauthorizedException("Account has been deactivated");
            }

            // If the user already has a different OAuth provider linked, return error
            if (user.getOauthProvider() != null && !user.getOauthProvider().equals(provider)) {
                throw new ValidationException(
                        "This email is already linked to a different OAuth provider: " + user.getOauthProvider(),
                        List.of("Please sign in using " + user.getOauthProvider() + " or your email/password")
                );
            }

            // Link the OAuth provider to the existing account
            user.setOauthProvider(provider);
            user.setOauthProviderId(profile.providerId());
            return userRepository.save(user);
        }

        // Create a new user
        User newUser = User.builder()
                .email(profile.email())
                .displayName(profile.displayName())
                .role(UserRole.HOST)
                .oauthProvider(provider)
                .oauthProviderId(profile.providerId())
                .build();

        return userRepository.save(newUser);
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

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Internal record representing a user profile fetched from an OAuth provider.
     */
    record OAuthUserProfile(String providerId, String email, String displayName) {}
}
