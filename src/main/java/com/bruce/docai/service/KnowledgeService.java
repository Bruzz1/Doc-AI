package com.bruce.docai.service;

import com.bruce.docai.model.RagDocument;
import com.bruce.docai.repository.RagDocumentRepository;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class KnowledgeService {

    private final RagDocumentRepository documentRepository;
    private final JdbcClient jdbcClient;
    private final VectorStore vectorStore;

    private final DocumentService documentService;

    public KnowledgeService(RagDocumentRepository documentRepository,
                             JdbcClient jdbcClient,
                             VectorStore vectorStore,
                             @Qualifier("tika-parser") DocumentService documentService) {
        this.documentRepository = documentRepository;
        this.jdbcClient = jdbcClient;
        this.vectorStore = vectorStore;
        this.documentService = documentService;
    }

    public List<RagDocument> list(String organizationId) {
        return documentRepository.findAllByOrganizationId(requireOrganization(organizationId));
    }

    @Transactional
    public RagDocument add(MultipartFile file, String organizationId) throws IOException {
        String resolvedOrganization = requireOrganization(organizationId);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty. Allowed types: pdf, doc, docx, txt.");
        }

        String checksum = checksum(file.getBytes());
        if (documentRepository.existsByChecksum(resolvedOrganization, checksum)) {
            throw new IllegalArgumentException("This document has already been loaded.");
        }

        UUID id = UUID.randomUUID();
        RagDocument document = new RagDocument(id, resolvedOrganization, safeFilename(file.getOriginalFilename()),
                extension(file.getOriginalFilename()), file.getContentType(), file.getSize(), 0, null);
        documentRepository.insert(document, checksum);

        try {
            documentService.processFile(file, resolvedOrganization, id);

            int chunkCount = jdbcClient.sql("""
                    SELECT COUNT(*) FROM vector_store
                    WHERE metadata ->> 'documentId' = :documentId
                      AND metadata ->> 'organizationId' = :organizationId
                    """)
                    .param("documentId", id.toString())
                    .param("organizationId", resolvedOrganization)
                    .query(Integer.class)
                    .single();
            documentRepository.updateChunkCount(id, chunkCount);
            return documentRepository.findByIdAndOrganizationId(id, resolvedOrganization).orElseThrow();
        } catch (RuntimeException | IOException ex) {
            deleteVectors(id, resolvedOrganization);
            documentRepository.delete(id, resolvedOrganization);
            throw ex;
        }
    }

    @Transactional
    public void remove(UUID id, String organizationId) {
        String resolvedOrganization = requireOrganization(organizationId);
        documentRepository.findByIdAndOrganizationId(id, resolvedOrganization)
                .orElseThrow(() -> new IllegalArgumentException("Document not found."));
        deleteVectors(id, resolvedOrganization);
        documentRepository.delete(id, resolvedOrganization);
    }

    private void deleteVectors(UUID id, String organizationId) {
        vectorStore.delete("documentId == '" + escapeFilterValue(id.toString())
                + "' && organizationId == '" + escapeFilterValue(organizationId) + "'");
    }

    private String requireOrganization(String organizationId) {
        if (organizationId == null || organizationId.isBlank()) {
            throw new IllegalArgumentException("Your account is not assigned to an organization.");
        }
        return organizationId;
    }

    private String checksum(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }

    private String safeFilename(String filename) {
        return filename == null || filename.isBlank() ? "unnamed-document" : filename;
    }

    private String extension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(java.util.Locale.ROOT);
    }

    private String escapeFilterValue(String value) {
        return value.replace("'", "\\'");
    }
}



