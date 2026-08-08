package com.bruce.docai.service;

import com.bruce.docai.model.Organization;
import com.bruce.docai.repository.RagDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeServiceTest {

    private RagDocumentRepository documentRepository;
    private VectorStore vectorStore;
    private DocumentService documentService;
    private TenantService tenantService;
    private AuditService auditService;
    private KnowledgeService service;

    @BeforeEach
    void setUp() {
        documentRepository = mock(RagDocumentRepository.class);
        JdbcClient jdbcClient = mock(JdbcClient.class);
        vectorStore = mock(VectorStore.class);
        documentService = mock(DocumentService.class);
        tenantService = mock(TenantService.class);
        auditService = mock(AuditService.class);
        service = new KnowledgeService(documentRepository, jdbcClient, vectorStore, documentService,
                tenantService, auditService);
    }

    @Test
    void rejectsUploadWhenOrganizationMissing() {
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.add(file, "  ", "admin@example.com"));

        assertEquals("Your account is not assigned to an organization.", ex.getMessage());
    }

    @Test
    void rejectsUploadWhenDocumentQuotaReached() throws IOException {
        Organization org = new Organization();
        org.setId("org-1");
        org.setName("Org One");
        org.setSlug("org-1");
        org.setMaxDocuments(2);
        when(tenantService.requireActive("org-1")).thenReturn(org);
        when(documentRepository.countByOrganizationId("org-1")).thenReturn(2);

        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.add(file, "org-1", "admin@example.com"));

        assertEquals("Document limit reached for this organization (2). "
                + "Delete a document before uploading a new one.", ex.getMessage());
        verify(documentRepository, never()).insert(any(), anyString());
    }

    @Test
    void usesConfiguredDefaultQuotaWhenOrganizationHasNoOverride() throws IOException {
        Organization org = new Organization();
        org.setId("org-1");
        org.setName("Org One");
        org.setSlug("org-1");
        org.setMaxDocuments(null);
        ReflectionTestUtils.setField(service, "defaultMaxDocuments", 1);
        when(tenantService.requireActive("org-1")).thenReturn(org);
        when(documentRepository.countByOrganizationId("org-1")).thenReturn(1);

        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes());

        assertThrows(IllegalArgumentException.class,
                () -> service.add(file, "org-1", "admin@example.com"));
        verify(documentService, never()).processFile(file, "org-1", null);
    }
}
