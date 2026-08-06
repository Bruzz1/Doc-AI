package com.bruce.docai.security;

import com.bruce.docai.model.User;
import com.bruce.docai.security.config.SecurityProperties;
import com.bruce.docai.security.service.JwtService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    @Test
    void generatesAndValidatesAccessToken() {
        SecurityProperties properties = new SecurityProperties(
                new SecurityProperties.Jwt(
                        "1234567890123456789012345678901234567890",
                        300,
                        86400,
                        604800
                ),
                new SecurityProperties.Cookie(true, "Lax", ""),
                new SecurityProperties.Bootstrap(false, "", "", "", "")
        );

        JwtService jwtService = new JwtService(properties);
        User user = new User();
        user.setEmail("user@example.com");
        user.setOrganizationId("org-1");
        user.setRole("USER");

        String token = jwtService.generateAccessToken(user);

        assertTrue(jwtService.isAccessTokenValid(token));
        assertEquals("user@example.com", jwtService.extractEmail(token).orElseThrow());
    }
}


