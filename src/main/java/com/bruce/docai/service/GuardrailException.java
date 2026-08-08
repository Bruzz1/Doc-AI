package com.bruce.docai.service;

/**
 * Raised when an inbound request violates a guardrail (e.g. the per-tenant rate
 * limit). The message is safe to surface directly to the end user.
 */
public class GuardrailException extends RuntimeException {

    public GuardrailException(String message) {
        super(message);
    }
}
