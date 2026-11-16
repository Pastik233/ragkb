package com.example.ragkb.note;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 内存版 BM25 倒排索引（中文按二元组切词）。
 *
 * <p>维护方式是增量式的：一篇文档重新切片后只重算这篇文档的片段，
 * 不再为了一个文档把全库片段重新分词。索引第一次被用到时才从库里全量加载，
 * 之后只有显式 {@link #invalidate()} 才会再全量加载一次。
 */
@Component
public class Bm25Index {

    private static final Logger log = LoggerFactory.getLogger(Bm25Index.class);

    private static final double K1 = 1.2;
    private static final double B = 0.75;
    private static final int MAX_QUERY_TERMS = 16;

    private static final Pattern TOKEN = Pattern.compile("[\\p{IsHan}]+|[A-Za-z0-9_]{2,}");

    private static final Set<String> STOP_TERMS = Set.of(
            "什么", "怎么", "如何", "为何", "是否", "可以", "这个", "那个", "一下", "为什", "么是");

    private final NoteChunkMapper chunks;

    /**
     * 读多写少：搜索拿读锁并发跑，增删片段拿写锁。
     * 用读写锁而不是 synchronized，是因为一次重建会连续改很多次，读者没必要陪着排队。
     */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /** term -> (chunkId -> 词频) */
    private final Map<String, Map<Long, Integer>> postings = new HashMap<>();

    /** chunkId -> 该片段出现过的词，删除时用来精确回收倒排项 */
    private final Map<Long, Set<String>> vocabularies = new HashMap<>();

    /** chunkId -> 片段长度 */
    private final Map<Long, Integer> lengths = new HashMap<>();

    /** chunkId -> noteId，删整篇笔记时按归属批量回收 */
    private final Map<Long, Long> owners = new HashMap<>();

    private boolean loaded;

    public Bm25Index(NoteChunkMapper chunks) {
        this.chunks = chunks;
    }

    /**
     * 索引状态不可信（启动时、或批量补建后）调用：下次搜索重新从库里全量加载。
     */
    public void invalidate() {
        lock.writeLock().lock();
        try {
            clear();
            loaded = false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 一篇文档重新切片后调用：先摘掉它名下的旧片段，再挂上新的。
     *
     * @param noteId  笔记 id
     * @param current 该笔记当前的片段（应来自数据库，带自增 id）
     */
    public void updateNote(long noteId, List<NoteChunk> current) {
        lock.writeLock().lock();
        try {
            if (!loaded) {
                // 还没加载过，就不用增量了：第一次搜索会从库里全量加载，自然包含新片段
                return;
            }
            removeOwnedBy(noteId);
            for (NoteChunk chunk : current) {
                addDocument(chunk);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 删除整篇笔记的片段（片段行已被数据库级联删掉，这里只回收内存索引）。
     */
    public void removeNote(long noteId) {
        lock.writeLock().lock();
        try {
            if (!loaded) {
                return;
            }
            removeOwnedBy(noteId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<Long> search(String query, int limit) {
        loadIfNeeded();
        List<String> terms = queryTerms(query);
        if (terms.isEmpty()) {
            return List.of();
        }

        lock.readLock().lock();
        try {
            int docCount = lengths.size();
            if (docCount == 0) {
                return List.of();
            }

            long totalLength = 0;
            for (int length : lengths.values()) {
                totalLength += length;
            }
            double avgLength = (double) totalLength / docCount;

            Map<Long, Double> scores = new HashMap<>();
            for (String term : terms) {
                Map<Long, Integer> posting = postings.get(term);
                if (posting == null) {
                    continue;
                }
                // IDF：出现该词的片段越少，权重越大
                double idf = Math.log(1 + (docCount - posting.size() + 0.5)
                        / (posting.size() + 0.5));
                for (Map.Entry<Long, Integer> entry : posting.entrySet()) {
                    long chunkId = entry.getKey();
                    int tf = entry.getValue();
                    // 长度归一化：长片段要打折
                    double norm = K1 * (1 - B + B * lengths.get(chunkId) / avgLength);
                    scores.merge(chunkId,
                            idf * tf * (K1 + 1) / (tf + norm),
                            Double::sum);
                }
            }

            return scores.entrySet().stream()
                    .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                    .limit(limit)
                    .map(Map.Entry::getKey)
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    private void loadIfNeeded() {
        lock.readLock().lock();
        try {
            if (loaded) {
                return;
            }
        } finally {
            lock.readLock().unlock();
        }

        lock.writeLock().lock();
        try {
            if (loaded) {
                return;
            }
            List<NoteChunk> rows = chunks.findAll();
            clear();
            for (NoteChunk row : rows) {
                addDocument(row);
            }
            loaded = true;
            log.info("BM25 索引已全量加载，片段数={}", lengths.size());
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void addDocument(NoteChunk chunk) {
        long chunkId = chunk.id();
        String content = chunk.content() == null ? "" : chunk.content();
        lengths.put(chunkId, Math.max(1, content.length()));
        owners.put(chunkId, chunk.noteId());

        Map<String, Integer> frequencies = frequencies(content);
        Set<String> vocabulary = new LinkedHashSet<>(frequencies.keySet());
        vocabularies.put(chunkId, vocabulary);
        for (Map.Entry<String, Integer> entry : frequencies.entrySet()) {
            postings.computeIfAbsent(entry.getKey(), key -> new HashMap<>())
                    .put(chunkId, entry.getValue());
        }
    }

    private void removeOwnedBy(long noteId) {
        List<Long> doomed = new ArrayList<>();
        for (Map.Entry<Long, Long> entry : owners.entrySet()) {
            if (entry.getValue() != null && entry.getValue() == noteId) {
                doomed.add(entry.getKey());
            }
        }
        doomed.forEach(this::removeDocument);
    }

    private void removeDocument(long chunkId) {
        Set<String> vocabulary = vocabularies.remove(chunkId);
        if (vocabulary != null) {
            for (String term : vocabulary) {
                Map<Long, Integer> posting = postings.get(term);
                if (posting == null) {
                    continue;
                }
                posting.remove(chunkId);
                if (posting.isEmpty()) {
                    postings.remove(term);
                }
            }
        }
        lengths.remove(chunkId);
        owners.remove(chunkId);
    }

    private void clear() {
        postings.clear();
        vocabularies.clear();
        lengths.clear();
        owners.clear();
    }

    private Map<String, Integer> frequencies(String content) {
        Map<String, Integer> tf = new HashMap<>();
        Matcher matcher = TOKEN.matcher(content);
        while (matcher.find()) {
            Set<String> tokens = new LinkedHashSet<>();
            collect(tokens, matcher.group());
            for (String token : tokens) {
                tf.merge(token, 1, Integer::sum);
            }
        }
        return tf;
    }

    private List<String> queryTerms(String query) {
        Set<String> terms = new LinkedHashSet<>();
        Matcher matcher = TOKEN.matcher(query);
        while (matcher.find() && terms.size() < MAX_QUERY_TERMS) {
            collect(terms, matcher.group());
        }
        terms.removeAll(STOP_TERMS);
        return List.copyOf(terms);
    }

    /**
     * 中文没有空格，整句会被当成一个词，所以按字生成二元组：
     * "线程安全" → 线程 / 程安 / 安全
     */
    private void collect(Set<String> sink, String token) {
        if (isHan(token)) {
            if (token.length() == 1) {
                return;
            }
            if (token.length() <= 4) {
                sink.add(token);
            }
            for (int i = 0; i + 1 < token.length(); i++) {
                sink.add(token.substring(i, i + 2));
            }
        } else {
            sink.add(token.toLowerCase(Locale.ROOT));
        }
    }

    private boolean isHan(String token) {
        return token.codePoints().allMatch(Character::isIdeographic);
    }
}