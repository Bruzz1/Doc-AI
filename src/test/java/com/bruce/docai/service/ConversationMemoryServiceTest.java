package com.bruce.docai.service;

import com.bruce.docai.model.Channel;
import com.bruce.docai.model.ChatRequest;
import com.bruce.docai.model.Conversation;
import com.bruce.docai.repository.ConversationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationMemoryServiceTest {

    private ConversationRepository repository;
    private ConversationMemoryService service;

    @BeforeEach
    void setUp() {
        repository = mock(ConversationRepository.class);
        service = new ConversationMemoryService(repository);
        ReflectionTestUtils.setField(service, "idleMinutes", 30L);
    }

    private ChatRequest request() {
        return ChatRequest.of("org-1", Channel.WEB_ADMIN, "user-1", "hi");
    }

    private Conversation conversationLastActive(Instant lastActivity) {
        return new Conversation(UUID.randomUUID(), "org-1", Channel.WEB_ADMIN, "user-1",
                Instant.now().minus(2, ChronoUnit.HOURS), lastActivity);
    }

    @Test
    void resolveClearsHistoryWhenConversationIsIdleBeyondThreshold() {
        Conversation idle = conversationLastActive(Instant.now().minus(45, ChronoUnit.MINUTES));
        when(repository.findOrCreate("org-1", Channel.WEB_ADMIN, "user-1")).thenReturn(idle);

        Conversation resolved = service.resolve(request());

        verify(repository).clearMessages(idle.id());
        // Same conversation row is reused; only its history is cleared.
        org.junit.jupiter.api.Assertions.assertEquals(idle.id(), resolved.id());
    }

    @Test
    void resolveKeepsHistoryWhenConversationIsRecentlyActive() {
        Conversation active = conversationLastActive(Instant.now().minus(5, ChronoUnit.MINUTES));
        when(repository.findOrCreate("org-1", Channel.WEB_ADMIN, "user-1")).thenReturn(active);

        service.resolve(request());

        verify(repository, never()).clearMessages(any());
    }

    @Test
    void idleTimeoutDisabledWhenIdleMinutesIsZero() {
        ReflectionTestUtils.setField(service, "idleMinutes", 0L);
        Conversation old = conversationLastActive(Instant.now().minus(10, ChronoUnit.DAYS));
        when(repository.findOrCreate("org-1", Channel.WEB_ADMIN, "user-1")).thenReturn(old);

        service.resolve(request());

        verify(repository, never()).clearMessages(any());
    }

    @Test
    void resetClearsHistoryForResolvedConversation() {
        Conversation conversation = conversationLastActive(Instant.now());
        when(repository.findOrCreate("org-1", Channel.WEB_ADMIN, "user-1")).thenReturn(conversation);

        service.reset(request());

        verify(repository).clearMessages(conversation.id());
    }

    @Test
    void anonymousUserRefIsNormalizedWhenResolving() {
        Conversation conversation = new Conversation(UUID.randomUUID(), "org-1", Channel.WIDGET, "anonymous",
                Instant.now(), Instant.now());
        when(repository.findOrCreate(eq("org-1"), eq(Channel.WIDGET), anyString())).thenReturn(conversation);

        ChatRequest anonymous = ChatRequest.of("org-1", Channel.WIDGET, null, "hi");
        service.resolve(anonymous);

        verify(repository).findOrCreate("org-1", Channel.WIDGET, "anonymous");
    }
}
