package com.bruce.docai.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        Jwt jwt,
        Cookie cookie,
        Bootstrap bootstrap
) {

    public record Jwt(
            String secret,
            long accessTokenSeconds,
            long refreshTokenSeconds,
            long inviteTokenSeconds
    ) {
    }

    public record Cookie(
            boolean secure,
            String sameSite,
            String domain
    ) {
    }

    public record Bootstrap(
            boolean enabled,
            String email,
            String password,
            String name,
            String organizationId
    ) {
    }
}

