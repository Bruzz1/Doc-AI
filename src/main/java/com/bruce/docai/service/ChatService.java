package com.bruce.docai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaModel;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final OllamaChatModel chatModel;


    public String chat(String prompt) {
        ChatResponse response = chatModel.call(
                new Prompt(
                        prompt,
                        OllamaOptions.builder()
                                .model(OllamaModel.LLAMA3)
                                .build()
                ));
        return response.getResult().getOutput().getText();
    }

}
