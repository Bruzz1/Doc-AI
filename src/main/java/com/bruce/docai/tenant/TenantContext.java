package com.bruce.docai.tenant;

import java.util.Optional;

/**
 * Holds the organization (tenant) of the current request thread.
 *
 * <p>Populated once per request from the authenticated principal in
 * {@code JwtAuthFilter}, and read by services instead of threading the
 * {@code organizationId} through every method signature. This is the single
 * source of truth for "which tenant is this request for".</p>
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_ORG = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(String organizationId) {
        if (organizationId == null || organizationId.isBlank()) {
            CURRENT_ORG.remove();
        } else {
            CURRENT_ORG.set(organizationId);
        }
    }

    public static Optional<String> get() {
        return Optional.ofNullable(CURRENT_ORG.get());
    }

    /**
     * Returns the current tenant or throws if none is set. Use on paths that must
     * never run without a tenant (document management, RAG retrieval).
     */
    public static String require() {
        String organizationId = CURRENT_ORG.get();
        if (organizationId == null || organizationId.isBlank()) {
            throw new IllegalStateException("No organization is bound to the current request.");
        }
        return organizationId;
    }

    public static void clear() {
        CURRENT_ORG.remove();
    }
}
