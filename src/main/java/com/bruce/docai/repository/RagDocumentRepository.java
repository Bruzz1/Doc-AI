package com.bruce.docai.repository;

import com.bruce.docai.model.RagDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RagDocumentRepository {

    private final JdbcClient jdbcClient;

    public List<RagDocument> findAllByOrganizationId(String organizationId) {
        return jdbcClient.sql("""
                SELECT id, organization_id, filename, extension, content_type, size, chunk_count, created_at
                FROM rag_documents
                WHERE organization_id = :organizationId
                ORDER BY created_at DESC, filename
                """)
                .param("organizationId", organizationId)
                .query((rs, rowNum) -> map(rs.getObject("id", UUID.class), rs.getString("organization_id"),
                        rs.getString("filename"), rs.getString("extension"), rs.getString("content_type"),
                        rs.getLong("size"), rs.getInt("chunk_count"), rs.getTimestamp("created_at")))
                .list();
    }

    public Optional<RagDocument> findByIdAndOrganizationId(UUID id, String organizationId) {
        return jdbcClient.sql("""
                SELECT id, organization_id, filename, extension, content_type, size, chunk_count, created_at
                FROM rag_documents
                WHERE id = :id AND organization_id = :organizationId
                """)
                .param("id", id)
                .param("organizationId", organizationId)
                .query((rs, rowNum) -> map(rs.getObject("id", UUID.class), rs.getString("organization_id"),
                        rs.getString("filename"), rs.getString("extension"), rs.getString("content_type"),
                        rs.getLong("size"), rs.getInt("chunk_count"), rs.getTimestamp("created_at")))
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

    public void insert(RagDocument document, String checksum) {
        try {
            jdbcClient.sql("""
                    INSERT INTO rag_documents
                        (id, organization_id, filename, extension, content_type, size, checksum, chunk_count)
                    VALUES (:id, :organizationId, :filename, :extension, :contentType, :size, :checksum, :chunkCount)
                    """)
                    .param("id", document.id())
                    .param("organizationId", document.organizationId())
                    .param("filename", document.filename())
                    .param("extension", document.extension())
                    .param("contentType", document.contentType())
                    .param("size", document.size())
                    .param("checksum", checksum)
                    .param("chunkCount", document.chunkCount())
                    .update();
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("This document has already been loaded.", ex);
        }
    }

    public void updateChunkCount(UUID id, int chunkCount) {
        jdbcClient.sql("UPDATE rag_documents SET chunk_count = :chunkCount WHERE id = :id")
                .param("id", id)
                .param("chunkCount", chunkCount)
                .update();
    }

    public void delete(UUID id, String organizationId) {
        jdbcClient.sql("DELETE FROM rag_documents WHERE id = :id AND organization_id = :organizationId")
                .param("id", id)
                .param("organizationId", organizationId)
                .update();
    }

    private RagDocument map(UUID id, String organizationId, String filename, String extension,
                            String contentType, long size, int chunkCount, Timestamp createdAt) {
        return new RagDocument(id, organizationId, filename, extension, contentType, size, chunkCount,
                createdAt != null ? createdAt.toInstant() : Instant.EPOCH);
    }
}

