package com.bruce.docai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaModel;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ChatService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;


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

        PromptTemplate template = getPromptTemplate();

        Map<String, Object> promptsParam = new HashMap<>();
        promptsParam.put("input", question);
        promptsParam.put("documents", findSimilarData(question));
        long afterSearch = System.currentTimeMillis();

        String result = chatClient.prompt(template.create(promptsParam)).call().content();

        long afterLLM = System.currentTimeMillis();

        log.info("Search took: {} ms", (afterSearch - start));
        log.info("LLM call took: {} ms", (afterLLM - afterSearch));
        return  result;


    }

    private static PromptTemplate getPromptTemplate() {
        String prompt = """
                You are tasked with answering a question about Roland Jay (RJ) Bruce, the developer of this application.
                Use only the information provided in the DOCUMENTS section to answer. Respond confidently and directly—do not 
                mention or reference the documents, even implicitly (e.g., avoid phrases like "based on the documents" or "according 
                to the information provided").
                
                If the answer is not found or is unclear from the documents, respond with:
                "The answer is not available in the provided documents."
                
                QUESTION:
                {input}
                
                DOCUMENTS:
                {documents}
                """;
        PromptTemplate template = new PromptTemplate(prompt);
        return template;
    }

    private Object findSimilarData(String question) {
        List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                .query(question)
                .topK(2)
                .build());

        String context = documents.stream()
                .map(Document::getText)
                .collect(Collectors.joining());
        log.info("Prompt token length: "+context.length());
        return context;
    }

}
