package com.bruce.docai.controller;

import com.bruce.docai.service.DocumentService;
import com.bruce.docai.service.UnsupportedDocumentTypeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DocumentControllerTest {

    private DocumentService documentService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        documentService = mock(DocumentService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new DocumentController(documentService)).build();
    }

    @Test
    void shouldReturnUnsupportedMediaTypeForRejectedExtension() throws Exception {
        doThrow(new UnsupportedDocumentTypeException("Unsupported file 'bad.exe' with type 'application/octet-stream'. Allowed types: pdf, doc, docx, txt."))
                .when(documentService).processFile(any());

        MockMultipartFile file = new MockMultipartFile("file", "bad.exe", "application/octet-stream", "data".getBytes());

        mockMvc.perform(multipart("/upload").file(file))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().string("Unsupported file 'bad.exe' with type 'application/octet-stream'. Allowed types: pdf, doc, docx, txt."));
    }

    @Test
    void shouldReturnBadRequestForUnreadableDocument() throws Exception {
        doThrow(new IOException("boom")).when(documentService).processFile(any());

        MockMultipartFile file = new MockMultipartFile("file", "broken.pdf", "application/pdf", "bad".getBytes());

        mockMvc.perform(multipart("/upload").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Could not read the uploaded file. Make sure it is a valid pdf, doc, docx, or txt document."));
    }

    @Test
    void shouldReturnOkForSupportedDocument() throws Exception {
        doNothing().when(documentService).processFile(any());

        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());

        mockMvc.perform(multipart("/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(content().string("File processed and indexed successfully."));
    }
}

