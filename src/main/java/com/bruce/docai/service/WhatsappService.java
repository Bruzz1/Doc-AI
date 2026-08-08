package com.bruce.docai.service;

import com.bruce.docai.config.AsyncConfig;
import com.bruce.docai.config.WhatsappProperties;
import com.bruce.docai.model.Channel;
import com.bruce.docai.model.ChatRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/**
 * WhatsApp Cloud API channel adapter logic: request authenticity, inbound parsing,
 * tenant resolution, and outbound replies. Inbound messages are processed
 * asynchronously so the webhook can acknowledge immediately.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WhatsappService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_PREFIX = "sha256=";

    private final WhatsappProperties properties;
    private final AgentOrchestrator agentOrchestrator;
    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;

    /**
     * Validate the {@code X-Hub-Signature-256} header against the raw request body.
     * When no app secret is configured (local/dev), validation is skipped.
     */
    public boolean isValidSignature(byte[] payload, String signatureHeader) {
        if (properties.getAppSecret() == null || properties.getAppSecret().isBlank()) {
            log.warn("WhatsApp app secret not configured; skipping signature verification.");
            return true;
        }
        if (signatureHeader == null || !signatureHeader.startsWith(SIGNATURE_PREFIX)) {
            return false;
        }
        String expected = SIGNATURE_PREFIX + hmacSha256Hex(payload, properties.getAppSecret());
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signatureHeader.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Parse and handle a webhook payload asynchronously: for each inbound text message,
     * run the agent for the resolving tenant and send the reply back over WhatsApp.
     */
    @Async(AsyncConfig.WHATSAPP_EXECUTOR)
    public void handleInboundAsync(byte[] payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            for (JsonNode entry : root.path("entry")) {
                for (JsonNode change : entry.path("changes")) {
                    processChange(change.path("value"));
                }
            }
        } catch (Exception ex) {
            log.error("Failed to process WhatsApp webhook payload.", ex);
        }
    }

    private void processChange(JsonNode value) {
        String phoneNumberId = value.path("metadata").path("phone_number_id").asText(null);
        String organizationId = resolveOrganization(phoneNumberId);
        if (organizationId == null || organizationId.isBlank()) {
            log.warn("No tenant mapped for WhatsApp phone_number_id={}; ignoring messages.", phoneNumberId);
            return;
        }

        for (JsonNode message : value.path("messages")) {
            if (!"text".equals(message.path("type").asText())) {
                continue;
            }
            String from = message.path("from").asText(null);
            String text = message.path("text").path("body").asText("");
            if (from == null || text.isBlank()) {
                continue;
            }

            try {
                ChatRequest request = ChatRequest.of(organizationId, Channel.WHATSAPP, from, text);
                String reply = agentOrchestrator.handle(request);
                sendTextMessage(phoneNumberId, from, reply);
            } catch (Exception ex) {
                log.error("Failed to answer WhatsApp message from {} for org {}.", from, organizationId, ex);
            }
        }
    }

    String resolveOrganization(String phoneNumberId) {
        if (phoneNumberId != null) {
            String mapped = properties.getPhoneNumberOrgMap().get(phoneNumberId);
            if (mapped != null && !mapped.isBlank()) {
                return mapped;
            }
        }
        return properties.getDefaultOrganizationId();
    }

    private void sendTextMessage(String phoneNumberId, String to, String body) {
        if (properties.getAccessToken() == null || properties.getAccessToken().isBlank()) {
            log.warn("WhatsApp access token not configured; would have replied to {} with: {}", to, body);
            return;
        }

        Map<String, Object> requestBody = Map.of(
                "messaging_product", "whatsapp",
                "recipient_type", "individual",
                "to", to,
                "type", "text",
                "text", Map.of("preview_url", false, "body", body));

        try {
            restClientBuilder.build()
                    .post()
                    .uri(properties.getApiBaseUrl() + "/{phoneNumberId}/messages", phoneNumberId)
                    .header("Authorization", "Bearer " + properties.getAccessToken())
                    .header("Content-Type", "application/json")
                    .body(requestBody)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.error("Failed to send WhatsApp reply to {}.", to, ex);
        }
    }

    private String hmacSha256Hex(byte[] payload, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(payload));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to compute WhatsApp signature.", ex);
        }
    }
}
