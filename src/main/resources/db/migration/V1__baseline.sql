-- Clean baseline schema for Doc-AI (no legacy data; FKs enforced from the start).

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Tenant registry: turns organization_id into a real, lifecycle-managed entity.
CREATE TABLE organizations (
    id varchar(255) PRIMARY KEY,
    name varchar(255) NOT NULL,
    slug varchar(255) NOT NULL,
    status varchar(32) NOT NULL DEFAULT 'ACTIVE',
    max_documents integer,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uq_organizations_slug ON organizations (slug);

-- Seed tenant used by local development and the optional startup seed loader.
INSERT INTO organizations (id, name, slug)
VALUES ('default-org', 'Default Organization', 'default-org');

-- Spring AI pgvector store.
CREATE TABLE vector_store (
    id uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    content text,
    metadata json,
    embedding vector(768)
);

CREATE INDEX idx_vector_store_embedding
    ON vector_store USING HNSW (embedding vector_cosine_ops);

-- Per-tenant metadata lookups used by KnowledgeService and the vector pre-filter.
CREATE INDEX idx_vector_store_org
    ON vector_store ((metadata ->> 'organizationId'));

CREATE INDEX idx_vector_store_document
    ON vector_store ((metadata ->> 'documentId'));

-- Tenant-owned document metadata.
CREATE TABLE rag_documents (
    id uuid PRIMARY KEY,
    organization_id varchar(255) NOT NULL REFERENCES organizations (id),
    filename varchar(512) NOT NULL,
    extension varchar(20),
    content_type varchar(255),
    size bigint NOT NULL,
    checksum varchar(64) NOT NULL,
    chunk_count integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uq_rag_documents_org_checksum
    ON rag_documents (organization_id, checksum);

CREATE INDEX idx_rag_documents_org
    ON rag_documents (organization_id);

-- Application users.
CREATE TABLE users (
    id varchar(255) PRIMARY KEY,
    email varchar(255) UNIQUE,
    password varchar(255),
    name varchar(255),
    organization_id varchar(255) REFERENCES organizations (id),
    role varchar(255),
    must_change_password boolean DEFAULT false,
    password_changed_at timestamptz
);

-- Invite tokens.
CREATE TABLE invite_tokens (
    id varchar(255) PRIMARY KEY,
    email varchar(255) NOT NULL,
    token_hash varchar(128) NOT NULL UNIQUE,
    organization_id varchar(255) NOT NULL,
    role varchar(255) NOT NULL,
    expires_at timestamptz NOT NULL,
    used_at timestamptz,
    created_at timestamptz NOT NULL,
    created_by varchar(255) NOT NULL
);

-- Refresh tokens.
CREATE TABLE refresh_tokens (
    id varchar(255) PRIMARY KEY,
    user_id varchar(255) NOT NULL REFERENCES users (id),
    token_hash varchar(128) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    replaced_by_hash varchar(128),
    created_at timestamptz NOT NULL
);

-- Lightweight tenant-scoped audit trail for sensitive actions.
CREATE TABLE audit_log (
    id uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id varchar(255),
    actor varchar(255),
    action varchar(64) NOT NULL,
    target varchar(512),
    detail text,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_log_org_created
    ON audit_log (organization_id, created_at DESC);
