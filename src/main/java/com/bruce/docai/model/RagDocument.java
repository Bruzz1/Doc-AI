package com.bruce.docai.model;

import java.time.Instant;
import java.util.UUID;

public record RagDocument(
        UUID id,
        String organizationId,
        String filename,
        String extension,
        String contentType,
        long size,
        int chunkCount,
        Instant createdAt
) {
}

