package com.example.ragkb.document;

import com.example.ragkb.document.DocumentParser.ParsedDocument;
import com.example.ragkb.note.Note;
import com.example.ragkb.note.NoteMapper;
import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.apache.tika.exception.TikaException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.xml.sax.SAXException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain",
            "text/markdown",
            "text/html",
            "text/csv"
    );

    private final DocumentParser parser;
    private final NoteMapper notes;
    private final IndexingService indexing;

    public DocumentService(DocumentParser parser, NoteMapper notes,
                           IndexingService indexing) {
        this.parser = parser;
        this.notes = notes;
        this.indexing = indexing;
    }

    public Note importFile(MultipartFile file) {
        try {
            ParsedDocument parsed = parser.parse(file);
            String mimeType = normalizeMime(parsed.mimeType());

            if (!ALLOWED_TYPES.contains(mimeType)) {
                throw new ResponseStatusException(
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "不支持的文件类型：" + mimeType);
            }
            String filename = safeFilename(file.getOriginalFilename());
            String title = titleOf(filename);
            Long noteId = notes.insert(Note.imported(
                    title, parsed.text(), filename, mimeType));

            try {
                indexing.reindex(noteId);
            } catch (RuntimeException ex) {
                // 原文已入库，索引失败不推翻上传结果；状态已由 IndexingService 标为 FAILED
                log.warn("文档已入库但切片失败 noteId={}", noteId, ex);
            }
            // 重新查一次，把 READY / FAILED 的真实状态返回给调用方
            return notes.findById(noteId).orElseThrow(
                    () -> new IllegalStateException("文档写入后读回失败：" + noteId));

        } catch (IOException | TikaException | SAXException ex) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "文档解析失败", ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    private String normalizeMime(String raw) {
        if (raw == null || raw.isBlank()) {
            return "application/octet-stream";
        }
        int semicolon = raw.indexOf(';');
        String value = semicolon >= 0
                ? raw.substring(0, semicolon)
                : raw;
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String safeFilename(String raw) {
        String value = Objects.requireNonNullElse(raw, "未命名文档");
        value = value.replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1).trim();
        if (value.isEmpty()) {
            value = "未命名文档";
        }
        return value.length() > 500
                ? value.substring(0, 500)
                : value;
    }

    private String titleOf(String filename) {
        String title = filename.replaceFirst("\\.[^.]+$", "").trim();
        if (title.isEmpty()) {
            title = "未命名文档";
        }
        return title.length() > 500
                ? title.substring(0, 500)
                : title;
    }
}
