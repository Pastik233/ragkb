package com.example.ragkb.note;

import java.util.List;

/**
 * 向量化能力的接口，业务代码只依赖这里，不关心底层是 Spring AI 还是别的 SDK。
 *
 * <p>当前实现是 {@link SpringAiEmbeddingAdapter}（Spring AI 2.0 EmbeddingModel）。
 */
public interface EmbeddingPort {

    /** 单条向量化，用于把用户问题转成查询向量 */
    float[] embed(String text);

    /** 批量向量化，返回顺序必须与输入 texts 一一对应 */
    List<float[]> embedBatch(List<String> texts);

    /**
     * 当前模型名。写进 note_chunks.embedding_model，检索时按它过滤，
     * 防止换模型后新旧向量混检；换模型必须同步重建索引。
     */
    String model();
}
