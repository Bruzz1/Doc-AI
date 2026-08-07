CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS vector_store (
	id uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
	content text,
	metadata json,
	embedding vector(768)
);

CREATE INDEX ON vector_store USING HNSW (embedding vector_cosine_ops);

CREATE TABLE IF NOT EXISTS rag_documents (
	id uuid PRIMARY KEY,
	organization_id varchar(255) NOT NULL,
	filename varchar(512) NOT NULL,
	extension varchar(20),
	content_type varchar(255),
	size bigint NOT NULL,
	checksum varchar(64) NOT NULL,
	chunk_count integer NOT NULL DEFAULT 0,
	created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_rag_documents_org_checksum
	ON rag_documents (organization_id, checksum);

CREATE INDEX IF NOT EXISTS idx_rag_documents_org
	ON rag_documents (organization_id);
