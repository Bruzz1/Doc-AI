package com.bruce.docai.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Per-tenant configurable agent behavior. A single row per organization drives
 * every channel (admin chat UI, widget, WhatsApp) so configuration changes apply
 * everywhere at once.
 *
 * Nullable tuning fields ({@code model}, {@code temperature}, {@code topK},
 * {@code similarityThreshold}, {@code maxContextChars}) mean "fall back to the
 * application defaults in application.yaml"; a non-null value overrides them.
 */
public record AgentConfig(
        UUID id,
        String organizationId,
        String name,
        AgentMode mode,
        String systemPrompt,
        String model,
        Double temperature,
        String enabledTools,
        Integer topK,
        Double similarityThreshold,
        Integer maxContextChars,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
