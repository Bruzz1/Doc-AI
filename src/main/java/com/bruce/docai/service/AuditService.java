package com.bruce.docai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Lightweight tenant-scoped audit trail. Records who did what, in which org,
 * for sensitive actions (document upload/delete, invite create/consume).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final JdbcClient jdbcClient;

    public void record(String organizationId, String actor, String action, String target, String detail) {
        try {
            jdbcClient.sql("""
                    INSERT INTO audit_log (organization_id, actor, action, target, detail)
                    VALUES (:organizationId, :actor, :action, :target, :detail)
                    """)
                    .param("organizationId", organizationId)
                    .param("actor", actor)
                    .param("action", action)
                    .param("target", target)
                    .param("detail", detail)
                    .update();
        } catch (RuntimeException ex) {
            // Auditing must never break the primary operation.
            log.warn("Failed to write audit entry action={} org={} actor={}", action, organizationId, actor, ex);
        }
    }
}
