package com.bruce.docai.service;

import com.bruce.docai.config.GuardrailProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuardrailServiceTest {

    private GuardrailService service(int reqPerMin, int maxIn, int maxOut, String... blocked) {
        GuardrailProperties props = new GuardrailProperties();
        props.setRequestsPerMinutePerOrg(reqPerMin);
        props.setMaxInputChars(maxIn);
        props.setMaxOutputChars(maxOut);
        props.setBlockedTerms(java.util.List.of(blocked));
        return new GuardrailService(props);
    }

    @Test
    void trimsAndTruncatesInboundText() {
        GuardrailService guardrails = service(60, 5, 100);

        assertEquals("hello", guardrails.enforceInbound("org-1", "  hello world  "));
    }

    @Test
    void rateLimitsPerOrganizationWithinWindow() {
        GuardrailService guardrails = service(2, 4000, 8000);

        guardrails.enforceInbound("org-1", "one");
        guardrails.enforceInbound("org-1", "two");

        GuardrailException ex = assertThrows(GuardrailException.class,
                () -> guardrails.enforceInbound("org-1", "three"));
        assertTrue(ex.getMessage().toLowerCase().contains("quickly"));
    }

    @Test
    void rateLimitIsIsolatedPerOrganization() {
        GuardrailService guardrails = service(1, 4000, 8000);

        guardrails.enforceInbound("org-1", "a");
        // A different tenant must not be affected by org-1's usage.
        assertEquals("b", guardrails.enforceInbound("org-2", "b"));
    }

    @Test
    void truncatesOutbound() {
        GuardrailService guardrails = service(60, 4000, 10);

        assertEquals("0123456789", guardrails.enforceOutbound("0123456789ABCDEF"));
    }

    @Test
    void redactsBlockedTermsCaseInsensitively() {
        GuardrailService guardrails = service(60, 4000, 4000, "secret");

        assertEquals("this is ***", guardrails.enforceOutbound("this is Secret"));
    }
}
