-- Asynchronous ingestion lifecycle for rag_documents.
-- Documents are inserted as PENDING, moved to PROCESSING by the ingest worker,
-- and finalized as INDEXED or FAILED (with an error_message) once embedding completes.
ALTER TABLE rag_documents
    ADD COLUMN status varchar(20) NOT NULL DEFAULT 'INDEXED',
    ADD COLUMN error_message text,
    ADD COLUMN updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Existing rows predate async ingestion and already have their vectors, so they
-- stay INDEXED. New uploads default to PENDING in application code.
CREATE INDEX idx_rag_documents_org_status
    ON rag_documents (organization_id, status);
