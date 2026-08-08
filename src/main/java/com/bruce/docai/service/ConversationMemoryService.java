package com.bruce.docai.service;

import com.bruce.docai.model.ChatRequest;
import com.bruce.docai.model.Conversation;
import com.bruce.docai.model.ConversationMessage;
import com.bruce.docai.model.MessageRole;
import com.bruce.docai.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Multi-turn conversation memory for AGENTIC agents. Resolves the conversation for a
 * request, exposes prior turns as Spring AI {@link Message}s for prompt construction,
 * and records new turns. SIMPLE_RAG never calls into this service and stays stateless.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ConversationMemoryService {

    /** Maximum number of prior turns replayed into the prompt. */
    static final int HISTORY_LIMIT = 20;

    private final ConversationRepository conversationRepository;

    /**
     * Resolve (or create) the conversation for a request. An explicit
     * {@link ChatRequest#conversationId()} wins; otherwise the conversation is keyed
     * by {@code (organizationId, channel, userRef)}.
     */
    public Conversation resolve(ChatRequest request) {
        if (request.conversationId() != null && !request.conversationId().isBlank()) {
            UUID id = parseId(request.conversationId());
            if (id != null) {
                return conversationRepository.findById(id, request.organizationId())
                        .orElseGet(() -> findOrCreate(request));
            }
        }
        return findOrCreate(request);
    }

    /**
     * Prior turns as Spring AI messages, oldest first. TOOL messages are not replayed.
     */
    public List<Message> loadHistory(UUID conversationId) {
        List<Message> history = new ArrayList<>();
        for (ConversationMessage stored : conversationRepository.findRecentMessages(conversationId, HISTORY_LIMIT)) {
            if (stored.content() == null || stored.content().isBlank()) {
                continue;
            }
            switch (stored.role()) {
                case USER -> history.add(new UserMessage(stored.content()));
                case ASSISTANT -> history.add(new AssistantMessage(stored.content()));
                case TOOL -> { /* tool output is not replayed into later turns */ }
            }
        }
        return history;
    }

    /**
     * Persist a completed user/assistant exchange and bump the conversation activity.
     */
    public void record(Conversation conversation, String userText, String assistantText) {
        String organizationId = conversation.organizationId();
        UUID conversationId = conversation.id();

        conversationRepository.insertMessage(new ConversationMessage(
                null, conversationId, organizationId, MessageRole.USER, userText, null, null));
        conversationRepository.insertMessage(new ConversationMessage(
                null, conversationId, organizationId, MessageRole.ASSISTANT, assistantText, null, null));
        conversationRepository.touch(conversationId);
    }

    private Conversation findOrCreate(ChatRequest request) {
        String userRef = request.userRef() == null || request.userRef().isBlank()
                ? "anonymous"
                : request.userRef();
        return conversationRepository.findOrCreate(request.organizationId(), request.channel(), userRef);
    }

    private UUID parseId(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            log.warn("Ignoring malformed conversationId '{}'.", value);
            return null;
        }
    }
}
