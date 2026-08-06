package com.bruce.docai.security.service;

import com.bruce.docai.model.RefreshToken;
import com.bruce.docai.model.User;
import com.bruce.docai.repository.RefreshTokenRepository;
import com.bruce.docai.security.config.SecurityProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenHashingService tokenHashingService;
    private final SecurityProperties securityProperties;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            TokenHashingService tokenHashingService,
            SecurityProperties securityProperties
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenHashingService = tokenHashingService;
        this.securityProperties = securityProperties;
    }

    @Transactional
    public String create(User user) {
        String rawToken = UUID.randomUUID().toString() + UUID.randomUUID();

        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(tokenHashingService.sha256(rawToken));
        token.setExpiresAt(Instant.now().plusSeconds(securityProperties.jwt().refreshTokenSeconds()));

        refreshTokenRepository.save(token);
        return rawToken;
    }

    @Transactional(readOnly = true)
    public Optional<RefreshToken> findValidByRawToken(String rawToken) {
        String hash = tokenHashingService.sha256(rawToken);
        return refreshTokenRepository.findByTokenHash(hash)
                .filter(token -> token.isUsableAt(Instant.now()));
    }

    @Transactional
    public String rotate(RefreshToken previousToken) {
        String newRawToken = UUID.randomUUID().toString() + UUID.randomUUID();
        String newHash = tokenHashingService.sha256(newRawToken);

        previousToken.setRevokedAt(Instant.now());
        previousToken.setReplacedByHash(newHash);
        refreshTokenRepository.save(previousToken);

        RefreshToken newToken = new RefreshToken();
        newToken.setUser(previousToken.getUser());
        newToken.setTokenHash(newHash);
        newToken.setExpiresAt(Instant.now().plusSeconds(securityProperties.jwt().refreshTokenSeconds()));
        refreshTokenRepository.save(newToken);

        return newRawToken;
    }

    @Transactional
    public void revokeByRawToken(String rawToken) {
        String hash = tokenHashingService.sha256(rawToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(Instant.now());
                refreshTokenRepository.save(token);
            }
        });
    }

    @Transactional
    public void revokeAllForUser(String userId) {
        List<RefreshToken> activeTokens = refreshTokenRepository.findAllByUser_IdAndRevokedAtIsNull(userId);
        Instant now = Instant.now();
        activeTokens.forEach(token -> token.setRevokedAt(now));
        refreshTokenRepository.saveAll(activeTokens);
    }
}

