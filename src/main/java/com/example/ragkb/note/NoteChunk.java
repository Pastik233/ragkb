package com.example.ragkb.note;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("note_chunks")
public record NoteChunk(
        @Id Long id,
        @Column("note_id") Long noteId,
        @Column("chunk_index") int chunkIndex,
        String content,
        @Column("char_count") int charCount,
        @Column("embedding_model") String embeddingModel,
        @Column("created_at") LocalDateTime createdAt) {}
