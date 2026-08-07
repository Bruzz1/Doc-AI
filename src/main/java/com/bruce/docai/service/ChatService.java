package com.bruce.docai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ChatService {

    private static final String NO_INFORMATION_RESPONSE = "I don't have enough information to answer that question.";
    private static final String EMPTY_QUESTION_RESPONSE = "Please enter a question.";
    private static final String CONTEXT_SEPARATOR = "\n\n---\n\n";
    private static final String TRUNCATION_MARKER = "\n...[additional context omitted]";

    private static final String PROMPT_TEMPLATE = """
            You are a helpful assistant. Answer the user's question using ONLY the information provided in the CONTEXT CHUNKS section below.
            Respond confidently and directly. Do not mention or reference the documents explicitly
            (e.g., avoid phrases like "based on the documents" or "according to the provided information").

            If the answer cannot be found in the documents, respond with:
            "I don't have enough information to answer that question."

            If multiple chunks are relevant, combine them into one concise answer.

            QUESTION:
            {input}

            CONTEXT CHUNKS:
            {documents}

            DISTINCT SOURCES:
            {sources}
            """;

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    @Value("${app.rag.top-k:6}")
    private int topK = 6;

    @Value("${app.rag.similarity-threshold:0.35}")
    private double similarityThreshold = 0.35d;

    @Value("${app.rag.fallback-top-k:8}")
    private int fallbackTopK = 8;

    @Value("${app.rag.minimum-results-for-answer:2}")
    private int minimumResultsForAnswer = 2;

    @Value("${app.rag.max-context-chars:12000}")
    private int maxContextChars = 12000;

    public ChatService(ChatClient.Builder chatClient, VectorStore vectorStore) {
        this.chatClient = chatClient.build();
        this.vectorStore = vectorStore;
    }

    public String chat(String prompt) {
        ChatResponse response = chatClient.prompt(prompt).call().chatResponse();

        if (response != null) {
            return response.getResult().getOutput().getText();
        } else {
            return "AI chat client did not respond.";
        }
    }

    public String getKnownInfo(String question) {
        return getKnownInfo(question, null);
    }

    public String getKnownInfo(String question, String organizationId) {
        String normalizedQuestion = normalizeUserInput(question);
        if (normalizedQuestion.isBlank()) {
            return EMPTY_QUESTION_RESPONSE;
        }

        long start = System.currentTimeMillis();

        List<Document> documents = findSimilarDocuments(normalizedQuestion, organizationId);
        long afterSearch = System.currentTimeMillis();

        if (documents.isEmpty()) {
            log.info("No documents retrieved for question: {}", normalizedQuestion);
            return NO_INFORMATION_RESPONSE;
        }

        String context = buildContext(documents);
        if (context.isBlank()) {
            log.info("Retrieved documents did not contain usable text for question: {}", normalizedQuestion);
            return NO_INFORMATION_RESPONSE;
        }

        String sources = buildSources(documents);

        log.info("Retrieved {} chunks | Prompt context length: {} chars | Sources: {}",
                documents.size(), context.length(), sources);

        PromptTemplate template = new PromptTemplate(PROMPT_TEMPLATE);
        Map<String, Object> promptsParam = new HashMap<>();
        promptsParam.put("input", normalizedQuestion);
        promptsParam.put("documents", context);
        promptsParam.put("sources", sources);

        String result = chatClient.prompt(template.create(promptsParam)).call().content();

        long afterLLM = System.currentTimeMillis();
        log.info("Search took: {} ms | LLM call took: {} ms", (afterSearch - start), (afterLLM - afterSearch));

        if (result == null || result.isBlank()) {
            return NO_INFORMATION_RESPONSE;
        }

        return result.strip();
    }

    List<Document> findSimilarDocuments(String question) {
        return findSimilarDocuments(question, null);
    }

    List<Document> findSimilarDocuments(String question, String organizationId) {
        List<Document> primaryMatches = deduplicateDocuments(search(question, topK, similarityThreshold, organizationId));

        if (primaryMatches.size() >= Math.max(1, minimumResultsForAnswer)) {
            return primaryMatches;
        }

        List<Document> fallbackMatches = deduplicateDocuments(search(question, Math.max(topK, fallbackTopK), null, organizationId));
        if (fallbackMatches.isEmpty()) {
            return primaryMatches;
        }

        return deduplicateDocuments(combine(primaryMatches, fallbackMatches));
    }

    String buildContext(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return "";
        }

        int contextBudget = Math.max(1, maxContextChars);
        StringBuilder context = new StringBuilder();

        for (int i = 0; i < documents.size(); i++) {
            String formattedChunk = formatDocumentChunk(i + 1, documents.get(i));
            if (formattedChunk.isBlank()) {
                continue;
            }

            String prefix = context.isEmpty() ? "" : CONTEXT_SEPARATOR;
            if (context.length() + prefix.length() + formattedChunk.length() <= contextBudget) {
                context.append(prefix).append(formattedChunk);
                continue;
            }

            int remaining = contextBudget - context.length() - prefix.length();
            if (remaining > TRUNCATION_MARKER.length()) {
                context.append(prefix)
                        .append(formattedChunk, 0, remaining - TRUNCATION_MARKER.length())
                        .append(TRUNCATION_MARKER);
            }
            break;
        }

        return context.toString().strip();
    }

    String buildSources(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return "unknown";
        }

        return documents.stream()
                .map(this::resolveSource)
                .filter(source -> !source.isBlank())
                .distinct()
                .collect(Collectors.joining(", "));
    }

    private List<Document> search(String question, int requestedTopK, Double threshold, String organizationId) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(question)
                .topK(Math.max(1, requestedTopK));

        if (threshold != null) {
            builder.similarityThreshold(threshold);
        }
        if (organizationId != null && !organizationId.isBlank()) {
            builder.filterExpression("organizationId == '" + organizationId.replace("'", "\\'") + "'");
        }

        List<Document> matches = vectorStore.similaritySearch(builder.build());
        return matches != null ? matches : List.of();
    }

    private List<Document> combine(List<Document> primaryMatches, List<Document> fallbackMatches) {
        List<Document> combined = new ArrayList<>(primaryMatches.size() + fallbackMatches.size());
        combined.addAll(primaryMatches);
        combined.addAll(fallbackMatches);
        return combined;
    }

    private List<Document> deduplicateDocuments(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }

        Map<String, Document> uniqueDocuments = new LinkedHashMap<>();
        for (Document document : documents) {
            if (document == null) {
                continue;
            }

            String text = normalizeSnippet(document.getText());
            if (text.isBlank()) {
                continue;
            }

            String key = resolveSource(document) + "|" + resolvePage(document) + "|" + text;
            uniqueDocuments.putIfAbsent(key, document);
        }

        return new ArrayList<>(uniqueDocuments.values());
    }

    private String formatDocumentChunk(int index, Document document) {
        if (document == null) {
            return "";
        }

        String text = normalizeSnippet(document.getText());
        if (text.isBlank()) {
            return "";
        }

        StringBuilder header = new StringBuilder("[Chunk ").append(index)
                .append(" | Source: ").append(resolveSource(document));

        String page = resolvePage(document);
        if (!page.isBlank()) {
            header.append(" | Page: ").append(page);
        }

        String title = resolveMetadata(document, "dc:title", "title");
        if (!title.isBlank() && !title.equalsIgnoreCase(resolveSource(document))) {
            header.append(" | Title: ").append(title);
        }

        header.append("]");
        return header + "\n" + text;
    }

    private String resolveSource(Document document) {
        String source = resolveMetadata(document, "filename", "source", "document", "resourceName");
        return source.isBlank() ? "unknown" : source;
    }

    private String resolvePage(Document document) {
        return resolveMetadata(document, "page_number", "pageNumber", "page", "page-number");
    }

    private String resolveMetadata(Document document, String... keys) {
        if (document == null || keys == null) {
            return "";
        }

        for (String key : keys) {
            Object value = document.getMetadata().get(key);
            if (value != null) {
                String resolved = value.toString().strip();
                if (!resolved.isBlank()) {
                    return resolved;
                }
            }
        }

        return "";
    }

    private String normalizeSnippet(String text) {
        if (text == null) {
            return "";
        }

        return text.lines()
                .map(String::strip)
                .filter(line -> !line.isBlank())
                .collect(Collectors.joining("\n"))
                .strip();
    }

    private String normalizeUserInput(String question) {
        return Objects.requireNonNullElse(question, "").trim();
    }

}
