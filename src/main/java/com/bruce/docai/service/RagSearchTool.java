package com.bruce.docai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Exposes the existing RAG retrieval pipeline as a Spring AI tool so an AGENTIC
 * agent can decide when to search the knowledge base.
 *
 * <p>The owning tenant is never chosen by the model: it is supplied out-of-band via
 * {@link ToolContext} (key {@value #ORGANIZATION_ID_KEY}) that the orchestrator sets
 * per request, preserving tenant isolation. The model only supplies the search query.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RagSearchTool {

    public static final String ORGANIZATION_ID_KEY = "organizationId";

    private final ChatService chatService;

    @Tool(description = "Search the organization's private knowledge base (uploaded documents "
            + "and FAQs) for information relevant to the user's question. Call this whenever the "
            + "answer may depend on the organization's own documents. Returns the most relevant "
            + "text passages with their sources.")
    public String searchKnowledgeBase(
            @ToolParam(description = "A focused natural-language search query describing the "
                    + "information needed.") String query,
            ToolContext toolContext) {

        String organizationId = organizationId(toolContext);
        log.info("RAG tool invoked for org={} query='{}'", organizationId, query);
        return chatService.retrieveKnowledge(query, organizationId);
    }

    private String organizationId(ToolContext toolContext) {
        Object value = toolContext == null ? null : toolContext.getContext().get(ORGANIZATION_ID_KEY);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalStateException("RAG tool invoked without a tenant in the tool context.");
        }
        return value.toString();
    }
}
