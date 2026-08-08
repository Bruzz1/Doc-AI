package com.bruce.docai.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

public interface DocumentService {

    /** File extensions accepted by the ingestion pipeline. */
    Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx", "txt");

    void processFile(MultipartFile file, String organizationId, UUID documentId) throws IOException;

    /**
     * Ingest an already-persisted file from disk, preserving the caller-supplied
     * original filename and content type. Used by the asynchronous ingestion
     * worker so the upload can be streamed to a temp file on the request thread.
     */
    default void processFile(Path source, String filename, String contentType, long size,
                             String organizationId, UUID documentId) throws IOException {
        throw new UnsupportedOperationException("Path-based ingestion is not supported by this implementation.");
    }

    void processResource(Resource resource) throws IOException;

    default void processResource(Resource resource, String organizationId, UUID documentId) throws IOException {
        processResource(resource);
    }
}
