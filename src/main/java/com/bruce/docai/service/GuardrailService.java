package com.bruce.docai.service;

import com.bruce.docai.config.GuardrailProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies input/output guardrails around the agent core: per-tenant rate limiting,
 * input/output length caps, and redaction of configured blocked terms. Shared by
 * every channel via {@link AgentOrchestrator}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GuardrailService {

    private static final long WINDOW_MILLIS = 60_000L;
    private static final String REDACTION = "***";

    private final GuardrailProperties properties;
    private final Map<String, Deque<Long>> requestTimestampsByOrg = new ConcurrentHashMap<>();

    /**
     * Enforce inbound guardrails and return the sanitized message to process.
     *
     * @throws GuardrailException if the tenant has exceeded its request rate
     */
    public String enforceInbound(String organizationId, String text) {
        if (isRateLimited(organizationId)) {
            log.warn("Rate limit exceeded for organization {}.", organizationId);
            throw new GuardrailException("You're sending messages too quickly. Please wait a moment and try again.");
        }

        String sanitized = text == null ? "" : text.strip();
        int max = Math.max(1, properties.getMaxInputChars());
        if (sanitized.length() > max) {
            sanitized = sanitized.substring(0, max);
        }
        return sanitized;
    }

    /**
     * Enforce outbound guardrails: truncate over-long replies and redact blocked terms.
     */
    public String enforceOutbound(String text) {
        if (text == null) {
            return "";
        }
        String result = text;
        for (String term : properties.getBlockedTerms()) {
            if (term != null && !term.isBlank()) {
                result = redact(result, term);
            }
        }
        int max = Math.max(1, properties.getMaxOutputChars());
        if (result.length() > max) {
            result = result.substring(0, max);
        }
        return result;
    }

    private boolean isRateLimited(String organizationId) {
        int limit = properties.getRequestsPerMinutePerOrg();
        if (limit <= 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        Deque<Long> timestamps = requestTimestampsByOrg.computeIfAbsent(organizationId, key -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() >= WINDOW_MILLIS) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= limit) {
                return true;
            }
            timestamps.addLast(now);
            return false;
        }
    }

    private String redact(String text, String term) {
        StringBuilder result = new StringBuilder();
        String lowerText = text.toLowerCase(Locale.ROOT);
        String lowerTerm = term.toLowerCase(Locale.ROOT);
        int from = 0;
        int index;
        while ((index = lowerText.indexOf(lowerTerm, from)) >= 0) {
            result.append(text, from, index).append(REDACTION);
            from = index + term.length();
        }
        result.append(text.substring(from));
        return result.toString();
    }
}
