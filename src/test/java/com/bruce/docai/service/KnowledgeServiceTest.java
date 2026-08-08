package com.bruce.docai.service;

import com.bruce.docai.metrics.DocumentMetrics;
import com.bruce.docai.model.DocumentStatus;
import com.bruce.docai.model.Organization;
import com.bruce.docai.model.RagDocument;
import com.bruce.docai.repository.RagDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeServiceTest {

    private RagDocumentRepository documentRepository;
    private VectorStore vectorStore;
    private DocumentIngestionService ingestionService;
    private TenantService tenantService;
    private AuditService auditService;
    private DocumentMetrics documentMetrics;
    private KnowledgeService service;

    @BeforeEach
    void setUp() {
        documentRepository = mock(RagDocumentRepository.class);
        JdbcClient jdbcClient = mock(JdbcClient.class);
        vectorStore = mock(VectorStore.class);
        ingestionService = mock(DocumentIngestionService.class);
        tenantService = mock(TenantService.class);
        auditService = mock(AuditService.class);
        documentMetrics = mock(DocumentMetrics.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new KnowledgeService(documentRepository, jdbcClient, vectorStore, ingestionService,
                tenantService, auditService, documentMetrics, transactionManager);
    }

    @Test
    void rejectsUploadWhenOrganizationMissing() {
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.add(file, "  ", "admin@example.com"));

        assertEquals("Your account is not assigned to an organization.", ex.getMessage());
    }

    @Test
    void rejectsUploadWhenDocumentQuotaReached() {
        Organization org = organization(2);
        when(tenantService.requireActive("org-1")).thenReturn(org);
        when(documentRepository.countByOrganizationId("org-1")).thenReturn(2);

        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.add(file, "org-1", "admin@example.com"));

        assertEquals("Document limit reached for this organization (2). "
                + "Delete a document before uploading a new one.", ex.getMessage());
        verify(documentRepository, never()).insert(any(), anyString());
        verify(ingestionService, never()).ingestAsync(any(), anyString(), any(), anyLong(), anyString(), any());
    }

    @Test
    void usesConfiguredDefaultQuotaWhenOrganizationHasNoOverride() {
        Organization org = organization(null);
        ReflectionTestUtils.setField(service, "defaultMaxDocuments", 1);
        when(tenantService.requireActive("org-1")).thenReturn(org);
        when(documentRepository.countByOrganizationId("org-1")).thenReturn(1);

        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes());

        assertThrows(IllegalArgumentException.class,
                () -> service.add(file, "org-1", "admin@example.com"));
        verify(ingestionService, never()).ingestAsync(any(), anyString(), any(), anyLong(), anyString(), any());
    }

    @Test
    void rejectsDuplicateChecksumBeforeQueueing() {
        when(tenantService.requireActive("org-1")).thenReturn(organization(null));
        when(documentRepository.existsByChecksum(eq("org-1"), anyString())).thenReturn(true);

        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.add(file, "org-1", "admin@example.com"));

        assertEquals("This document has already been loaded.", ex.getMessage());
        verify(documentRepository, never()).insert(any(), anyString());
        verify(ingestionService, never()).ingestAsync(any(), anyString(), any(), anyLong(), anyString(), any());
    }

    @Test
    void queuesPendingDocumentForAsyncIngestion() throws IOException {
        when(tenantService.requireActive("org-1")).thenReturn(organization(null));
        when(documentRepository.existsByChecksum(eq("org-1"), anyString())).thenReturn(false);

        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "hello world".getBytes());

        RagDocument result = service.add(file, "org-1", "admin@example.com");

        assertEquals(DocumentStatus.PENDING, result.status());
        assertEquals("notes.txt", result.filename());
        verify(documentRepository).insert(any(RagDocument.class), anyString());
        verify(documentMetrics).recordUploadAccepted();

        ArgumentCaptor<Path> tempCaptor = ArgumentCaptor.forClass(Path.class);
        ArgumentCaptor<UUID> idCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(ingestionService).ingestAsync(tempCaptor.capture(), eq("notes.txt"), eq("text/plain"),
                anyLong(), eq("org-1"), idCaptor.capture());
        assertEquals(result.id(), idCaptor.getValue());
    }

    @Test
    void rejectsUnsupportedExtensionSynchronously() {
        when(tenantService.requireActive("org-1")).thenReturn(organization(null));

        MockMultipartFile file = new MockMultipartFile("file", "bad.exe", "application/octet-stream", "nope".getBytes());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.add(file, "org-1", "admin@example.com"));

        assertEquals("Unsupported file 'bad.exe'. Allowed types: pdf, doc, docx, txt.", ex.getMessage());
        verify(documentRepository, never()).insert(any(), anyString());
        verify(ingestionService, never()).ingestAsync(any(), anyString(), any(), anyLong(), anyString(), any());
    }

    private Organization organization(Integer maxDocuments) {
        Organization org = new Organization();
        org.setId("org-1");
        org.setName("Org One");
        org.setSlug("org-1");
        org.setMaxDocuments(maxDocuments);
        return org;
    }
}
