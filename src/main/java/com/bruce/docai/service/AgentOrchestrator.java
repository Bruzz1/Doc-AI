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
 *       prompt, model, and temperature. The tenant's configured tools (resolved via
 *       {@link ToolRegistry} from {@code AgentConfig.enabledTools()}) and conversation
 *       memory are attached.</li>
 * </ul>
 */
@Service
@Slf4j
public class AgentOrchestrator {

    static final String DEFAULT_DISABLED_MESSAGE =
            "The assistant is currently unavailable. Please try again later.";

    private final AgentConfigService agentConfigService;
    private final ChatService chatService;
    private final ToolRegistry toolRegistry;
    private final ConversationMemoryService conversationMemoryService;
    private final GuardrailService guardrailService;
    private final ChatClient chatClient;

    public AgentOrchestrator(AgentConfigService agentConfigService,
                             ChatService chatService,
                             ToolRegistry toolRegistry,
                             ConversationMemoryService conversationMemoryService,
                             GuardrailService guardrailService,
                             ChatClient.Builder chatClientBuilder) {
        this.agentConfigService = agentConfigService;
        this.chatService = chatService;
        this.toolRegistry = toolRegistry;
        this.conversationMemoryService = conversationMemoryService;
        this.guardrailService = guardrailService;
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * Process a normalized chat request and return the agent's reply text.
     */
    public String handle(ChatRequest request) {
        AgentConfig config = agentConfigService.getForOrganization(request.organizationId());

        if (!config.enabled()) {
            log.info("Agent disabled for organization {}; returning unavailable message.", request.organizationId());
            String message = config.disabledMessage();
            return message == null || message.isBlank() ? DEFAULT_DISABLED_MESSAGE : message;
        }

        String input;
        try {
            input = guardrailService.enforceInbound(request.organizationId(), request.text());
        } catch (GuardrailException ex) {
            return ex.getMessage();
        }

        log.info("Handling chat via channel={} org={} mode={}",
                request.channel(), request.organizationId(), config.mode());

        String reply = switch (config.mode()) {
            case SIMPLE_RAG -> chatService.getKnownInfo(input, request.organizationId());
            case AGENTIC -> handleAgentic(request, config, input);
        };
        return guardrailService.enforceOutbound(reply);
    }

    /**
     * Clear the conversation history for the request's identity (the "new chat" reset).
     * No-op for stateless SIMPLE_RAG semantics beyond clearing any stored memory.
     */
    public void resetConversation(ChatRequest request) {
        conversationMemoryService.reset(request);
    }

    private String handleAgentic(ChatRequest request, AgentConfig config, String input) {
        Conversation conversation = conversationMemoryService.resolve(request);
        List<Message> history = conversationMemoryService.loadHistory(conversation.id());

        ChatClient.ChatClientRequestSpec spec = chatClient.prompt();

        if (config.systemPrompt() != null && !config.systemPrompt().isBlank()) {
            spec = spec.system(config.systemPrompt());
        }
        if (!history.isEmpty()) {
            spec = spec.messages(history);
        }
        spec = spec.user(input);

        List<Object> tools = toolRegistry.resolve(config.enabledTools());
        if (!tools.isEmpty()) {
            spec = spec.tools(tools.toArray())
                    .toolContext(Map.of(RagSearchTool.ORGANIZATION_ID_KEY, request.organizationId()));
        }

        OllamaOptions options = buildOptions(config);
        if (options != null) {
            spec = spec.options(options);
        }

        String content = spec.call().content();
        String reply = content == null || content.isBlank()
                ? "I don't have enough information to answer that question."
                : content.strip();

        conversationMemoryService.record(conversation, input, reply);
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
