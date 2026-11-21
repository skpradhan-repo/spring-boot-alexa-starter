-- Knowledge schema. Owned by the Knowledge service; no other service reads these tables directly.
--
-- bge-m3 produces 1024-dimension embeddings. Changing the embedding model means a new migration
-- (new column or table) and a re-index, never an in-place edit of this file.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE documents (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name         TEXT        NOT NULL,
    checksum     CHAR(64)    NOT NULL,           -- SHA-256 of the uploaded bytes: skip re-embedding duplicates
    version      INTEGER     NOT NULL DEFAULT 1,
    access_group TEXT        NOT NULL DEFAULT 'public',  -- authorization is enforced in SQL before any LLM call
    language     TEXT,                           -- ISO 639-1: en, hi, or
    status       TEXT        NOT NULL DEFAULT 'PENDING'
                 CONSTRAINT documents_status_chk CHECK (status IN ('PENDING', 'INDEXED', 'FAILED')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT documents_name_version_uq UNIQUE (name, version),
    CONSTRAINT documents_version_chk CHECK (version >= 1)
);

CREATE INDEX documents_checksum_idx ON documents (checksum);
CREATE INDEX documents_access_group_idx ON documents (access_group);

CREATE TABLE document_chunks (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID        NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    chunk_index INTEGER     NOT NULL,
    page_number INTEGER,
    content     TEXT        NOT NULL,
    embedding   vector(1024),
    -- 'simple' on purpose: PostgreSQL ships no Hindi or Odia text-search configuration, and 'simple'
    -- works for any script while still matching exact IDs such as HR-POL-2026-01.
    tsv         TSVECTOR GENERATED ALWAYS AS (to_tsvector('simple', content)) STORED,
    CONSTRAINT chunks_doc_index_uq UNIQUE (document_id, chunk_index)
);

-- Approximate nearest-neighbour search (cosine) and keyword search for hybrid retrieval.
CREATE INDEX document_chunks_embedding_idx ON document_chunks USING hnsw (embedding vector_cosine_ops);
CREATE INDEX document_chunks_tsv_idx ON document_chunks USING gin (tsv);
