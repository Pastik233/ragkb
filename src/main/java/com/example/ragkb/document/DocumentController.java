package com.example.ragkb.document;

import com.example.ragkb.note.Note;
import com.example.ragkb.note.NoteChunkRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documents;
    private final IndexingService indexing;
    private final NoteChunkRepository chunks;

    public DocumentController(DocumentService documents,
                              IndexingService indexing,
                              NoteChunkRepository chunks) {
        this.documents = documents;
        this.indexing = indexing;
        this.chunks = chunks;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> upload(
            @RequestPart("file") MultipartFile file) {

        Note note = documents.importFile(file);
        UploadResponse body = new UploadResponse(
                note.id(),
                note.title(),
                note.mimeType(),
                note.indexStatus(),
                note.content().length());

        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/{id}/reindex")
    public ReindexResponse reindex(@PathVariable Long id) {
        try {
            int chunkCount = indexing.reindex(id);
            return new ReindexResponse(id, chunkCount, "READY");
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    @PostMapping("/reindex-pending")
    public IndexingService.BackfillResult reindexPending() {
        return indexing.reindexPending();
    }

    @GetMapping("/{id}/chunks")
    public List<ChunkView> chunks(@PathVariable Long id) {
        return chunks.findByNoteIdOrderByChunkIndex(id).stream()
                .map(c -> new ChunkView(
                        c.chunkIndex(), c.charCount(), preview(c.content())))
                .toList();
    }

    private String preview(String content) {
        return content.length() <= 120
                ? content
                : content.substring(0, 120) + "…";
    }

    public record UploadResponse(
            Long id,
            String title,
            String mimeType,
            String indexStatus,
            int textLength) {}

    public record ReindexResponse(Long id, int chunkCount, String indexStatus) {}

    public record ChunkView(int chunkIndex, int charCount, String preview) {}
}
