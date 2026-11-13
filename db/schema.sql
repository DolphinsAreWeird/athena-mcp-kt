-- Athena handbook index: written by the Python ingest job (ingest/), read by the Kotlin server.
-- Safe to run more than once.

CREATE EXTENSION IF NOT EXISTS vector;

-- One row per programme (aice-beng, aice-ms, ece-ms, general, ...).
CREATE TABLE IF NOT EXISTS programmes (
    code     text PRIMARY KEY,
    name     text NOT NULL,
    handbook text
);

-- One row per OKB page. Every OKB page is one handbook section.
CREATE TABLE IF NOT EXISTS sections (
    id              text PRIMARY KEY,           -- path without .md, e.g. 'ece-ms/23-returning-from-a-leave-of-absence'
    programme       text NOT NULL REFERENCES programmes (code),
    path            text NOT NULL,              -- path inside the OKB, e.g. 'ece-ms/23-returning-from-a-leave-of-absence.md'
    title           text NOT NULL,
    type            text,                       -- policy, course, faculty, minutes, ...
    description     text,
    section_no      text,                       -- '23' or '8.2' when the handbook numbers it
    handbook        text NOT NULL,              -- citation label, e.g. 'M.S. in ECE Student Handbook (MS-ECE_handbook-2026.pdf)'
    source_document text,
    updated_on      date,
    applies_to      text[] NOT NULL DEFAULT '{}', -- programmes this page applies to; empty = all programmes
    tags            text[] NOT NULL DEFAULT '{}',
    body            text NOT NULL,
    content_hash    text NOT NULL,              -- sha256 of the page file; unchanged pages are not re-embedded
    ingested_at     timestamptz NOT NULL DEFAULT now()
);

-- Retrieval units: a section split into passages short enough for the embedding model (128 tokens).
CREATE TABLE IF NOT EXISTS chunks (
    id            bigserial PRIMARY KEY,
    section_id    text NOT NULL REFERENCES sections (id) ON DELETE CASCADE,
    ord           int  NOT NULL,
    section_title text NOT NULL,
    heading       text,
    text          text NOT NULL,
    embedding     vector(384),                  -- NULL when ingested with --no-embed (lexical search still works)
    tsv           tsvector GENERATED ALWAYS AS (
                      setweight(to_tsvector('english', coalesce(section_title, '')), 'A') ||
                      setweight(to_tsvector('english', coalesce(heading, '')), 'B') ||
                      setweight(to_tsvector('english', text), 'D')
                  ) STORED,
    UNIQUE (section_id, ord)
);

CREATE INDEX IF NOT EXISTS chunks_tsv_idx       ON chunks USING gin (tsv);
CREATE INDEX IF NOT EXISTS chunks_embedding_idx ON chunks USING hnsw (embedding vector_cosine_ops);
CREATE INDEX IF NOT EXISTS sections_programme_idx ON sections (programme);

-- The OKB change log: one row per page touched by an approved commit (git log of the OKB).
CREATE TABLE IF NOT EXISTS changes (
    commit       text        NOT NULL,
    committed_at timestamptz NOT NULL,
    author       text        NOT NULL,
    action       text        NOT NULL,          -- Approve, Undo, Add new page, Delete page, Edit, ...
    title        text,
    path         text        NOT NULL,
    section_id   text,                          -- no FK: deleted pages keep their history
    programme    text,
    status       char(1)     NOT NULL,          -- A added, M modified, D deleted
    PRIMARY KEY (commit, path)
);

CREATE INDEX IF NOT EXISTS changes_committed_at_idx ON changes (committed_at DESC);

-- What the last ingest run loaded, shown by GET /health.
CREATE TABLE IF NOT EXISTS ingest_runs (
    id          bigserial PRIMARY KEY,
    okb_root    text        NOT NULL,
    okb_commit  text,
    model       text,
    sections    int         NOT NULL,
    chunks      int         NOT NULL,
    embedded    int         NOT NULL,
    finished_at timestamptz NOT NULL DEFAULT now()
);
