-- ============================================================
-- Runs once, automatically, on first database creation.
--
-- Creates the pgvector extension and the chat tables written by the Python
-- agent. Hibernate (ddl-auto=update) also manages `conversations` for the
-- Java side; both definitions must stay compatible.
--
-- NOTE: this bootstrap exists because the project has no migration tool yet.
-- Replacing it with Flyway, owned by exactly one service, is P1 in AUDIT.md.
-- ============================================================

CREATE EXTENSION IF NOT EXISTS vector;

-- Written by the Python agent (UPSERT by session_id), read by Spring Boot.
CREATE TABLE IF NOT EXISTS conversations (
    id               BIGSERIAL PRIMARY KEY,
    session_id       VARCHAR(255) NOT NULL UNIQUE,
    langue           VARCHAR(10),
    adresse_ip       VARCHAR(64),
    localisation     VARCHAR(255),
    date_debut       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    date_fin         TIMESTAMP,
    nombre_messages  INTEGER DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_conversations_session_id ON conversations(session_id);

CREATE TABLE IF NOT EXISTS messages (
    id                   BIGSERIAL PRIMARY KEY,
    conversation_id      BIGINT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    question             TEXT,
    question_reformulee  TEXT,
    intention            VARCHAR(50),
    reponse              TEXT,
    sources_utilisees    TEXT[],
    rag_utilise          BOOLEAN DEFAULT FALSE,
    date_creation        TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_messages_conv_id        ON messages(conversation_id);
CREATE INDEX IF NOT EXISTS idx_messages_date_creation  ON messages(date_creation);
CREATE INDEX IF NOT EXISTS idx_messages_sources        ON messages USING gin(sources_utilisees);
