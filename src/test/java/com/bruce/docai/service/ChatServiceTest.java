package com.bruce.docai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatServiceTest {

    private VectorStore vectorStore;
    private ChatClient chatClient;
    private ChatService service;

    @BeforeEach
    void setUp() {
        vectorStore = mock(VectorStore.class);
        ChatClient.Builder chatClientBuilder = mock(ChatClient.Builder.class);
        chatClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
        when(chatClientBuilder.build()).thenReturn(chatClient);

        service = new ChatService(chatClientBuilder, vectorStore);
        ReflectionTestUtils.setField(service, "topK", 2);
        ReflectionTestUtils.setField(service, "fallbackTopK", 4);
        ReflectionTestUtils.setField(service, "similarityThreshold", 0.35d);
        ReflectionTestUtils.setField(service, "minimumResultsForAnswer", 2);
        ReflectionTestUtils.setField(service, "maxContextChars", 120);
    }

    @Test
    void shouldReturnFallbackMessageWhenRetrieverFindsNothing() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of()).thenReturn(List.of());

        String result = service.getKnownInfo("unknown fact");

        assertEquals("I don't have enough information to answer that question.", result);
        verify(vectorStore, times(2)).similaritySearch(any(SearchRequest.class));
        verify(chatClient, never()).prompt(any(Prompt.class));
    }

    @Test
    void shouldRetryWithoutThresholdAndMergeUniqueDocuments() {
        Document primary = new Document("Primary match", metadata("filename", "resume.pdf", "page_number", 1));
        Document fallbackDuplicate = new Document("Primary match", metadata("filename", "resume.pdf", "page_number", 1));
        Document fallbackExtra = new Document("Additional supporting context", metadata("filename", "resume.pdf", "page_number", 2));

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(primary))
                .thenReturn(List.of(fallbackDuplicate, fallbackExtra));

        List<Document> result = service.findSimilarDocuments("experience");

        assertEquals(2, result.size());
        assertEquals("Primary match", result.get(0).getText());
        assertEquals("Additional supporting context", result.get(1).getText());
        verify(vectorStore, times(2)).similaritySearch(any(SearchRequest.class));
    }

    @Test
    void shouldFormatContextWithChunkHeadersAndTruncation() {
        ReflectionTestUtils.setField(service, "maxContextChars", 90);

        Document first = new Document(
                "This is a long first chunk with enough text to force truncation in the context builder and keep the output within a tiny budget.",
                metadata("filename", "resume.pdf", "page_number", 3)
        );
        Document second = new Document(
                "This second chunk should not fully fit in the configured context budget.",
                metadata("filename", "portfolio.pdf")
        );

        String context = service.buildContext(List.of(first, second));

        assertTrue(context.contains("[Chunk 1 | Source: resume.pdf | Page: 3]"));
        assertTrue(context.contains("...[additional context omitted]"));
    }

    @Test
    void shouldReturnTrimmedGroundedAnswerWhenContextExists() {
        Document doc = new Document("Roland Bruce is a software developer.", metadata("filename", "resume.pdf"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));
        when(chatClient.prompt(any(Prompt.class)).call().content()).thenReturn("  Roland Bruce is a software developer.  ");

        String result = service.getKnownInfo("Who is Roland Bruce?");

        assertEquals("Roland Bruce is a software developer.", result);
        verify(chatClient).prompt(any(Prompt.class));
    }

    @Test
    void appliesOrganizationFilterToSearchRequests() {
        org.mockito.ArgumentCaptor<SearchRequest> captor = org.mockito.ArgumentCaptor.forClass(SearchRequest.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        service.getKnownInfo("anything", "org-42");

        verify(vectorStore, org.mockito.Mockito.atLeastOnce()).similaritySearch(captor.capture());
        boolean allScopedToOrg = captor.getAllValues().stream()
                .allMatch(request -> request.getFilterExpression() != null
                        && request.getFilterExpression().toString().contains("org-42"));
        assertTrue(allScopedToOrg, "every vector search must be scoped to the caller's organization");
    }

    private Map<String, Object> metadata(Object... keyValues) {
        Map<String, Object> metadata = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            metadata.put(keyValues[i].toString(), keyValues[i + 1]);
        }
        return metadata;
    }
}



