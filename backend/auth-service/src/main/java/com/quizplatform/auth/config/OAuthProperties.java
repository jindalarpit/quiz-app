package com.quizplatform.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "oauth")
public class OAuthProperties {

    private ProviderConfig google = new ProviderConfig();
    private ProviderConfig github = new ProviderConfig();

    @Data
    public static class ProviderConfig {
        private String clientId;
        private String clientSecret;
        private String redirectUri;
        private String authUri;
        private String tokenUri;
        private String userInfoUri;
        private String scope;
    }

    public ProviderConfig getProvider(String providerName) {
        return switch (providerName.toLowerCase()) {
            case "google" -> google;
            case "github" -> github;
            default -> throw new IllegalArgumentException("Unsupported OAuth provider: " + providerName);
        };
    }
}
