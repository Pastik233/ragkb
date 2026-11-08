ALTER TABLE notes ADD COLUMN embedding vector(1024);

CREATE INDEX idx_notes_embedding ON notes USING hnsw (embedding vector_cosine_ops);
