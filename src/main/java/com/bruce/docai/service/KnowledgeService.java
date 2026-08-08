package com.bruce.docai.service;

import com.bruce.docai.metrics.DocumentMetrics;
import com.bruce.docai.model.DocumentStatus;
import com.bruce.docai.model.Organization;
import com.bruce.docai.model.RagDocument;
import com.bruce.docai.repository.RagDocumentRepository;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
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

    private final DocumentIngestionService ingestionService;
    private final TenantService tenantService;
    private final AuditService auditService;
    private final DocumentMetrics documentMetrics;
    private final TransactionTemplate transactionTemplate;

    @Value("${app.tenant.default-max-documents:0}")
    private int defaultMaxDocuments;

    @Value("${app.tenant.rls-enabled:false}")
    private boolean rlsEnabled;

    public KnowledgeService(RagDocumentRepository documentRepository,
                            JdbcClient jdbcClient,
                            VectorStore vectorStore,
                            DocumentIngestionService ingestionService,
                            TenantService tenantService,
                            AuditService auditService,
                            DocumentMetrics documentMetrics,
                            PlatformTransactionManager transactionManager) {
        this.documentRepository = documentRepository;
        this.jdbcClient = jdbcClient;
        this.vectorStore = vectorStore;
        this.ingestionService = ingestionService;
        this.tenantService = tenantService;
        this.auditService = auditService;
        this.documentMetrics = documentMetrics;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public List<RagDocument> list(String organizationId) {
        return documentRepository.findAllByOrganizationId(requireOrganization(organizationId));
    }

    /**
     * Accepts an upload, persists it to a temp file (computing its checksum in the
     * same pass), records a PENDING document row, and hands parsing/embedding to
     * the asynchronous ingestion worker. Returns immediately; callers should poll
     * the document status for completion.
     */
    public RagDocument add(MultipartFile file, String organizationId, String actor) throws IOException {
        String resolvedOrganization = requireOrganization(organizationId);
        Organization organization = tenantService.requireActive(resolvedOrganization);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty. Allowed types: pdf, doc, docx, txt.");
        }
        String extension = extension(file.getOriginalFilename());
        if (!DocumentService.ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Unsupported file '" + safeFilename(file.getOriginalFilename())
                    + "'. Allowed types: pdf, doc, docx, txt.");
        }
        enforceDocumentQuota(organization, resolvedOrganization);

        Path temp = Files.createTempFile("docai-upload-", ".bin");
        boolean handedOff = false;
        try {
            String checksum = streamToTempFile(file, temp);
            if (documentRepository.existsByChecksum(resolvedOrganization, checksum)) {
                throw new IllegalArgumentException("This document has already been loaded.");
            }

            UUID id = UUID.randomUUID();
            String filename = safeFilename(file.getOriginalFilename());
            RagDocument document = new RagDocument(id, resolvedOrganization, filename,
                    extension, file.getContentType(), file.getSize(), 0,
                    DocumentStatus.PENDING, null, null, null);

            registerPending(resolvedOrganization, document, checksum);
            auditService.record(resolvedOrganization, actor, "DOCUMENT_UPLOAD", filename, "id=" + id + ", status=PENDING");
            documentMetrics.recordUploadAccepted();

            ingestionService.ingestAsync(temp, filename, file.getContentType(), file.getSize(), resolvedOrganization, id);
            handedOff = true;
            return document;
        } finally {
            if (!handedOff) {
                deleteQuietly(temp);
            }
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

    /**
     * Inserts the PENDING metadata row in its own short transaction so the RLS
     * tenant binding (when enabled) is scoped to the same connection as the write.
     */
    private void registerPending(String organizationId, RagDocument document, String checksum) {
        transactionTemplate.executeWithoutResult(status -> {
            bindTenantForRls(organizationId);
            documentRepository.insert(document, checksum);
        });
    }

    private String streamToTempFile(MultipartFile file, Path target) throws IOException {
        MessageDigest digest = newSha256();
        try (InputStream in = file.getInputStream();
             DigestInputStream digestStream = new DigestInputStream(in, digest);
             OutputStream out = Files.newOutputStream(target)) {
            digestStream.transferTo(out);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private void enforceDocumentQuota(Organization organization, String organizationId) {
        Integer limit = organization.getMaxDocuments() != null
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

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Temp file cleanup is best effort.
        }
    }

    private MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }

    private String requireOrganization(String organizationId) {
        if (organizationId == null || organizationId.isBlank()) {
            throw new IllegalArgumentException("Your account is not assigned to an organization.");
        }
        return organizationId;
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
