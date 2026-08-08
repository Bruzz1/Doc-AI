-- Conversation memory for AGENTIC agents (multi-turn context).
--
-- SIMPLE_RAG stays stateless; these tables are only written when an agent runs in
-- AGENTIC mode. A conversation is keyed by (organization_id, channel, user_ref) so a
-- returning user on the same channel continues their thread without the caller having
-- to track a conversation id.
--
-- organization_id is denormalized onto messages so Row-Level Security can filter it
-- directly by column, consistent with the rest of the schema.
CREATE TABLE conversations (
    id uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id varchar(255) NOT NULL REFERENCES organizations (id),
    channel varchar(20) NOT NULL,
    user_ref varchar(255) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_activity_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- One active conversation per user per channel within a tenant (find-or-create key).
CREATE UNIQUE INDEX uq_conversations_org_channel_user
    ON conversations (organization_id, channel, user_ref);

CREATE TABLE messages (
    id uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
    conversation_id uuid NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    organization_id varchar(255) NOT NULL REFERENCES organizations (id),
    role varchar(20) NOT NULL,
    content text NOT NULL,
    tool_name varchar(255),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_messages_conversation_created
    ON messages (conversation_id, created_at);

-- Tenant isolation (defense-in-depth), mirroring V2 policies.
ALTER TABLE conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE conversations FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_conversations ON conversations
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

ALTER TABLE messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE messages FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_messages ON messages
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
