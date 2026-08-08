package com.bruce.docai.model;

/**
 * Normalized, channel-agnostic chat request produced by every channel adapter
 * (admin chat UI, widget, WhatsApp) and consumed by the agent core.
 *
 * <p>Adapters own transport concerns (authentication, payload parsing, sync vs.
 * async reply, platform-specific formatting) and collapse them into this single
 * shape so the {@code AgentOrchestrator} never needs to know where a message came
 * from.
 *
 * @param organizationId owning tenant; all retrieval and config lookups are scoped by it
 * @param channel        origin platform (carried as metadata, not a branch)
 * @param userRef        stable per-channel identifier of the end user (e.g. user id,
 *                       widget session id, WhatsApp phone number); used together with
 *                       {@code channel} to resolve a conversation for memory
 * @param text           the user's message
 * @param conversationId optional existing conversation to continue; {@code null} lets the
 *                       core resolve/create one from {@code (organizationId, channel, userRef)}
 */
public record ChatRequest(
        String organizationId,
        Channel channel,
        String userRef,
        String text,
        String conversationId
) {

    public ChatRequest {
        if (organizationId == null || organizationId.isBlank()) {
            throw new IllegalArgumentException("A chat request must be scoped to an organization.");
        }
        if (channel == null) {
            throw new IllegalArgumentException("A chat request must specify a channel.");
        }
        text = text == null ? "" : text.strip();
    }

    /** Convenience factory for stateless, single-turn requests (e.g. WEB_ADMIN test chat). */
    public static ChatRequest of(String organizationId, Channel channel, String userRef, String text) {
        return new ChatRequest(organizationId, channel, userRef, text, null);
    }
}
