package com.bruce.docai.controller;

import com.bruce.docai.model.Channel;
import com.bruce.docai.model.ChatRequest;
import com.bruce.docai.service.AgentOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Embeddable website chat widget ({@link Channel#WIDGET}) channel adapter.
 *
 * <p>Public (unauthenticated) endpoint: an organization embeds the widget on its own
 * site with its {@code organizationId}, and end users chat anonymously. The adapter
 * normalizes the JSON payload into a {@link ChatRequest} and delegates to the shared
 * {@link AgentOrchestrator}; tenant isolation is enforced downstream by scoping all
 * retrieval to {@code organizationId}. A per-browser {@code sessionId} keys the
 * conversation so AGENTIC memory persists across turns.
 *
 * <p>Security note: the widget trusts the {@code organizationId} embedded in the page,
 * which is appropriate for a public knowledge assistant. Introduce a per-tenant public
 * widget key if stricter origin binding is required.
 */
@RestController
@RequestMapping("/widget")
@CrossOrigin(origins = "*")
@Slf4j
@RequiredArgsConstructor
public class WidgetController {

    private final AgentOrchestrator agentOrchestrator;

    @PostMapping("/chat")
    public WidgetChatResponse chat(@RequestBody WidgetChatRequest body) {
        if (body == null || body.organizationId() == null || body.organizationId().isBlank()) {
            throw new IllegalArgumentException("organizationId is required.");
        }
        if (body.message() == null || body.message().isBlank()) {
            throw new IllegalArgumentException("message is required.");
        }

        ChatRequest request = new ChatRequest(
                body.organizationId(),
                Channel.WIDGET,
                body.sessionId(),
                body.message(),
                body.conversationId());

        String reply = agentOrchestrator.handle(request);
        return new WidgetChatResponse(reply);
    }

    /**
     * @param organizationId public tenant identifier embedded in the widget snippet
     * @param sessionId      stable per-browser id used to maintain conversation memory
     * @param message        the user's message
     * @param conversationId optional explicit conversation to continue
     */
    public record WidgetChatRequest(
            String organizationId,
            String sessionId,
            String message,
            String conversationId) {
    }

    public record WidgetChatResponse(String reply) {
    }
}
