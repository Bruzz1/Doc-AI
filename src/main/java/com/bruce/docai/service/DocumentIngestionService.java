package com.bruce.docai.service;

import com.bruce.docai.config.AsyncConfig;
import com.bruce.docai.metrics.DocumentMetrics;
import com.bruce.docai.model.DocumentStatus;
import com.bruce.docai.repository.RagDocumentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Runs document parsing and embedding off the HTTP request thread on the bounded
 * {@link AsyncConfig#DOCUMENT_INGEST_EXECUTOR} pool. Progress is reflected in the
 * {@code rag_documents.status} column so the UI can poll for completion.
 */
@Service
@Slf4j
public class DocumentIngestionService {

    private final RagDocumentRepository documentRepository;
    private final JdbcClient jdbcClient;
    private final VectorStore vectorStore;
    private final DocumentService documentService;
    private final DocumentMetrics documentMetrics;
    private final TransactionTemplate transactionTemplate;

    @Value("${app.tenant.rls-enabled:false}")
    private boolean rlsEnabled;

    public DocumentIngestionService(RagDocumentRepository documentRepository,
                                    JdbcClient jdbcClient,
                                    VectorStore vectorStore,
                                    @Qualifier("tika-parser") DocumentService documentService,
                                    DocumentMetrics documentMetrics,
                                    PlatformTransactionManager transactionManager) {
        this.documentRepository = documentRepository;
        this.jdbcClient = jdbcClient;
        this.vectorStore = vectorStore;
        this.documentService = documentService;
        this.documentMetrics = documentMetrics;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Async(AsyncConfig.DOCUMENT_INGEST_EXECUTOR)
    public void ingestAsync(Path source, String filename, String contentType, long size,
                            String organizationId, UUID documentId) {
        Instant started = Instant.now();
        try {
            documentRepository.updateStatus(documentId, DocumentStatus.PROCESSING, null);
            int chunkCount = runIngestion(source, filename, contentType, size, organizationId, documentId);
            documentRepository.markIndexed(documentId, chunkCount);
            documentMetrics.recordIngestSuccess(Duration.between(started, Instant.now()), chunkCount);
            log.info("Ingested document id={} org={} chunks={}", documentId, organizationId, chunkCount);
        } catch (Exception ex) {
            log.error("Ingestion failed for document id={} org={} file={}", documentId, organizationId, filename, ex);
            safeRollback(documentId, organizationId, ex);
            documentMetrics.recordIngestFailure(Duration.between(started, Instant.now()));
        } finally {
            deleteQuietly(source);
        }
    }

    private int runIngestion(Path source, String filename, String contentType, long size,
                             String organizationId, UUID documentId) {
        if (rlsEnabled) {
            // Row-Level Security needs set_config and the vector writes to share a
            // connection, so the parse/embed happens inside a single transaction.
            transactionTemplate.executeWithoutResult(status -> {
                bindTenantForRls(organizationId);
                parse(source, filename, contentType, size, organizationId, documentId);
            });
        } else {
            // No RLS: keep the Ollama embedding call outside any DB transaction so a
            // pooled connection is not held for the duration of the network round-trip.
            parse(source, filename, contentType, size, organizationId, documentId);
        }
        return countChunks(documentId, organizationId);
    }

    private void parse(Path source, String filename, String contentType, long size,
                       String organizationId, UUID documentId) {
        try {
            documentService.processFile(source, filename, contentType, size, organizationId, documentId);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private void safeRollback(UUID documentId, String organizationId, Exception cause) {
        try {
            deleteVectors(documentId, organizationId);
        } catch (RuntimeException cleanupError) {
            log.warn("Failed to clean up vectors for failed document id={} org={}", documentId, organizationId,
                    cleanupError);
        }
        String message = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
        documentRepository.updateStatus(documentId, DocumentStatus.FAILED, truncate(message));
    }

    private int countChunks(UUID documentId, String organizationId) {
        return jdbcClient.sql("""
                SELECT COUNT(*) FROM vector_store
                WHERE metadata ->> 'documentId' = :documentId
                  AND metadata ->> 'organizationId' = :organizationId
                """)
                .param("documentId", documentId.toString())
                .param("organizationId", organizationId)
                .query(Integer.class)
                .single();
    }

    private void deleteVectors(UUID id, String organizationId) {
        FilterExpressionBuilder filter = new FilterExpressionBuilder();
        vectorStore.delete(filter.and(
                filter.eq("documentId", id.toString()),
                filter.eq("organizationId", organizationId)
        ).build());
    }

    private void bindTenantForRls(String organizationId) {
        jdbcClient.sql("SELECT set_config('app.current_org', :org, true)")
                .param("org", organizationId)
                .query(String.class)
                .optional();
    }

    private void deleteQuietly(Path source) {
        if (source == null) {
            return;
        }
        try {
            Files.deleteIfExists(source);
        } catch (IOException ex) {
            log.warn("Failed to delete temp upload {}", source, ex);
        }
    }

    private String truncate(String message) {
        int limit = 1000;
        return message != null && message.length() > limit ? message.substring(0, limit) : message;
    }
}
