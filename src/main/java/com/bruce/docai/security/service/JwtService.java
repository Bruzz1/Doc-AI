package com.bruce.docai.security.service;

import com.bruce.docai.model.User;
import com.bruce.docai.security.config.SecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final SecurityProperties securityProperties;

    public JwtService(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
        this.signingKey = Keys.hmacShaKeyFor(securityProperties.jwt().secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(User user) {
        Date issuedAt = new Date();
        Date expiry = new Date(System.currentTimeMillis() + securityProperties.jwt().accessTokenSeconds() * 1000);

        return Jwts.builder()
                .setSubject(user.getEmail())
                .claim("orgId", user.getOrganizationId())
                .claim("role", user.getRole())
                .claim("token_type", "access")
                .setIssuedAt(issuedAt)
                .setExpiration(expiry)
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public Optional<String> extractEmail(String token) {
        return parseClaims(token).map(Claims::getSubject);
    }

    public boolean isAccessTokenValid(String token) {
        Optional<Claims> claims = parseClaims(token);
        return claims
                .filter(parsed -> "access".equals(parsed.get("token_type", String.class)))
                .filter(parsed -> parsed.getExpiration().after(new Date()))
                .isPresent();
    }

    private Optional<Claims> parseClaims(String token) {
        try {
            return Optional.of(Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody());
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}

