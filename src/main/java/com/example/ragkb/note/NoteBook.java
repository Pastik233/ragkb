package com.example.ragkb.note;

import com.example.ragkb.document.IndexingService;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NoteBook {

    private static final Logger log = LoggerFactory.getLogger(NoteBook.class);

    private final NoteMapper notes;
    private final IndexingService indexing;
    private final SearchService search;

    public NoteBook(NoteMapper notes, IndexingService indexing, SearchService search) {
        this.notes = notes;
        this.indexing = indexing;
        this.search = search;
    }

    /** 人工新增的笔记也必须切片，否则片段检索永远看不到它 */
    public Note add(String title, String content) {
        Note draft = Note.of(title, content);
        Long id = notes.insert(draft);
        try {
            indexing.reindex(id);
        } catch (RuntimeException ex) {
            log.warn("笔记已保存但切片失败 id={}", id, ex);
        }
        return notes.findById(id).orElseThrow(
                () -> new IllegalStateException("笔记写入后读回失败：" + id));
    }

    public List<ChunkMatch> searchChunks(String query, int k, double maxDistance) {
        return search.search(query, k, maxDistance);
    }

    public Optional<Note> get(long id) {
        return notes.findById(id);
    }

    public List<Note> search(String keyword) {
        return notes.findByTitleContainingIgnoreCase(keyword.trim());
    }

    public boolean remove(long id) {
        if (!notes.existsById(id)) {
            return false;
        }
        notes.deleteById(id);
        search.removeFromIndex(id);   // 片段随笔记级联删除，内存索引按 noteId 精确回收
        return true;
    }

    public List<Note> list() {
        return notes.findAll();
    }
}
