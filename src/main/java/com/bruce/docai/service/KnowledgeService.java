package com.bruce.docai.service;

import com.bruce.docai.model.Organization;
import com.bruce.docai.model.RagDocument;
import com.bruce.docai.repository.RagDocumentRepository;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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
    private final TenantService tenantService;
    private final AuditService auditService;

    @Value("${app.tenant.default-max-documents:0}")
    private int defaultMaxDocuments;

    @Value("${app.tenant.rls-enabled:false}")
    private boolean rlsEnabled;

    public KnowledgeService(RagDocumentRepository documentRepository,
                             JdbcClient jdbcClient,
                             VectorStore vectorStore,
                             @Qualifier("tika-parser") DocumentService documentService,
                             TenantService tenantService,
                             AuditService auditService) {
        this.documentRepository = documentRepository;
        this.jdbcClient = jdbcClient;
        this.vectorStore = vectorStore;
        this.documentService = documentService;
        this.tenantService = tenantService;
        this.auditService = auditService;
    }

    public List<RagDocument> list(String organizationId) {
        return documentRepository.findAllByOrganizationId(requireOrganization(organizationId));
    }

    @Transactional
    public RagDocument add(MultipartFile file, String organizationId, String actor) throws IOException {
        String resolvedOrganization = requireOrganization(organizationId);
        Organization organization = tenantService.requireActive(resolvedOrganization);
        bindTenantForRls(resolvedOrganization);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty. Allowed types: pdf, doc, docx, txt.");
        }

        enforceDocumentQuota(organization, resolvedOrganization);

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
            RagDocument saved = documentRepository.findByIdAndOrganizationId(id, resolvedOrganization).orElseThrow();
            auditService.record(resolvedOrganization, actor, "DOCUMENT_UPLOAD", saved.filename(),
                    "id=" + id + ", chunks=" + chunkCount);
            return saved;
        } catch (RuntimeException | IOException ex) {
            deleteVectors(id, resolvedOrganization);
            documentRepository.delete(id, resolvedOrganization);
            throw ex;
        }
    }

    @Transactional
    public void remove(UUID id, String organizationId, String actor) {
        String resolvedOrganization = requireOrganization(organizationId);
        bindTenantForRls(resolvedOrganization);
        RagDocument document = documentRepository.findByIdAndOrganizationId(id, resolvedOrganization)
                .orElseThrow(() -> new IllegalArgumentException("Document not found."));
        deleteVectors(id, resolvedOrganization);
        documentRepository.delete(id, resolvedOrganization);
        auditService.record(resolvedOrganization, actor, "DOCUMENT_DELETE", document.filename(), "id=" + id);
    }

    private void enforceDocumentQuota(Organization organization, String organizationId) {        Integer limit = organization.getMaxDocuments() != null
                ? organization.getMaxDocuments()
                : (defaultMaxDocuments > 0 ? defaultMaxDocuments : null);
        if (limit == null) {
            return;
        }
        int current = documentRepository.countByOrganizationId(organizationId);
        if (current >= limit) {
            throw new IllegalArgumentException(
                    "Document limit reached for this organization (" + limit + "). Delete a document before uploading a new one.");
        }
    }

    private void deleteVectors(UUID id, String organizationId) {
        FilterExpressionBuilder filter = new FilterExpressionBuilder();
        vectorStore.delete(filter.and(
                filter.eq("documentId", id.toString()),
                filter.eq("organizationId", organizationId)
        ).build());
    }

    /**
     * Binds the current tenant to the active DB transaction so Postgres Row-Level
     * Security policies (see the V2 migration) enforce isolation as a backstop.
     * No-op unless {@code app.tenant.rls-enabled=true}; must run inside a
     * transaction so {@code set_config(..., true)} is scoped to it.
     */
    private void bindTenantForRls(String organizationId) {
        if (!rlsEnabled) {
            return;
        }
        jdbcClient.sql("SELECT set_config('app.current_org', :org, true)")
                .param("org", organizationId)
                .query(String.class)
                .optional();
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
}



