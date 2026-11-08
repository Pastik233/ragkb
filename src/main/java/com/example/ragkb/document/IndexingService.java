package com.example.ragkb.document;

import com.example.ragkb.note.Bm25Index;
import com.example.ragkb.note.EmbeddingClient;
import com.example.ragkb.note.Note;
import com.example.ragkb.note.NoteChunk;
import com.example.ragkb.note.NoteChunkRepository;
import com.example.ragkb.note.NoteRepository;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class IndexingService {

    private static final Logger log = LoggerFactory.getLogger(IndexingService.class);

    private final NoteRepository notes;
    private final NoteChunkRepository chunks;
    private final TextChunker chunker;
    private final EmbeddingClient embeddings;
    private final Bm25Index bm25;
    private final TransactionTemplate tx;
    private final int batchSize;

    public IndexingService(NoteRepository notes,
                           NoteChunkRepository chunks,
                           TextChunker chunker,
                           EmbeddingClient embeddings,
                           Bm25Index bm25,
                           PlatformTransactionManager txManager,
                           @Value("${app.ingestion.embedding-batch-size:10}") int batchSize) {
        this.notes = notes;
        this.chunks = chunks;
        this.chunker = chunker;
        this.embeddings = embeddings;
        this.bm25 = bm25;
        this.tx = new TransactionTemplate(txManager);
        this.batchSize = batchSize;
    }

    public int reindex(Long noteId) {
        Note note = notes.findById(noteId)
                .orElseThrow(() -> new IllegalArgumentException("笔记不存在：" + noteId));
        notes.updateIndexStatus(noteId, "PROCESSING");
        try {
            String body = note.content() == null ? "" : note.content();
            // 扫描版 PDF、空文件解析出来就是空白，这种不该悄悄标成 READY
            if (body.isBlank()) {
                throw new IllegalStateException("文档没有可索引的正文（可能是扫描版 PDF 或无文本层）");
            }
            String source = note.title() + "\n\n" + body;
            List<String> pieces = chunker.split(source);
            if (pieces.isEmpty()) {
                throw new IllegalStateException("切片结果为空，无法生成索引");
            }

            List<String> vectors = new ArrayList<>(pieces.size());
            for (int from = 0; from < pieces.size(); from += batchSize) {
                List<String> batch = pieces.subList(
                        from, Math.min(from + batchSize, pieces.size()));
                for (float[] vec : embeddings.embedBatch(batch)) {
                    vectors.add(EmbeddingClient.toPgVector(vec));
                }
            }

            // 向量已经在内存里算好，事务只做数据库写入，不跨网络调用
            List<NoteChunk> saved = new ArrayList<>(pieces.size());
            tx.executeWithoutResult(status -> {
                chunks.deleteByNoteId(noteId);
                for (int i = 0; i < pieces.size(); i++) {
                    chunks.insertChunk(noteId, i, pieces.get(i),
                            pieces.get(i).length(), embeddings.model(), vectors.get(i));
                }
                notes.updateIndexStatus(noteId, "READY");
                // 回读一次拿到自增 id：BM25 内存索引要靠 chunkId 定位片段
                saved.clear();
                saved.addAll(chunks.findByNoteIdOrderByChunkIndex(noteId));
            });

            // 必须放在事务外：事务里失败会回滚，索引不该跟着回滚
            // 只增量替换这篇文档的片段，不再让全库重新分词
            bm25.updateNote(noteId, saved);
            return pieces.size();
        } catch (RuntimeException ex) {
            notes.updateIndexStatus(noteId, "FAILED");
            log.warn("重建索引失败 noteId={}", noteId, ex);
            throw ex;
        }
    }

    public BackfillResult reindexPending() {
        List<Long> ids = notes.findIdsNotReady();
        int succeeded = 0;
        List<Long> failed = new ArrayList<>();
        for (Long id : ids) {
            try {
                reindex(id);
                succeeded++;
            } catch (RuntimeException ex) {
                failed.add(id);
            }
        }
        return new BackfillResult(ids.size(), succeeded, List.copyOf(failed));
    }

    public record BackfillResult(int total, int succeeded, List<Long> failed) {}
}
