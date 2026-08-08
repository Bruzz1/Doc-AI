-- Optional database-level tenant isolation (defense-in-depth).
--
-- Row-Level Security is the last line of defense: even if application code forgets
-- a tenant filter, Postgres physically refuses to return other tenants' rows.
--
-- IMPORTANT operational notes:
--   * A table owner / superuser BYPASSES RLS unless FORCE ROW LEVEL SECURITY is set.
--     We FORCE it so the policy applies even when the app connects as the table owner.
--   * The application must set the current tenant per transaction via:
--         SET LOCAL app.current_org = '<organizationId>';
--     TenantContext + TenantRlsAspect do this automatically for authenticated requests.
--   * When app.current_org is unset (e.g. startup migrations, seeding, admin jobs),
--     the policies fall through to allow-all so those paths keep working.

ALTER TABLE rag_documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE rag_documents FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_rag_documents ON rag_documents
    USING (
        current_setting('app.current_org', true) IS NULL
        OR current_setting('app.current_org', true) = ''
        OR organization_id = current_setting('app.current_org', true)
    )
    WITH CHECK (
        current_setting('app.current_org', true) IS NULL
        OR current_setting('app.current_org', true) = ''
        OR organization_id = current_setting('app.current_org', true)
    );

ALTER TABLE vector_store ENABLE ROW LEVEL SECURITY;
ALTER TABLE vector_store FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_vector_store ON vector_store
    USING (
        current_setting('app.current_org', true) IS NULL
        OR current_setting('app.current_org', true) = ''
        OR (metadata ->> 'organizationId') = current_setting('app.current_org', true)
    )
    WITH CHECK (
        current_setting('app.current_org', true) IS NULL
        OR current_setting('app.current_org', true) = ''
        OR (metadata ->> 'organizationId') = current_setting('app.current_org', true)
    );
