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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
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
     * Minutes of inactivity after which a returning user's conversation is treated as a
     * fresh start (its prior messages are cleared). {@code 0} disables the timeout so
     * conversations resume indefinitely.
     */
    @Value("${app.agent.conversation-idle-minutes:30}")
    private long idleMinutes;

    /**
     * Resolve (or create) the conversation for a request. An explicit
     * {@link ChatRequest#conversationId()} wins; otherwise the conversation is keyed
     * by {@code (organizationId, channel, userRef)}.
     *
     * <p>If the resolved conversation has been idle longer than
     * {@code app.agent.conversation-idle-minutes}, its history is cleared so the next
     * turn starts fresh — preventing stale context from bleeding across sessions.
     */
    public Conversation resolve(ChatRequest request) {
        Conversation conversation = resolveRaw(request);
        if (isIdle(conversation)) {
            log.info("Conversation {} idle for over {} min; clearing history for a fresh start.",
                    conversation.id(), idleMinutes);
            conversationRepository.clearMessages(conversation.id());
        }
        return conversation;
    }

    private Conversation resolveRaw(ChatRequest request) {
        if (request.conversationId() != null && !request.conversationId().isBlank()) {
            UUID id = parseId(request.conversationId());
            if (id != null) {
                return conversationRepository.findById(id, request.organizationId())
                        .orElseGet(() -> findOrCreate(request));
            }
        }
        return findOrCreate(request);
    }

    private boolean isIdle(Conversation conversation) {
        if (idleMinutes <= 0 || conversation.lastActivityAt() == null) {
            return false;
        }
        Duration inactivity = Duration.between(conversation.lastActivityAt(), Instant.now());
        return inactivity.compareTo(Duration.ofMinutes(idleMinutes)) > 0;
    }

    /**
     * Explicitly clear the conversation history for a request's
     * {@code (organizationId, channel, userRef)} — the "new chat" reset.
     */
    public void reset(ChatRequest request) {
        Conversation conversation = findOrCreate(request);
        conversationRepository.clearMessages(conversation.id());
        log.info("Reset conversation {} for org={} channel={}.",
                conversation.id(), request.organizationId(), request.channel());
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
