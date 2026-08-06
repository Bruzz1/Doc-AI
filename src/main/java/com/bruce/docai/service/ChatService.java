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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ChatService {

    private static final String PROMPT_TEMPLATE = """
            You are a helpful assistant. Answer the user's question using ONLY the information provided in the DOCUMENTS section below.
            Respond confidently and directly. Do not mention or reference the documents explicitly
            (e.g., avoid phrases like "based on the documents" or "according to the provided information").

            If the answer cannot be found in the documents, respond with:
            "I don't have enough information to answer that question."

            QUESTION:
            {input}

            DOCUMENTS:
            {documents}

            SOURCES:
            {sources}
            """;

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    @Value("${app.rag.top-k:4}")
    private int topK;

    @Value("${app.rag.similarity-threshold:0.6}")
    private double similarityThreshold;

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
        long start = System.currentTimeMillis();

        List<Document> documents = findSimilarDocuments(question);
        long afterSearch = System.currentTimeMillis();

        String context = documents.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));

        String sources = documents.stream()
                .map(doc -> {
                    Object filename = doc.getMetadata().get("filename");
                    return filename != null ? filename.toString() : "unknown";
                })
                .distinct()
                .collect(Collectors.joining(", "));

        log.info("Prompt context length: {} chars | Sources: {}", context.length(), sources);

        PromptTemplate template = new PromptTemplate(PROMPT_TEMPLATE);
        Map<String, Object> promptsParam = new HashMap<>();
        promptsParam.put("input", question);
        promptsParam.put("documents", context);
        promptsParam.put("sources", sources);

        String result = chatClient.prompt(template.create(promptsParam)).call().content();

        long afterLLM = System.currentTimeMillis();
        log.info("Search took: {} ms | LLM call took: {} ms", (afterSearch - start), (afterLLM - afterSearch));

        return result;
    }

    private List<Document> findSimilarDocuments(String question) {
        return vectorStore.similaritySearch(SearchRequest.builder()
                .query(question)
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .build());
    }

}
