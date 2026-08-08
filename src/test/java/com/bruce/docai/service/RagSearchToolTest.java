package com.bruce.docai.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagSearchToolTest {

    @Test
    void usesTenantFromToolContextAndDelegatesToRetrieval() {
        ChatService chatService = mock(ChatService.class);
        when(chatService.retrieveKnowledge("what is the policy?", "org-9")).thenReturn("CONTEXT");
        RagSearchTool tool = new RagSearchTool(chatService);

        ToolContext context = new ToolContext(Map.of(RagSearchTool.ORGANIZATION_ID_KEY, "org-9"));
        String result = tool.searchKnowledgeBase("what is the policy?", context);

        assertEquals("CONTEXT", result);
        verify(chatService).retrieveKnowledge("what is the policy?", "org-9");
    }

    @Test
    void rejectsInvocationWithoutTenantInContext() {
        ChatService chatService = mock(ChatService.class);
        RagSearchTool tool = new RagSearchTool(chatService);

        ToolContext empty = new ToolContext(Map.of());
        assertThrows(IllegalStateException.class, () -> tool.searchKnowledgeBase("q", empty));
    }
}
