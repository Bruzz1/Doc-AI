package com.bruce.docai.service;

/**
 * Marker for a Spring AI tool that can be enabled per tenant via
 * {@code AgentConfig.enabledTools()}.
 *
 * <p>Each implementation exposes a stable {@link #name()} (the token stored in the
 * comma-separated {@code enabledTools} column, e.g. {@code "rag_search"}). The
 * {@link ToolRegistry} auto-discovers every {@code AgentTool} bean, so adding a new
 * tool is just a matter of creating a {@code @Component} that implements this interface
 * and carries one or more {@code @Tool}-annotated methods — no change to
 * {@link AgentOrchestrator} is required.
 */
public interface AgentTool {

    /**
     * Stable identifier used in the {@code enabledTools} configuration. Must be unique
     * across tools and should be lowercase snake_case.
     */
    String name();
}
