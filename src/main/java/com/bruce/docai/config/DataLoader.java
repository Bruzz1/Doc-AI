package com.bruce.docai.config;

import com.bruce.docai.service.DocumentService;
import com.bruce.docai.model.RagDocument;
import com.bruce.docai.repository.RagDocumentRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataLoader {

    private static final Pattern VECTOR_DIMENSION_PATTERN = Pattern.compile("vector\\((\\d+)\\)");

    private final JdbcClient jdbcClient;
    private final DocumentService documentService;
    private final RagDocumentRepository ragDocumentRepository;

    @Value("classpath:/RJBRUCE_CV.pdf")
    private Resource pdfResource;

    @Value("${spring.ai.vectorstore.pgvector.dimensions:768}")
    private int expectedVectorDimension;

    @Value("${app.rag.reset-on-dimension-drift:true}")
    private boolean resetOnDimensionDrift;

    @PostConstruct
    public void init() {
        Integer currentDimension = getCurrentVectorDimension();
        if (currentDimension != null && currentDimension != expectedVectorDimension) {
            if (!resetOnDimensionDrift) {
                throw new IllegalStateException("Vector dimension mismatch: table uses " + currentDimension
                        + " but app expects " + expectedVectorDimension
                        + ". Enable app.rag.reset-on-dimension-drift to auto-recreate vector_store in dev.");
            }

            log.warn("Dimension drift detected. Recreating vector_store from {} to {}.",
                    currentDimension, expectedVectorDimension);
            recreateVectorStoreTable();
        } else if (currentDimension == null) {
            log.warn("vector_store.embedding metadata not found. Recreating vector_store with dimension {}.",
                    expectedVectorDimension);
            recreateVectorStoreTable();
        }

        Integer count = jdbcClient.sql("select COUNT(*) from vector_store")
                .query(Integer.class)
                .single();

        log.info("No of Records in the PG Vector Store: {}", count);
        if (count == 0) {
            log.info("Loading personal resume into vector_store");
            try {
                UUID documentId = UUID.randomUUID();
                byte[] bytes = pdfResource.getInputStream().readAllBytes();
                String filename = pdfResource.getFilename() != null ? pdfResource.getFilename() : "seed-document";
                ragDocumentRepository.insert(new RagDocument(documentId, "default-org", filename,
                        extension(filename), "application/pdf", bytes.length, 0, null), checksum(bytes));
                documentService.processResource(pdfResource, "default-org", documentId);
                int chunkCount = jdbcClient.sql("SELECT COUNT(*) FROM vector_store WHERE metadata ->> 'documentId' = :documentId")
                        .param("documentId", documentId.toString()).query(Integer.class).single();
                ragDocumentRepository.updateChunkCount(documentId, chunkCount);
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to preload seed document into vector_store.", ex);
            }

            log.info("Application is ready to serve the request");
        } else {
            log.info("Skipping seed load because vector_store already contains data");
        }
    }

    private Integer getCurrentVectorDimension() {
        String embeddingType = jdbcClient.sql("""
                        SELECT format_type(a.atttypid, a.atttypmod)
                        FROM pg_attribute a
                        JOIN pg_class c ON a.attrelid = c.oid
                        JOIN pg_namespace n ON c.relnamespace = n.oid
                        WHERE c.relname = 'vector_store'
                          AND a.attname = 'embedding'
                          AND a.attnum > 0
                          AND NOT a.attisdropped
                        LIMIT 1
                        """)
                .query(String.class)
                .optional()
                .orElse(null);

        if (embeddingType == null) {
            return null;
        }

        Matcher matcher = VECTOR_DIMENSION_PATTERN.matcher(embeddingType);
        if (!matcher.matches()) {
            return null;
        }
        return Integer.parseInt(matcher.group(1));
    }

    private void recreateVectorStoreTable() {
        jdbcClient.sql("DROP TABLE IF EXISTS vector_store").update();
        jdbcClient.sql("""
                CREATE TABLE vector_store (
                    id uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
                    content text,
                    metadata json,
                    embedding vector(%d)
                )
                """.formatted(expectedVectorDimension)).update();
        jdbcClient.sql("CREATE INDEX ON vector_store USING HNSW (embedding vector_cosine_ops)").update();
    }

    private String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase();
    }

    private String checksum(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }
}
