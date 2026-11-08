-- 整篇笔记向量已被 note_chunks 的片段向量取代，删除遗留列与其 HNSW 索引
DROP INDEX IF EXISTS idx_notes_embedding;

ALTER TABLE notes DROP COLUMN IF EXISTS embedding;