package com.bruce.docai.service;

import com.bruce.docai.model.AgentConfig;
import com.bruce.docai.model.AgentMode;
import com.bruce.docai.model.ChatRequest;
import com.bruce.docai.model.Conversation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * The single agent core shared by every channel (admin chat UI, widget, WhatsApp).
 *
 * <p>It loads the tenant's {@link AgentConfig} and dispatches on {@link AgentMode}:
 * <ul>
 *   <li>{@code SIMPLE_RAG} — reproduces the original single-shot retrieval flow by
 *       delegating to {@link ChatService#getKnownInfo(String, String)}. Behaviorally
 *       identical to the pre-agent app.</li>
 *   <li>{@code AGENTIC} — builds a {@link ChatClient} from the configured system
 *       prompt, model, and temperature. Tool-calling (RAG as a tool) and conversation
 *       memory are attached in subsequent steps.</li>
 * </ul>
 */
@Service
@Slf4j
public class AgentOrchestrator {

    private final AgentConfigService agentConfigService;
    private final ChatService chatService;
    private final RagSearchTool ragSearchTool;
    private final ConversationMemoryService conversationMemoryService;
    private final ChatClient chatClient;

    public AgentOrchestrator(AgentConfigService agentConfigService,
                             ChatService chatService,
                             RagSearchTool ragSearchTool,
                             ConversationMemoryService conversationMemoryService,
                             ChatClient.Builder chatClientBuilder) {
        this.agentConfigService = agentConfigService;
        this.chatService = chatService;
        this.ragSearchTool = ragSearchTool;
        this.conversationMemoryService = conversationMemoryService;
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * Process a normalized chat request and return the agent's reply text.
     */
    public String handle(ChatRequest request) {
        AgentConfig config = agentConfigService.getForOrganization(request.organizationId());

        if (!config.enabled()) {
            log.info("Agent disabled for organization {}; returning unavailable message.", request.organizationId());
            return "The assistant is currently unavailable. Please try again later.";
        }

        log.info("Handling chat via channel={} org={} mode={}",
                request.channel(), request.organizationId(), config.mode());

        return switch (config.mode()) {
            case SIMPLE_RAG -> chatService.getKnownInfo(request.text(), request.organizationId());
            case AGENTIC -> handleAgentic(request, config);
        };
    }

    private String handleAgentic(ChatRequest request, AgentConfig config) {
        Conversation conversation = conversationMemoryService.resolve(request);
        List<Message> history = conversationMemoryService.loadHistory(conversation.id());

        ChatClient.ChatClientRequestSpec spec = chatClient.prompt();

        if (config.systemPrompt() != null && !config.systemPrompt().isBlank()) {
            spec = spec.system(config.systemPrompt());
        }
        if (!history.isEmpty()) {
            spec = spec.messages(history);
        }
        spec = spec.user(request.text());

        spec = spec.tools(ragSearchTool)
                .toolContext(Map.of(RagSearchTool.ORGANIZATION_ID_KEY, request.organizationId()));

        OllamaOptions options = buildOptions(config);
        if (options != null) {
            spec = spec.options(options);
        }

        String content = spec.call().content();
        String reply = content == null || content.isBlank()
                ? "I don't have enough information to answer that question."
                : content.strip();

        conversationMemoryService.record(conversation, request.text(), reply);
        return reply;
    }

    private OllamaOptions buildOptions(AgentConfig config) {
        if (config.model() == null && config.temperature() == null) {
            return null;
        }
        OllamaOptions.Builder builder = OllamaOptions.builder();
        if (config.model() != null && !config.model().isBlank()) {
            builder.model(config.model());
        }
        if (config.temperature() != null) {
            builder.temperature(config.temperature());
        }
        return builder.build();
    }
}
