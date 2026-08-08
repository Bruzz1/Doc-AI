package com.bruce.docai.service;

import com.bruce.docai.model.AgentConfig;
import com.bruce.docai.model.AgentMode;
import com.bruce.docai.model.Channel;
import com.bruce.docai.model.ChatRequest;
import com.bruce.docai.model.Conversation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.ai.chat.client.ChatClient;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentOrchestratorTest {

    private AgentConfigService agentConfigService;
    private ChatService chatService;
    private ToolRegistry toolRegistry;
    private ConversationMemoryService memoryService;
    private GuardrailService guardrailService;
    private AgentOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        agentConfigService = mock(AgentConfigService.class);
        chatService = mock(ChatService.class);
        toolRegistry = mock(ToolRegistry.class);
        memoryService = mock(ConversationMemoryService.class);
        guardrailService = mock(GuardrailService.class);

        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
        when(builder.build()).thenReturn(chatClient);

        // By default guardrails pass through unchanged.
        when(guardrailService.enforceInbound(anyString(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        when(guardrailService.enforceOutbound(anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        orchestrator = new AgentOrchestrator(
                agentConfigService, chatService, toolRegistry, memoryService, guardrailService, builder);
    }

    private AgentConfig config(AgentMode mode, boolean enabled) {
        return new AgentConfig(UUID.randomUUID(), "org-1", "Default Agent", mode,
                null, null, null, null, null, null, null, enabled, null, Instant.now(), Instant.now());
    }

    private AgentConfig config(AgentMode mode, boolean enabled, String disabledMessage) {
        return new AgentConfig(UUID.randomUUID(), "org-1", "Default Agent", mode,
                null, null, null, null, null, null, null, enabled, disabledMessage, Instant.now(), Instant.now());
    }

    @Test
    void simpleRagDelegatesToChatServiceScopedToTenant() {
        when(agentConfigService.getForOrganization("org-1")).thenReturn(config(AgentMode.SIMPLE_RAG, true));
        when(chatService.getKnownInfo("hello", "org-1")).thenReturn("grounded answer");

        String reply = orchestrator.handle(ChatRequest.of("org-1", Channel.WEB_ADMIN, "user-1", "hello"));

        assertEquals("grounded answer", reply);
        verify(chatService).getKnownInfo("hello", "org-1");
        verify(guardrailService).enforceInbound("org-1", "hello");
        verify(guardrailService).enforceOutbound("grounded answer");
    }

    @Test
    void disabledAgentReturnsUnavailableWithoutCallingModel() {
        when(agentConfigService.getForOrganization("org-1")).thenReturn(config(AgentMode.SIMPLE_RAG, false));

        String reply = orchestrator.handle(ChatRequest.of("org-1", Channel.WIDGET, "u", "hi"));

        assertTrue(reply.toLowerCase().contains("unavailable"));
        verify(chatService, never()).getKnownInfo(anyString(), anyString());
        verify(guardrailService, never()).enforceInbound(anyString(), anyString());
    }

    @Test
    void disabledAgentReturnsConfiguredMessageWhenPresent() {
        when(agentConfigService.getForOrganization("org-1"))
                .thenReturn(config(AgentMode.SIMPLE_RAG, false, "Offline for maintenance until 5 PM."));

        String reply = orchestrator.handle(ChatRequest.of("org-1", Channel.WHATSAPP, "u", "hi"));

        assertEquals("Offline for maintenance until 5 PM.", reply);
        verify(chatService, never()).getKnownInfo(anyString(), anyString());
    }

    @Test
    void guardrailViolationShortCircuitsBeforeModel() {
        when(agentConfigService.getForOrganization("org-1")).thenReturn(config(AgentMode.SIMPLE_RAG, true));
        when(guardrailService.enforceInbound("org-1", "spam"))
                .thenThrow(new GuardrailException("Slow down."));

        String reply = orchestrator.handle(ChatRequest.of("org-1", Channel.WHATSAPP, "u", "spam"));

        assertEquals("Slow down.", reply);
        verify(chatService, never()).getKnownInfo(anyString(), anyString());
    }

    @Test
    void agenticModeResolvesConversationAndRecordsTurn() {
        when(agentConfigService.getForOrganization("org-1")).thenReturn(config(AgentMode.AGENTIC, true));
        when(toolRegistry.resolve(any())).thenReturn(List.of());
        Conversation conversation = new Conversation(
                UUID.randomUUID(), "org-1", Channel.WEB_ADMIN, "user-1", Instant.now(), Instant.now());
        when(memoryService.resolve(any(ChatRequest.class))).thenReturn(conversation);
        when(memoryService.loadHistory(conversation.id())).thenReturn(List.of());

        String reply = orchestrator.handle(ChatRequest.of("org-1", Channel.WEB_ADMIN, "user-1", "hi there"));

        verify(memoryService).resolve(any(ChatRequest.class));
        verify(memoryService).loadHistory(conversation.id());
        verify(memoryService).record(eq(conversation), eq("hi there"), anyString());
        verify(toolRegistry).resolve(any());
        verify(guardrailService).enforceOutbound(anyString());
        // SIMPLE_RAG path must not be used in AGENTIC mode.
        verify(chatService, never()).getKnownInfo(anyString(), anyString());
        assertTrue(reply != null && !reply.isBlank());
    }
}
