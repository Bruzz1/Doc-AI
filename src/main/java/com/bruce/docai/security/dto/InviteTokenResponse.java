package com.bruce.docai.security.dto;

import java.time.Instant;

public record InviteTokenResponse(
    String id,
    String email,
    String role,
    String organizationId,
    Instant expiresAt,
    Instant usedAt,
    Instant createdAt,
    String createdBy
) {
    public static InviteTokenResponse fromModel(com.bruce.docai.model.InviteToken invite) {
        return new InviteTokenResponse(
            invite.getId(),
            invite.getEmail(),
            invite.getRole(),
            invite.getOrganizationId(),
            invite.getExpiresAt(),
            invite.getUsedAt(),
            invite.getCreatedAt(),
            invite.getCreatedBy()
        );
    }
}

