package com.bruce.docai.model;

/**
 * Behavioral mode for a tenant's configurable agent.
 *
 * SIMPLE_RAG reproduces the original single-shot retrieval flow (one RAG lookup,
 * fixed prompt, stateless) and is the safe default. AGENTIC enables tool-calling
 * and conversation memory so the model can decide when to search, combine tools,
 * and hold multi-turn context.
 */
public enum AgentMode {
    SIMPLE_RAG,
    AGENTIC
}
