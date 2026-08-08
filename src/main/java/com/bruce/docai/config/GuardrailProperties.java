package com.bruce.docai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Tunable safety limits applied by the agent core to every channel.
 */
@Component
@ConfigurationProperties(prefix = "app.guardrails")
@Data
public class GuardrailProperties {

    /** Inbound user messages longer than this are truncated before processing. */
    private int maxInputChars = 4000;

    /** Outbound replies longer than this are truncated. */
    private int maxOutputChars = 8000;

    /** Simple per-tenant abuse cap: max messages accepted per rolling minute. */
    private int requestsPerMinutePerOrg = 60;

    /** Case-insensitive terms redacted from outbound replies. */
    private List<String> blockedTerms = new ArrayList<>();
}
