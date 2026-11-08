package com.example.ragkb.note;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("notes")
public record Note(
        @Id Long id,
        String title,
        String content,
        @Column("created_at") LocalDateTime createdAt,
        @Column("source_type") String sourceType,
        @Column("original_filename") String originalFilename,
        @Column("mime_type") String mimeType,
        @Column("index_status") String indexStatus,
        @Column("updated_at") LocalDateTime updatedAt) {

    public static Note of(String title, String content) {
        LocalDateTime now = LocalDateTime.now();
        return new Note(
                null, title, content, now,
                "MANUAL", null, null, "PENDING", now);
    }

    public static Note imported(
            String title, String content,
            String originalFilename, String mimeType) {
        LocalDateTime now = LocalDateTime.now();
        return new Note(
                null, title, content, now,
                "FILE", originalFilename, mimeType, "PENDING", now);
    }
}
