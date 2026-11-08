package com.example.ragkb.document;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 切片器的纯逻辑测试：不需要数据库，也不调用任何外部接口 */
class TextChunkerTest {

    @Test
    void blank_text_produces_no_chunks() {
        TextChunker chunker = new TextChunker(800, 150);

        assertTrue(chunker.split(null).isEmpty());
        assertTrue(chunker.split("   \n\n  ").isEmpty());
    }

    @Test
    void short_text_is_one_trimmed_chunk() {
        TextChunker chunker = new TextChunker(800, 150);

        assertEquals(List.of("只有一句话。"), chunker.split("  只有一句话。  "));
    }

    @Test
    void unbroken_text_never_exceeds_max_chars() {
        TextChunker chunker = new TextChunker(200, 50);

        List<String> chunks = chunker.split("x".repeat(2000));

        assertFalse(chunks.isEmpty());
        assertTrue(chunks.size() >= 10, "2000 字 / 200 上限 至少 10 段，实际 " + chunks.size());
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= 200, "片段超长：" + chunk.length());
        }
    }

    @Test
    void chinese_sentences_are_split_on_punctuation() {
        TextChunker chunker = new TextChunker(60, 10);

        List<String> chunks = chunker.split("第一句话在这里。".repeat(20));

        assertTrue(chunks.size() > 1);
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= 60, "片段超长：" + chunk.length());
        }
    }

    @Test
    void consecutive_chunks_overlap() {
        TextChunker chunker = new TextChunker(100, 30, List.of("\n\n"));

        List<String> chunks = chunker.split("x".repeat(500));

        assertTrue(chunks.size() > 2);
        // 后一段的开头来自前一段的结尾，这就是 overlap 的实际效果
        assertTrue(chunks.get(1).startsWith(chunks.get(0).substring(chunks.get(0).length() - 30)));
    }

    @Test
    void rejects_invalid_configuration() {
        assertThrows(IllegalArgumentException.class, () -> new TextChunker(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new TextChunker(100, 100));
    }
}