package com.example.ragkb.document;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TextChunker {

    /** 从粗到细：段落 > 行 > 中文句末 > 逗号 > 英文句号 > 空格 > 硬切 */
    private static final List<String> SEPARATORS = List.of(
            "\n\n", "\n", "。", "！", "？", "；", "，", ". ", " ");

    private final int maxChars;
    private final int overlapChars;
    private final int atomChars;
    private final List<String> separators;

    @Autowired
    public TextChunker(
            @Value("${app.ingestion.chunk-max-chars:800}") int maxChars,
            @Value("${app.ingestion.chunk-overlap-chars:150}") int overlapChars) {
        this(maxChars, overlapChars, SEPARATORS);
    }

    TextChunker(int maxChars, int overlapChars, List<String> separators) {
        if (maxChars <= 0) {
            throw new IllegalArgumentException("chunk-max-chars 必须大于 0");
        }
        if (overlapChars < 0 || overlapChars >= maxChars) {
            throw new IllegalArgumentException("chunk-overlap-chars 必须在 [0, chunk-max-chars) 内");
        }
        this.maxChars = maxChars;
        this.overlapChars = overlapChars;
        this.atomChars = maxChars - overlapChars;
        this.separators = List.copyOf(separators);
    }

    public List<String> split(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> atoms = new ArrayList<>();
        for (String atom : splitRecursive(text, 0)) {
            if (!atom.isBlank()) {
                atoms.add(atom);
            }
        }
        return merge(atoms);
    }

    /** 只有当前这段仍超过 atomChars 才继续用下一级分隔符 */
    private List<String> splitRecursive(String text, int level) {
        if (text.length() <= atomChars) {
            return List.of(text);
        }
        if (level >= separators.size()) {
            return hardSlice(text);
        }
        String separator = separators.get(level);
        if (separator.isEmpty()) {
            return hardSlice(text);
        }
        List<String> parts = splitKeepingSeparator(text, separator);
        if (parts.size() <= 1) {
            return splitRecursive(text, level + 1);
        }
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            if (part.length() <= atomChars) {
                result.add(part);
            } else {
                result.addAll(splitRecursive(part, level + 1));
            }
        }
        return result;
    }

    /** 分隔符留在片段末尾，拼回去就是原文，不需要额外补分隔符 */
    private List<String> splitKeepingSeparator(String text, String separator) {
        List<String> parts = new ArrayList<>();
        int from = 0;
        int index;
        while ((index = text.indexOf(separator, from)) >= 0) {
            int end = index + separator.length();
            parts.add(text.substring(from, end));
            from = end;
        }
        if (from < text.length()) {
            parts.add(text.substring(from));
        }
        return parts;
    }

    private List<String> hardSlice(String text) {
        List<String> parts = new ArrayList<>();
        for (int start = 0; start < text.length(); start += atomChars) {
            parts.add(text.substring(start, Math.min(start + atomChars, text.length())));
        }
        return parts;
    }

    private List<String> merge(List<String> atoms) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String atom : atoms) {
            if (current.isEmpty()) {
                current.append(atom);
            } else if (current.length() + atom.length() <= maxChars) {
                current.append(atom);
            } else {
                String raw = current.toString();
                String chunk = raw.strip();
                if (!chunk.isEmpty()) {
                    chunks.add(chunk);
                }
                String tail = tail(raw);
                current.setLength(0);
                if (tail.length() + atom.length() <= maxChars) {
                    current.append(tail);
                }
                current.append(atom);
            }
        }
        String last = current.toString().strip();
        if (!last.isEmpty()) {
            chunks.add(last);
        }
        return List.copyOf(chunks);
    }

    /** 从尚未 strip 的原始缓冲里取尾部，否则结尾的换行会被吃掉，粘出病句 */
    private String tail(String rawChunk) {
        if (overlapChars == 0 || rawChunk.length() <= maxChars / 2) {
            return "";
        }
        int start = Math.max(0, rawChunk.length() - overlapChars);
        return rawChunk.substring(alignToBoundary(rawChunk, start)).stripLeading();
    }

    /** 只在重叠窗口的前半段找边界，避免对齐到文末导致重叠变空 */
    private int alignToBoundary(String text, int from) {
        int limit = Math.min(text.length() - 1, from + overlapChars / 2);
        for (int i = from; i < limit; i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '。' || c == '！' || c == '？' || c == '；') {
                return i + 1;
            }
        }
        for (int i = from; i < limit; i++) {
            if (text.charAt(i) == ' ') {
                return i + 1;
            }
        }
        return from;
    }
}
