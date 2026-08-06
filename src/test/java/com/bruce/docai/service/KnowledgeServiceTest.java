package com.bruce.docai.service;

import com.bruce.docai.model.KnowledgeEntry;
import com.bruce.docai.model.KnowledgeUploadPayload;
import com.bruce.docai.model.User;
import com.bruce.docai.repository.KnowledgeEntryRepository;
import com.bruce.docai.repository.KnowledgeUploadPayloadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeServiceTest {

    private DocumentService documentService;
    private KnowledgeEntryRepository knowledgeEntryRepository;
    private KnowledgeUploadPayloadRepository knowledgeUploadPayloadRepository;
    private JdbcTemplate jdbcTemplate;
    private KnowledgeService knowledgeService;

    @BeforeEach
    void setUp() {
        documentService = mock(DocumentService.class);
        knowledgeEntryRepository = mock(KnowledgeEntryRepository.class);
        knowledgeUploadPayloadRepository = mock(KnowledgeUploadPayloadRepository.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        knowledgeService = new KnowledgeService(documentService, knowledgeEntryRepository, knowledgeUploadPayloadRepository, jdbcTemplate);
    }

    @Test
    void addKnowledgeShouldIngestAndPersistMetadata() throws IOException {
        User user = buildUser();
        MockMultipartFile file = new MockMultipartFile("file", "policy.txt", "text/plain", "content".getBytes());

        when(knowledgeEntryRepository.save(any(KnowledgeEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KnowledgeEntry result = knowledgeService.addKnowledge(file, user);

        assertEquals("policy.txt", result.getFilename());
        assertEquals("org-1", result.getOrganizationId());
        assertEquals("admin@example.com", result.getUploadedBy());
        assertTrue(result.getKnowledgeId() != null && !result.getKnowledgeId().isBlank());

        ArgumentCaptor<IngestionMetadataContext> contextCaptor = ArgumentCaptor.forClass(IngestionMetadataContext.class);
        verify(documentService).processFile(eq(file), contextCaptor.capture());
        assertEquals(result.getKnowledgeId(), contextCaptor.getValue().knowledgeId());
        assertEquals("org-1", contextCaptor.getValue().organizationId());
        assertEquals("admin@example.com", contextCaptor.getValue().uploadedBy());

        verify(knowledgeEntryRepository).save(any(KnowledgeEntry.class));
        verify(knowledgeUploadPayloadRepository).save(any(KnowledgeUploadPayload.class));
    }

    @Test
    void addKnowledgeShouldRejectEmptyFile() {
        User user = buildUser();
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> knowledgeService.addKnowledge(emptyFile, user));

        assertEquals("Please select a document to upload.", ex.getMessage());
    }

    @Test
    void removeKnowledgeShouldDeleteVectorRowsAndMetadata() {
        User user = buildUser();

        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setKnowledgeId("k-123");
        entry.setOrganizationId("org-1");
        entry.setFilename("policy.txt");
        entry.setUploadedBy("admin@example.com");
        entry.setCreatedAt(Instant.now());

        when(knowledgeEntryRepository.findByKnowledgeIdAndOrganizationId("k-123", "org-1"))
                .thenReturn(Optional.of(entry));

        knowledgeService.removeKnowledge("k-123", user);

        verify(jdbcTemplate).update("DELETE FROM vector_store WHERE metadata->>'knowledge-id' = ?", "k-123");
        verify(knowledgeUploadPayloadRepository).deleteById("k-123");
        verify(knowledgeEntryRepository).delete(entry);
    }

    @Test
    void listKnowledgeShouldFilterByOrganization() {
        User user = buildUser();
        when(knowledgeEntryRepository.findByOrganizationIdOrderByCreatedAtDesc("org-1"))
                .thenReturn(List.of());

        knowledgeService.listKnowledge(user);

        verify(knowledgeEntryRepository).findByOrganizationIdOrderByCreatedAtDesc("org-1");
    }

    private User buildUser() {
        User user = new User();
        user.setEmail("admin@example.com");
        user.setOrganizationId("org-1");
        user.setRole("ADMIN");
        return user;
    }
}

