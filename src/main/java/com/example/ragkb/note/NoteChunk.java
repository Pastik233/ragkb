package com.example.ragkb.note;

import java.time.LocalDateTime;

/**
 * 笔记切片。
 *
 * <p>同样是不可变 record，映射写在 NoteChunkMapper.xml 的 resultMap 里。
 * 这里刻意不含 embedding 列：向量只在检索 SQL 里参与排序，不需要读回内存。
 */
public record NoteChunk(
        Long id,
        Long noteId,
        int chunkIndex,
        String content,
        int charCount,
        String embeddingModel,
        LocalDateTime createdAt) {}
