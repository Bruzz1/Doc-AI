package com.bruce.docai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for the WhatsApp Cloud API channel.
 *
 * <p>{@code phoneNumberOrgMap} maps an inbound WhatsApp business
 * {@code phone_number_id} to the owning tenant so multiple organizations can share
 * one deployment. {@code defaultOrganizationId} is used when no mapping matches.
 */
@Component
@ConfigurationProperties(prefix = "app.whatsapp")
@Data
public class WhatsappProperties {

    /** Token echoed back during the GET verification handshake. */
    private String verifyToken = "";

    /** Meta app secret used to validate the X-Hub-Signature-256 header. */
    private String appSecret = "";

    /** Bearer token used to call the Graph API when sending replies. */
    private String accessToken = "";

    /** Graph API base URL (version-pinned). */
    private String apiBaseUrl = "https://graph.facebook.com/v20.0";

    /** Fallback tenant when a phone_number_id is not explicitly mapped. */
    private String defaultOrganizationId = "";

    /** phone_number_id -> organizationId. */
    private Map<String, String> phoneNumberOrgMap = new HashMap<>();
}
