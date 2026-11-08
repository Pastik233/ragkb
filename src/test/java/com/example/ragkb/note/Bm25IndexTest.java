package com.example.ragkb.note;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 增量维护的倒排索引：不需要数据库，验证「只重算受影响的片段」 */
class Bm25IndexTest {

    private NoteChunkRepository chunks;
    private Bm25Index index;

    @BeforeEach
    void setUp() {
        chunks = mock(NoteChunkRepository.class);
        index = new Bm25Index(chunks);
    }

    @Test
    void first_search_loads_all_chunks_once() {
        when(chunks.findAll()).thenReturn(List.of(chunk(1L, 1L, "线程安全的集合")));

        assertEquals(List.of(1L), index.search("线程", 5));

        // 第二次搜索走内存，不再打库
        assertEquals(List.of(1L), index.search("集合", 5));
        verify(chunks, times(1)).findAll();
    }

    @Test
    void update_note_only_recomputes_that_note() {
        when(chunks.findAll()).thenReturn(List.of(
                chunk(1L, 1L, "线程安全的集合"),
                chunk(2L, 2L, "数据库事务的隔离级别")));
        index.search("线程", 5);           // 触发一次全量加载

        index.updateNote(1L, List.of(chunk(3L, 1L, "垃圾回收的分代假设")));

        assertEquals(List.of(3L), index.search("垃圾回收", 3));
        assertEquals(List.of(2L), index.search("隔离级别", 3), "别的笔记不受影响");
        verify(chunks, times(1)).findAll();                  // 没有再全量重建
    }

    @Test
    void remove_note_drops_its_chunks() {
        when(chunks.findAll()).thenReturn(List.of(
                chunk(1L, 1L, "线程安全的集合"),
                chunk(2L, 2L, "数据库事务的隔离级别")));
        index.search("线程", 5);

        index.removeNote(2L);

        assertTrue(index.search("隔离级别", 4).isEmpty());
        assertEquals(List.of(1L), index.search("线程", 4));
    }

    @Test
    void invalidate_forces_a_reload() {
        when(chunks.findAll()).thenReturn(List.of(chunk(1L, 1L, "线程安全的集合")));

        index.search("线程", 5);
        index.invalidate();
        index.search("线程", 5);

        verify(chunks, times(2)).findAll();
    }

    @Test
    void update_before_first_load_is_skipped() {
        // 没加载过时增量无从下手，直接交给第一次全量加载，避免重复挂片段
        when(chunks.findAll()).thenReturn(List.of(chunk(1L, 1L, "线程安全的集合")));

        index.updateNote(1L, List.of(chunk(2L, 1L, "垃圾回收")));

        assertEquals(List.of(1L), index.search("线程", 5));
        assertTrue(index.search("垃圾回收", 5).isEmpty());
    }

    private static NoteChunk chunk(long id, long noteId, String content) {
        return new NoteChunk(id, noteId, 0, content, content.length(), "test-model",
                LocalDateTime.of(2026, 1, 1, 0, 0));
    }
}