package com.example.ragkb.note;

import java.time.LocalDateTime;

/**
 * 笔记实体。
 *
 * <p>不可变 record，没有无参构造也没有 setter，所以不走 MyBatis 的自动映射：
 * 列与字段的对应关系统一写在 NoteMapper.xml 的 resultMap 里，用 constructor 按列序装配。
 */
public record Note(
        Long id,
        String title,
        String content,
        LocalDateTime createdAt,
        String sourceType,
        String originalFilename,
        String mimeType,
        String indexStatus,
        LocalDateTime updatedAt) {

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
