package com.bruce.docai.repository;

import com.bruce.docai.model.DocumentStatus;
import com.bruce.docai.model.RagDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RagDocumentRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, organization_id, filename, extension, content_type, size,
                   chunk_count, status, error_message, created_at, updated_at
            """;

    private final JdbcClient jdbcClient;

    public List<RagDocument> findAllByOrganizationId(String organizationId) {
        return jdbcClient.sql(SELECT_COLUMNS + """
                FROM rag_documents
                WHERE organization_id = :organizationId
                ORDER BY created_at DESC, filename
                """)
                .param("organizationId", organizationId)
                .query(this::map)
                .list();
    }

    public Optional<RagDocument> findByIdAndOrganizationId(UUID id, String organizationId) {
        return jdbcClient.sql(SELECT_COLUMNS + """
                FROM rag_documents
                WHERE id = :id AND organization_id = :organizationId
                """)
                .param("id", id)
                .param("organizationId", organizationId)
                .query(this::map)
                .optional();
    }

    public boolean existsByChecksum(String organizationId, String checksum) {
        return jdbcClient.sql("""
                SELECT COUNT(*) FROM rag_documents
                WHERE organization_id = :organizationId AND checksum = :checksum
                """)
                .param("organizationId", organizationId)
                .param("checksum", checksum)
                .query(Integer.class)
                .single() > 0;
    }

    public int countByOrganizationId(String organizationId) {
        return jdbcClient.sql("SELECT COUNT(*) FROM rag_documents WHERE organization_id = :organizationId")
                .param("organizationId", organizationId)
                .query(Integer.class)
                .single();
    }

    public void insert(RagDocument document, String checksum) {
        try {
            jdbcClient.sql("""
                    INSERT INTO rag_documents
                        (id, organization_id, filename, extension, content_type, size, checksum, chunk_count, status)
                    VALUES (:id, :organizationId, :filename, :extension, :contentType, :size, :checksum, :chunkCount, :status)
                    """)
                    .param("id", document.id())
                    .param("organizationId", document.organizationId())
                    .param("filename", document.filename())
                    .param("extension", document.extension())
                    .param("contentType", document.contentType())
                    .param("size", document.size())
                    .param("checksum", checksum)
                    .param("chunkCount", document.chunkCount())
                    .param("status", document.status().name())
                    .update();
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("This document has already been loaded.", ex);
        }
    }

    public void updateStatus(UUID id, DocumentStatus status, String errorMessage) {
        jdbcClient.sql("""
                UPDATE rag_documents
                SET status = :status, error_message = :errorMessage, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """)
                .param("id", id)
                .param("status", status.name())
                .param("errorMessage", errorMessage)
                .update();
    }

    public void markIndexed(UUID id, int chunkCount) {
        jdbcClient.sql("""
                UPDATE rag_documents
                SET chunk_count = :chunkCount, status = :status, error_message = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """)
                .param("id", id)
                .param("chunkCount", chunkCount)
                .param("status", DocumentStatus.INDEXED.name())
                .update();
    }

    public void delete(UUID id, String organizationId) {
        jdbcClient.sql("DELETE FROM rag_documents WHERE id = :id AND organization_id = :organizationId")
                .param("id", id)
                .param("organizationId", organizationId)
                .update();
    }

    private RagDocument map(ResultSet rs, int rowNum) throws SQLException {
        return new RagDocument(
                rs.getObject("id", UUID.class),
                rs.getString("organization_id"),
                rs.getString("filename"),
                rs.getString("extension"),
                rs.getString("content_type"),
                rs.getLong("size"),
                rs.getInt("chunk_count"),
                DocumentStatus.valueOf(rs.getString("status")),
                rs.getString("error_message"),
                toInstant(rs, "created_at"),
                toInstant(rs, "updated_at"));
    }

    private Instant toInstant(ResultSet rs, String column) throws SQLException {
        var timestamp = rs.getTimestamp(column);
        return timestamp != null ? timestamp.toInstant() : Instant.EPOCH;
    }
}
