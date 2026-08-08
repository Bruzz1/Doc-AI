package com.bruce.docai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves the per-tenant {@code enabledTools} configuration into the concrete
 * {@link AgentTool} beans that should be handed to the model in AGENTIC mode.
 *
 * <p>All {@code AgentTool} beans are auto-discovered at startup and indexed by their
 * {@link AgentTool#name()}. This keeps {@link AgentOrchestrator} agnostic of which tools
 * exist: new tools become available simply by adding a new {@code AgentTool} bean and
 * listing its name in the admin agent configuration.
 */
@Component
@Slf4j
public class ToolRegistry {

    private final Map<String, AgentTool> toolsByName = new LinkedHashMap<>();

    public ToolRegistry(List<AgentTool> tools) {
        for (AgentTool tool : tools) {
            String name = tool.name() == null ? null : tool.name().trim();
            if (name == null || name.isEmpty()) {
                log.warn("Ignoring AgentTool {} with a blank name.", tool.getClass().getSimpleName());
                continue;
            }
            AgentTool previous = toolsByName.putIfAbsent(name, tool);
            if (previous != null) {
                log.warn("Duplicate AgentTool name '{}' ({} and {}); keeping the first.",
                        name, previous.getClass().getSimpleName(), tool.getClass().getSimpleName());
            }
        }
        log.info("Registered agent tools: {}", toolsByName.keySet());
    }

    /**
     * Resolve a comma-separated list of tool names into the matching tool beans.
     *
     * <p>A {@code null} or blank configuration enables <em>all</em> registered tools,
     * preserving backward-compatible behaviour for agents created before tools were
     * configurable. Unknown names are logged and skipped rather than failing the request.
     *
     * @return tool beans as {@code Object}s, ready to pass to Spring AI's
     *         {@code ChatClientRequestSpec.tools(Object...)}.
     */
    public List<Object> resolve(String enabledTools) {
        if (enabledTools == null || enabledTools.isBlank()) {
            return List.copyOf(toolsByName.values());
        }

        List<Object> resolved = new java.util.ArrayList<>();
        for (String raw : enabledTools.split(",")) {
            String name = raw.trim();
            if (name.isEmpty()) {
                continue;
            }
            AgentTool tool = toolsByName.get(name);
            if (tool == null) {
                log.warn("Unknown agent tool '{}' requested in configuration; skipping. Available: {}",
                        name, toolsByName.keySet());
                continue;
            }
            resolved.add(tool);
        }
        return List.copyOf(resolved);
    }

    /**
     * @return the names of all registered tools (for diagnostics/UI).
     */
    public java.util.Set<String> availableToolNames() {
        return java.util.Set.copyOf(toolsByName.keySet());
    }
}
