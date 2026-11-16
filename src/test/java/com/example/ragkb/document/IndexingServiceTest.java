package com.example.ragkb.document;

import com.example.ragkb.note.Bm25Index;
import com.example.ragkb.note.EmbeddingPort;
import com.example.ragkb.note.Note;
import com.example.ragkb.note.NoteChunk;
import com.example.ragkb.note.NoteChunkMapper;
import com.example.ragkb.note.NoteMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IndexingServiceTest {

    private NoteMapper notes;
    private NoteChunkMapper chunks;
    private EmbeddingPort embeddings;
    private Bm25Index bm25;
    private IndexingService service;

    @BeforeEach
    void setUp() {
        notes = mock(NoteMapper.class);
        chunks = mock(NoteChunkMapper.class);
        embeddings = mock(EmbeddingPort.class);
        bm25 = mock(Bm25Index.class);
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        service = new IndexingService(notes, chunks, new TextChunker(800, 150),
                embeddings, bm25, txManager, 10);
    }

    @Test
    void blank_content_is_failed_instead_of_ready() {
        when(notes.findById(1L)).thenReturn(Optional.of(note("扫描件.pdf", "   \n  ")));

        assertThrows(IllegalStateException.class, () -> service.reindex(1L));

        verify(notes).updateIndexStatus(1L, "PROCESSING");
        verify(notes).updateIndexStatus(1L, "FAILED");
        verify(embeddings, never()).embedBatch(anyList());
        verify(chunks, never()).insertChunks(anyList());
    }

    @Test
    void valid_note_is_chunked_and_marked_ready() {
        when(notes.findById(1L)).thenReturn(Optional.of(note("标题", "一段可以索引的正文。")));
        when(embeddings.model()).thenReturn("test-model");
        when(embeddings.embedBatch(anyList())).thenReturn(List.of(new float[]{0.1f, 0.2f, 0.3f}));
        when(chunks.findByNoteIdOrderByChunkIndex(1L)).thenReturn(List.of(
                new NoteChunk(7L, 1L, 0, "一段可以索引的正文。", 10, "test-model",
                        LocalDateTime.of(2026, 1, 1, 0, 0))));

        int count = service.reindex(1L);

        assertEquals(1, count);
        verify(chunks).deleteByNoteId(1L);
        verify(chunks).insertChunks(argThat(batch ->
                batch.size() == 1
                        && batch.get(0).noteId().equals(1L)
                        && batch.get(0).chunkIndex() == 0
                        && batch.get(0).content().contains("一段可以索引的正文")
                        && batch.get(0).model().equals("test-model")
                        && batch.get(0).embedding().startsWith("[")));
        verify(notes).updateIndexStatus(1L, "READY");
        // 增量维护：只把这篇文档的新片段交给索引，而不是让全库重新分词
        verify(bm25).updateNote(eq(1L), anyList());
    }

    private static Note note(String title, String content) {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
        return new Note(1L, title, content, now, "MANUAL", null, null, "PENDING", now);
    }
}