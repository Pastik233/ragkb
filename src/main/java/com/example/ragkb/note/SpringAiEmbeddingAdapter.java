package com.example.ragkb.note;

import java.util.Comparator;
import java.util.List;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 向量化的 Spring AI 2.0 实现。
 *
 * <p>EmbeddingModel 由 spring-ai-starter-model-openai 自动配置，底层是官方 openai-java SDK，
 * 走百炼的 OpenAI 兼容端点。模型名统一从 {@code app.embedding.model} 读取，
 * yaml 里再把它注入 {@code spring.ai.openai.embedding.model}，保证「实际调用的模型」
 * 与「写进 embedding_model 列用于过滤的模型名」不会出现两个数据源。
 */
@Component
public class SpringAiEmbeddingAdapter implements EmbeddingPort {

    private final EmbeddingModel embeddingModel;
    private final String modelName;

    public SpringAiEmbeddingAdapter(
            EmbeddingModel embeddingModel,
            @Value("${app.embedding.model:qwen3.7-text-embedding}") String modelName) {
        this.embeddingModel = embeddingModel;
        this.modelName = modelName;
    }

    @Override
    public float[] embed(String text) {
        return embeddingModel.embed(text);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        EmbeddingResponse resp = embeddingModel.embedForResponse(texts);
        if (resp == null || resp.getResults() == null
                || resp.getResults().size() != texts.size()) {
            throw new IllegalStateException("embedding 返回数量与输入不一致");
        }
        // 批量接口按 index 定位输入顺序，不能假设返回顺序和请求顺序一致。
        // 框架的 embed(List) 直接按 results 顺序返回，这里仍按 index 归位保留防御。
        return resp.getResults().stream()
                .sorted(Comparator.comparingInt(
                        e -> e.getIndex() == null ? 0 : e.getIndex()))
                .map(Embedding::getOutput)
                .toList();
    }

    @Override
    public String model() {
        return modelName;
    }
}
