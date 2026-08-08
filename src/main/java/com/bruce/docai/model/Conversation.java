package com.bruce.docai.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A multi-turn conversation between an end user and an AGENTIC agent, keyed by
 * {@code (organizationId, channel, userRef)}.
 */
public record Conversation(
        UUID id,
        String organizationId,
        Channel channel,
        String userRef,
        Instant createdAt,
        Instant lastActivityAt
) {
}
