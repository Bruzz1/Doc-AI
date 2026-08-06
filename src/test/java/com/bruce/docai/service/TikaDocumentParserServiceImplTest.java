package com.bruce.docai.service;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TikaDocumentParserServiceImplTest {

    private VectorStore vectorStore;
    private TikaDocumentParserServiceImpl service;

    @BeforeEach
    void setUp() {
        vectorStore = mock(VectorStore.class);
        service = new TikaDocumentParserServiceImpl(vectorStore);
    }

    @Test
    void shouldIngestTxtAndNormalizeWhitespace() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notes.txt",
                "text/plain",
                "Hello\tworld\r\n\r\nLine   two\r\nLine\tthree".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        service.processFile(file);

        List<Document> documents = captureAcceptedDocuments();
        assertFalse(documents.isEmpty());
        String text = documents.stream()
                .map(Document::getText)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.joining("\n"));
        assertTrue(text.contains("Hello world"));
        assertTrue(text.contains("Line two\nLine three") || text.contains("Line two\n\nLine three"));
        assertEquals("txt", documents.get(0).getMetadata().get("extension"));
    }

    @Test
    void shouldIngestPdfUpload() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.pdf",
                "application/pdf",
                readClasspathPdf()
        );

        service.processFile(file);

        List<Document> documents = captureAcceptedDocuments();
        assertFalse(documents.isEmpty());
        assertTrue(documents.stream().map(Document::getText).filter(Objects::nonNull).anyMatch(text -> !text.isBlank()));
        assertEquals("pdf", documents.get(0).getMetadata().get("extension"));
    }

    @Test
    void shouldIngestDocxUpload() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "proposal.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                createDocx()
        );

        service.processFile(file);

        List<Document> documents = captureAcceptedDocuments();
        assertFalse(documents.isEmpty());
        assertTrue(documents.stream()
                .map(Document::getText)
                .filter(Objects::nonNull)
                .anyMatch(text -> text.contains("Word document support is active")));
    }

    @Test
    void shouldRejectUnsupportedFileType() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "script.exe",
                "application/octet-stream",
                "not allowed".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        UnsupportedDocumentTypeException exception = assertThrows(UnsupportedDocumentTypeException.class, () -> service.processFile(file));
        assertTrue(exception.getMessage().contains("Allowed types: pdf, doc, docx, txt."));
    }

    @Test
    void shouldIngestClasspathStyleResource() throws IOException {
        ByteArrayResource resource = new ByteArrayResource("seed\n\ntext".getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "seed.txt";
            }
        };

        service.processResource(resource);

        List<Document> documents = captureAcceptedDocuments();
        assertFalse(documents.isEmpty());
        assertEquals("seed.txt", documents.get(0).getMetadata().get("filename"));
    }

    @SuppressWarnings("unchecked")
    private List<Document> captureAcceptedDocuments() {
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).accept(captor.capture());
        return captor.getValue();
    }

    private byte[] readClasspathPdf() throws IOException {
        ClassPathResource resource = new ClassPathResource("RJBRUCE_CV.pdf");
        try (InputStream inputStream = resource.getInputStream()) {
            return inputStream.readAllBytes();
        }
    }

    private byte[] createDocx() throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("Word document support is active");
            document.write(outputStream);
            return outputStream.toByteArray();
        }
    }
}





