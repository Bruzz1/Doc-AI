-- Per-tenant configurable agent behavior.
--
-- Every channel (admin chat UI, widget, WhatsApp, ...) reads the SAME agent_config
-- row for its organization, so a change made once in the admin UI applies everywhere.
--
-- mode = SIMPLE_RAG reproduces today's behavior (single RAG retrieval, stateless).
-- mode = AGENTIC enables tool-calling and conversation memory.
CREATE TABLE agent_config (
    id uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id varchar(255) NOT NULL REFERENCES organizations (id),
    name varchar(255) NOT NULL DEFAULT 'Default Agent',
    mode varchar(20) NOT NULL DEFAULT 'SIMPLE_RAG',
    system_prompt text,
    model varchar(255),
    temperature double precision,
    enabled_tools text,
    top_k integer,
    similarity_threshold double precision,
    max_context_chars integer,
    enabled boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- One active agent configuration per tenant (kept simple; per-channel overrides
-- can be layered on later without breaking this contract).
CREATE UNIQUE INDEX uq_agent_config_org ON agent_config (organization_id);

-- Seed a default SIMPLE_RAG agent for every existing tenant so runtime chat has a
-- configuration to load immediately after this migration.
INSERT INTO agent_config (organization_id, name, mode)
SELECT id, 'Default Agent', 'SIMPLE_RAG' FROM organizations
ON CONFLICT (organization_id) DO NOTHING;

-- Tenant isolation (defense-in-depth), mirroring V2 policies. Falls through to
-- allow-all when app.current_org is unset (migrations, seeding, admin jobs).
ALTER TABLE agent_config ENABLE ROW LEVEL SECURITY;
ALTER TABLE agent_config FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_agent_config ON agent_config
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
