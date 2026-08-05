CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked     BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);

CREATE TABLE documents (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    filename     VARCHAR(500) NOT NULL,
    source_type  VARCHAR(30)  NOT NULL DEFAULT 'OTHER',
    storage_key  VARCHAR(1000) NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    error_message TEXT,
    uploaded_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_documents_user_id ON documents(user_id);

CREATE TABLE chunks (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id   UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    chunk_index   INT  NOT NULL,
    content       TEXT NOT NULL,
    source_type   VARCHAR(30) NOT NULL DEFAULT 'OTHER',
    embedding     vector(1536),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_chunks_document_id ON chunks(document_id);
-- Ownership filter is part of every similarity query (see VectorStore) so one user's
-- chunks are never visible to another's retrieval, even before Phase 2 workspaces exist.
CREATE INDEX idx_chunks_user_id ON chunks(user_id);
CREATE INDEX idx_chunks_embedding_hnsw ON chunks USING hnsw (embedding vector_cosine_ops);

CREATE TABLE chat_sessions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title       VARCHAR(255) NOT NULL DEFAULT 'New chat',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_chat_sessions_user_id ON chat_sessions(user_id);

CREATE TABLE chat_messages (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id       UUID NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    role             VARCHAR(20) NOT NULL,
    content          TEXT NOT NULL,
    cited_chunk_ids  JSONB NOT NULL DEFAULT '[]',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_chat_messages_session_id ON chat_messages(session_id);
