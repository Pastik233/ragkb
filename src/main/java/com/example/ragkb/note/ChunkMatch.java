package com.example.ragkb.note;

import java.time.LocalDateTime;

public record ChunkMatch(
        Long chunkId,
        Long noteId,
        String title,
        int chunkIndex,
        String content,
        LocalDateTime createdAt,
        double distance) {}
