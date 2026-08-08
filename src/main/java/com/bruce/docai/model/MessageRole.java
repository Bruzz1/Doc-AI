package com.bruce.docai.model;

/**
 * Author of a stored conversation message. TOOL captures tool/function output when
 * an AGENTIC agent invokes a tool such as the RAG search.
 */
public enum MessageRole {
    USER,
    ASSISTANT,
    TOOL
}
