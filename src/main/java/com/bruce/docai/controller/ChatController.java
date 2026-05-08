package com.bruce.docai.controller;

import com.bruce.docai.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    private final OllamaChatModel chatModel;

    private final VectorStore vectorStore;

    private String prompt = """
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


    @GetMapping("/generate")
    public String chat(@RequestParam(value = "promptMessage") String question) {
        return chatService.chat(prompt);
    }

    @GetMapping("/v1")
    public String simplify(@RequestParam(value = "question") String question) {
        PromptTemplate template = new PromptTemplate(prompt);

        Map<String, Object> promptsParam = new HashMap<>();
        promptsParam.put("input", question);
        promptsParam.put("documents", findSimilarData(question));


        return chatModel
                .call(template.create(promptsParam))
                .getResult()
                .getOutput()
                .getText();

    }

    private Object findSimilarData(String question) {
        List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                .query(question)
                .topK(2)
                .build());

        return documents.stream()
                .map(Document::getText)
                .collect(Collectors.joining());
    }


}
