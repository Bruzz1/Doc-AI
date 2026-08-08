package com.bruce.docai.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A single stored turn within a {@link Conversation}.
 */
public record ConversationMessage(
        UUID id,
        UUID conversationId,
        String organizationId,
        MessageRole role,
        String content,
        String toolName,
        Instant createdAt
) {
}
