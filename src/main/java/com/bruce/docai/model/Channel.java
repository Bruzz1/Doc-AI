package com.bruce.docai.model;

/**
 * Origin platform of a chat request. Channels are thin transport adapters that all
 * funnel into the same agent core, so the channel is carried as metadata rather
 * than driving a separate code path.
 *
 * WEB_ADMIN is the existing Thymeleaf chat UI ({@code /app}), used by admins to
 * test configured agent behavior against the very same core the external channels use.
 */
public enum Channel {
    WEB_ADMIN,
    WIDGET,
    WHATSAPP
}
