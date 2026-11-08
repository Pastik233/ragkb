package com.example.ragkb.note;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/notes")
public class NoteController {

    /** 与 V2 迁移里的 VARCHAR(500) 保持一致 */
    private static final int MAX_TITLE_LENGTH = 500;

    /** 列表里的正文预览长度：整篇正文可能有上百万字，不能塞进列表接口 */
    private static final int PREVIEW_CHARS = 200;

    private final NoteBook noteBook;
    private final RagService rag;

    public NoteController(NoteBook noteBook, RagService rag) {
        this.noteBook = noteBook;
        this.rag = rag;
    }

    /** 列表只返回摘要，正文按需通过 GET /api/notes/{id} 拉 */
    @GetMapping
    public List<NoteSummary> list() {
        return noteBook.list().stream().map(NoteController::toSummary).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Note> get(@PathVariable long id) {
        return ResponseEntity.of(noteBook.get(id));   // 有值 200，空 404
    }

    @GetMapping("/search")
    public List<NoteSummary> search(@RequestParam String keyword) {
        return noteBook.search(keyword).stream().map(NoteController::toSummary).toList();
    }

    /** 混合检索：向量 + BM25 融合后重排，只回片段，不调对话模型 */
    @GetMapping("/semantic")
    public List<RagService.Source> semantic(@RequestParam String q,
                                            @RequestParam(defaultValue = "5") int k) {
        return rag.search(q, k);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)   // 让这个接口成功时返回 201 而不是默认 200
    public NoteSummary create(@RequestBody NoteRequest req) {
        // 先挡掉脏输入，否则会直接撞到数据库的 NOT NULL / 长度约束，变成 500
        String title = req.title() == null ? "" : req.title().trim();
        if (title.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title 不能为空");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "title 最多 " + MAX_TITLE_LENGTH + " 个字符");
        }
        return toSummary(noteBook.add(title, req.content()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        // remove 返回 true 表示确实删掉了一条，false 说明这个 id 本来就不存在
        return noteBook.remove(id)
                ? ResponseEntity.noContent().build()    // 204 删除成功，没有响应体
                : ResponseEntity.notFound().build();    // 404 没有这条记录
    }

    private static NoteSummary toSummary(Note note) {
        String content = note.content() == null ? "" : note.content();
        String preview = content.length() <= PREVIEW_CHARS
                ? content
                : content.substring(0, PREVIEW_CHARS) + "…";
        return new NoteSummary(
                note.id(),
                note.title(),
                preview,
                content.length(),
                note.sourceType(),
                note.indexStatus(),
                note.createdAt());
    }

    public record NoteRequest(String title, String content) {}

    public record NoteSummary(
            Long id,
            String title,
            String preview,
            int textLength,
            String sourceType,
            String indexStatus,
            LocalDateTime createdAt) {}
}