package com.example.ragkb.note;

import com.example.ragkb.document.IndexingService;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 用 mock 替掉仓储与索引服务，纯单元测试，不需要数据库 */
class NoteBookTest {

    private NoteRepository notes;
    private IndexingService indexing;
    private SearchService search;
    private NoteBook book;

    @BeforeEach
    void setUp() {
        notes = mock(NoteRepository.class);
        indexing = mock(IndexingService.class);
        search = mock(SearchService.class);
        book = new NoteBook(notes, indexing, search);
    }

    @Test
    void add_saves_then_reindexes_and_returns_fresh_status() {
        when(notes.save(any(Note.class))).thenReturn(note(1L, "PENDING"));
        when(notes.findById(1L)).thenReturn(Optional.of(note(1L, "READY")));

        Note result = book.add("标题", "正文");

        assertEquals("READY", result.indexStatus(), "返回的是重新查出来的真实状态");
        verify(indexing).reindex(1L);
    }

    @Test
    void add_survives_indexing_failure() {
        when(notes.save(any(Note.class))).thenReturn(note(1L, "PENDING"));
        when(notes.findById(1L)).thenReturn(Optional.of(note(1L, "FAILED")));
        doThrow(new IllegalStateException("embedding 挂了")).when(indexing).reindex(1L);

        Note result = book.add("标题", "正文");

        assertEquals("FAILED", result.indexStatus(), "索引失败不该让保存失败");
    }

    @Test
    void remove_returns_false_for_missing_id() {
        when(notes.existsById(9L)).thenReturn(false);

        assertFalse(book.remove(9L));

        verify(notes, never()).deleteById(anyLong());
        verify(search, never()).removeFromIndex(anyLong());
    }

    @Test
    void remove_deletes_and_reclaims_keyword_index() {
        when(notes.existsById(1L)).thenReturn(true);

        assertTrue(book.remove(1L));

        verify(notes).deleteById(1L);
        verify(search).removeFromIndex(1L);
    }

    @Test
    void search_chunks_passes_threshold_down() {
        book.searchChunks("问题", 5, 0.75);

        verify(search).search("问题", 5, 0.75);
    }

    private static Note note(long id, String status) {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
        return new Note(id, "标题", "正文", now, "MANUAL", null, null, status, now);
    }
}