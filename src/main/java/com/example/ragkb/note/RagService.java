package com.example.ragkb.note;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RagService {

    /** 单次最多返回多少片段：太大既撑爆 Prompt，也可能被拿来刷接口 */
    private static final int MAX_K = 20;

    private final NoteBook noteBook;
    private final EmbeddingClient ai;
    private final double maxDistance;

    public RagService(NoteBook noteBook, EmbeddingClient ai,
                      @Value("${app.rag.max-distance:0.75}") double maxDistance) {
        this.noteBook = noteBook;
        this.ai = ai;
        this.maxDistance = maxDistance;
    }

    public AskResponse ask(String question, int k) {
        List<ChunkMatch> hits = noteBook.searchChunks(question, saneK(k), maxDistance);
        if (hits.isEmpty()) {
            return new AskResponse("笔记里没有找到相关内容。", List.of());
        }

        StringBuilder context = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            ChunkMatch hit = hits.get(i);
            context.append("【").append(i + 1).append("】")
                    .append(hit.title())
                    .append("（片段 ").append(hit.chunkIndex()).append("）\n")
                    .append(hit.content())
                    .append("\n\n");
        }

        String prompt = """
                以下是从笔记库检索到的片段，按相关度从高到低排列：

                %s用户问题：%s

                要求：只依据上面的片段回答；引用时在句末标注片段编号，例如【1】；
                片段里没有答案就直接说"笔记里没有相关内容"，不要补充常识。
                """.formatted(context, question);

        return new AskResponse(ai.chat(prompt), toSources(hits));
    }

    private List<Source> toSources(List<ChunkMatch> hits) {
        return hits.stream()
                .map(h -> new Source(h.noteId(), h.title(), h.content(),
                        h.chunkIndex(), h.createdAt(), h.distance()))
                .toList();
    }

    /** 给搜索框用：只返回命中的片段，不调用对话模型 */
    public List<Source> search(String question, int k) {
        return toSources(noteBook.searchChunks(question, saneK(k), maxDistance));
    }

    /** 把调用方传的 k 夹到 [1, MAX_K]：k=-1 会让 SQL 的 LIMIT 报错，k 过大则直接吃内存 */
    private int saneK(int k) {
        return Math.clamp(k, 1, MAX_K);
    }

    public record AskResponse(String answer, List<Source> sources) {}

    public record Source(Long id, String title, String content,
                         Integer chunkIndex, LocalDateTime createdAt, double distance) {}
}
