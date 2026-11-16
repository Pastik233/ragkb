package com.example.ragkb.note;

/**
 * 批量写入 note_chunks 的参数载体。
 *
 * <p>embedding 在写库前已经算好并转成 pgvector 字面量，所以这里只承载字符串，
 * 由 SQL 里的 {@code CAST(... AS vector)} 落成向量类型。
 */
public record ChunkInsert(
        Long noteId,
        int chunkIndex,
        String content,
        int charCount,
        String model,
        String embedding) {}
