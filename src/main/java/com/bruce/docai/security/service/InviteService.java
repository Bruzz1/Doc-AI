package com.bruce.docai.security.service;

import com.bruce.docai.model.InviteToken;
import com.bruce.docai.repository.InviteTokenRepository;
import com.bruce.docai.security.config.SecurityProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class InviteService {

    private final InviteTokenRepository inviteTokenRepository;
    private final TokenHashingService tokenHashingService;
    private final SecurityProperties securityProperties;

    public InviteService(
            InviteTokenRepository inviteTokenRepository,
            TokenHashingService tokenHashingService,
            SecurityProperties securityProperties
    ) {
        this.inviteTokenRepository = inviteTokenRepository;
        this.tokenHashingService = tokenHashingService;
        this.securityProperties = securityProperties;
    }

    @Transactional
    public String createInvite(String email, String organizationId, String role, String createdBy) {
        String rawToken = UUID.randomUUID().toString() + UUID.randomUUID();
        InviteToken invite = new InviteToken();
        invite.setEmail(email.trim().toLowerCase());
        invite.setOrganizationId(organizationId);
        invite.setRole(role);
        invite.setTokenHash(tokenHashingService.sha256(rawToken));
        invite.setExpiresAt(Instant.now().plusSeconds(securityProperties.jwt().inviteTokenSeconds()));
        invite.setCreatedBy(createdBy);

        inviteTokenRepository.save(invite);
        return rawToken;
    }

    @Transactional
    public InviteToken consumeInvite(String rawToken) {
        String hash = tokenHashingService.sha256(rawToken);

        InviteToken invite = inviteTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid invite token"));

        if (invite.getUsedAt() != null) {
            throw new IllegalArgumentException("Invite token has already been used");
        }

        if (invite.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Invite token has expired");
        }

        invite.setUsedAt(Instant.now());
        return inviteTokenRepository.save(invite);
    }
}

