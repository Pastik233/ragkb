package com.example.ragkb.note;

import com.example.ragkb.common.LruCache;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final NoteChunkMapper chunks;
    private final EmbeddingClient embeddings;
    private final Bm25Index bm25;
    private final RerankClient reranker;
    private final int vectorCandidates;
    private final int keywordCandidates;
    private final int rrfK;
    private final int rerankCandidates;

    /** 查询向量缓存：同一个问题被反复追问、或在评测里重复跑题时不必重复调 embedding */
    private final LruCache<String, float[]> queryVectors = new LruCache<>(128);

    /** 重排结果缓存：键是「问题 + 候选片段 id」，片段一旦重切片 id 就变了，天然失效 */
    private final LruCache<String, List<Integer>> rerankOrders = new LruCache<>(64);

    public SearchService(NoteChunkMapper chunks,
                         EmbeddingClient embeddings,
                         Bm25Index bm25,
                         RerankClient reranker,
                         @Value("${app.rag.vector-candidates:20}") int vectorCandidates,
                         @Value("${app.rag.keyword-candidates:20}") int keywordCandidates,
                         @Value("${app.rag.rrf-k:60}") int rrfK,
                         @Value("${app.rag.rerank-candidates:20}") int rerankCandidates) {
        this.chunks = chunks;
        this.embeddings = embeddings;
        this.bm25 = bm25;
        this.reranker = reranker;
        this.vectorCandidates = vectorCandidates;
        this.keywordCandidates = keywordCandidates;
        this.rrfK = rrfK;
        this.rerankCandidates = rerankCandidates;
    }

    public List<ChunkMatch> search(String query, int k, double maxDistance) {
        float[] vec = queryVector(query);
        String vecParam = EmbeddingClient.toPgVector(vec);

        List<ChunkMatch> vectorHits = chunks
                .findNearestChunks(vecParam, embeddings.model(), vectorCandidates)
                .stream()
                .filter(hit -> hit.distance() <= maxDistance)
                .toList();

        List<Long> keywordIds = bm25.search(query, keywordCandidates);

        // 要重排就得多留候选，否则重排只能在 k 个里洗牌
        int candidateLimit = reranker.enabled() ? Math.max(k, rerankCandidates) : k;
        List<Long> fused = fuse(vectorHits, keywordIds, candidateLimit);
        if (fused.isEmpty()) {
            return List.of();
        }

        Map<Long, ChunkMatch> loaded = chunks.findByIds(fused, vecParam).stream()
                .collect(Collectors.toMap(ChunkMatch::chunkId, hit -> hit));

        // 关键词召回的片段也要过距离阈值，否则 BM25 命中的远距离片段会绕过 maxDistance
        List<ChunkMatch> candidates = fused.stream()
                .map(loaded::get)
                .filter(Objects::nonNull)
                .filter(hit -> hit.distance() <= maxDistance)
                .toList();

        return applyRerank(query, candidates, k);
    }

    private List<ChunkMatch> applyRerank(String query, List<ChunkMatch> candidates, int k) {
        if (!reranker.enabled() || candidates.size() <= 1) {
            return candidates.stream().limit(k).toList();
        }
        try {
            List<Integer> order = rerankOrders.get(cacheKey(query, candidates));
            if (order == null) {
                order = reranker.rerank(
                        query, candidates.stream().map(ChunkMatch::content).toList());
                rerankOrders.put(cacheKey(query, candidates), order);
            }
            return order.stream()
                    .filter(index -> index >= 0 && index < candidates.size())
                    .limit(k)
                    .map(candidates::get)
                    .toList();
        } catch (RuntimeException ex) {
            log.warn("重排失败，回退到 RRF 顺序", ex);
            return candidates.stream().limit(k).toList();
        }
    }

    private String cacheKey(String query, List<ChunkMatch> candidates) {
        return query + '|' + candidates.stream()
                .map(hit -> Long.toString(hit.chunkId()))
                .collect(Collectors.joining(","));
    }

    private float[] queryVector(String query) {
        String key = embeddings.model() + '|' + query;
        float[] cached = queryVectors.get(key);
        if (cached != null) {
            return cached;
        }
        float[] vec = embeddings.embed(query);
        queryVectors.put(key, vec);
        return vec;
    }

    private List<Long> fuse(List<ChunkMatch> vectorHits, List<Long> keywordIds, int limit) {
        Map<Long, Double> scores = new LinkedHashMap<>();
        for (int i = 0; i < vectorHits.size(); i++) {
            scores.merge(vectorHits.get(i).chunkId(), 1.0 / (rrfK + i + 1), Double::sum);
        }
        for (int i = 0; i < keywordIds.size(); i++) {
            scores.merge(keywordIds.get(i), 1.0 / (rrfK + i + 1), Double::sum);
        }
        return scores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    /** 单篇笔记的片段变了：只回收它在 BM25 里的倒排项 */
    public void removeFromIndex(long noteId) {
        rerankOrders.clear();
        bm25.removeNote(noteId);
    }
}
