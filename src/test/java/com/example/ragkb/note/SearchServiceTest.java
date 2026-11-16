package com.example.ragkb.note;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 检索链路：距离阈值、RRF 融合顺序、重排失败回退 */
class SearchServiceTest {

    private NoteChunkMapper chunks;
    private EmbeddingClient embeddings;
    private Bm25Index bm25;
    private RerankClient reranker;
    private SearchService service;

    @BeforeEach
    void setUp() {
        chunks = mock(NoteChunkMapper.class);
        embeddings = mock(EmbeddingClient.class);
        bm25 = mock(Bm25Index.class);
        reranker = mock(RerankClient.class);
        service = new SearchService(chunks, embeddings, bm25, reranker, 20, 20, 60, 20);
        when(embeddings.embed(anyString())).thenReturn(new float[]{0.1f});
        when(embeddings.model()).thenReturn("test-model");
    }

    @Test
    void keyword_hit_beyond_distance_threshold_is_dropped() {
        when(chunks.findNearestChunks(anyString(), anyString(), anyInt())).thenReturn(List.of());
        when(bm25.search("问题", 20)).thenReturn(List.of(7L));
        when(reranker.enabled()).thenReturn(false);
        when(chunks.findByIds(anyList(), anyString())).thenReturn(List.of(match(7L, 0.91)));

        List<ChunkMatch> hits = service.search("问题", 3, 0.75);

        assertTrue(hits.isEmpty(), "BM25 命中但距离超过阈值时不该进入结果");
    }

    @Test
    void hit_appearing_in_both_channels_ranks_first() {
        when(chunks.findNearestChunks(anyString(), anyString(), anyInt()))
                .thenReturn(List.of(match(10L, 0.20), match(11L, 0.30)));
        when(bm25.search("问题", 20)).thenReturn(List.of(12L, 11L));
        when(reranker.enabled()).thenReturn(false);
        when(chunks.findByIds(anyList(), anyString()))
                .thenReturn(List.of(match(10L, 0.20), match(11L, 0.30), match(12L, 0.40)));

        List<Long> ids = service.search("问题", 3, 0.75).stream()
                .map(ChunkMatch::chunkId)
                .toList();

        assertEquals(List.of(11L, 10L, 12L), ids, "两路都命中的片段 RRF 分数最高");
    }

    @Test
    void rerank_failure_falls_back_to_rrf_order() {
        when(chunks.findNearestChunks(anyString(), anyString(), anyInt()))
                .thenReturn(List.of(match(10L, 0.20), match(11L, 0.30)));
        when(bm25.search("问题", 20)).thenReturn(List.of());
        when(reranker.enabled()).thenReturn(true);
        when(chunks.findByIds(anyList(), anyString()))
                .thenReturn(List.of(match(10L, 0.20), match(11L, 0.30)));
        when(reranker.rerank(anyString(), anyList()))
                .thenThrow(new IllegalStateException("rerank 服务不可用"));

        List<Long> ids = service.search("问题", 2, 0.75).stream()
                .map(ChunkMatch::chunkId)
                .toList();

        assertEquals(List.of(10L, 11L), ids);
    }

    private static ChunkMatch match(long chunkId, double distance) {
        return new ChunkMatch(chunkId, 1L, "标题", 0, "正文",
                LocalDateTime.of(2026, 1, 1, 0, 0), distance);
    }
}