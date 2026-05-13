package com.quizplatform.auth.controller;

import com.quizplatform.auth.dto.AuthResponse;
import com.quizplatform.auth.dto.OAuthCallbackRequest;
import com.quizplatform.auth.service.OAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/oauth")
@RequiredArgsConstructor
public class OAuthController {

    private final OAuthService oAuthService;

    /**
     * Returns the authorization URL for the given OAuth provider.
     * The client should redirect the user to this URL to initiate the OAuth flow.
     *
     * @param provider the OAuth provider (google or github)
     * @return 302 redirect to the provider's authorization page
     */
    @GetMapping("/{provider}")
    public ResponseEntity<Void> initiateOAuth(@PathVariable String provider) {
        String authorizationUrl = oAuthService.getAuthorizationUrl(provider);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, authorizationUrl)
                .build();
    }

    /**
     * Handles the OAuth callback from the provider via POST.
     * Exchanges the authorization code for tokens, fetches user profile,
     * creates or links the user account, and returns JWT tokens.
     *
     * @param provider the OAuth provider (google or github)
     * @param request  the callback request containing the authorization code and optional redirect URI
     * @return AuthResponse with access token, refresh token, and user info
     */
    @PostMapping("/{provider}/callback")
    public ResponseEntity<AuthResponse> handleOAuthCallbackPost(
            @PathVariable String provider,
            @Valid @RequestBody OAuthCallbackRequest request) {
        AuthResponse response = oAuthService.handleOAuthCallback(provider, request.getCode(), request.getRedirectUri());
        return ResponseEntity.ok(response);
    }

    /**
     * Handles the OAuth callback from the provider via GET (browser redirect).
     * Exchanges the authorization code for tokens, fetches user profile,
     * creates or links the user account, and returns JWT tokens.
     *
     * @param provider    the OAuth provider (google or github)
     * @param code        the authorization code from the provider
     * @param redirectUri optional redirect URI override
     * @return AuthResponse with access token, refresh token, and user info
     */
    @GetMapping("/{provider}/callback")
    public ResponseEntity<AuthResponse> handleOAuthCallbackGet(
            @PathVariable String provider,
            @RequestParam String code,
            @RequestParam(required = false) String redirectUri) {
        AuthResponse response = oAuthService.handleOAuthCallback(provider, code, redirectUri);
        return ResponseEntity.ok(response);
    }
}
