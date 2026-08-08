package com.bruce.docai.controller;

import com.bruce.docai.model.Channel;
import com.bruce.docai.model.ChatRequest;
import com.bruce.docai.service.AgentOrchestrator;
import com.bruce.docai.service.ChatService;
import com.bruce.docai.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

import java.util.Map;

/**
 * Admin/web chat surface ({@code /app}). The {@code /faqs} endpoint acts as the
 * {@link Channel#WEB_ADMIN} channel adapter: it normalizes the request into a
 * {@link ChatRequest} and delegates to the shared {@link AgentOrchestrator}, so an
 * admin testing here sees exactly what external channels produce for the same
 * tenant configuration.
 */
@RestController
@Slf4j
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final AgentOrchestrator agentOrchestrator;

    @GetMapping("/chat")
    public String chat(@RequestParam(value = "message") String question) {
        return chatService.chat(question);
    }

    @GetMapping("/faqs")
    public String getKnownInfo(@RequestParam(value = "question") String question, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        String organizationId = user.getOrganizationId();
        if (organizationId == null || organizationId.isBlank()) {
            throw new IllegalStateException("Your account is not assigned to an organization.");
        }
        ChatRequest request = ChatRequest.of(organizationId, Channel.WEB_ADMIN, user.getId(), question);
        return agentOrchestrator.handle(request);
    }

    /**
     * Start a fresh conversation ("New chat") for the current admin user's WEB_ADMIN
     * thread by clearing its stored memory.
     */
    @DeleteMapping("/faqs/conversation")
    public Map<String, String> resetConversation(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        String organizationId = user.getOrganizationId();
        if (organizationId == null || organizationId.isBlank()) {
            throw new IllegalStateException("Your account is not assigned to an organization.");
        }
        ChatRequest request = ChatRequest.of(organizationId, Channel.WEB_ADMIN, user.getId(), "");
        agentOrchestrator.resetConversation(request);
        return Map.of("status", "reset");
    }


}
