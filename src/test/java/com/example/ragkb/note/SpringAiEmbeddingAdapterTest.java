package com.example.ragkb.note;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;

/** 不需要数据库和外网：只验证适配器里「批量按 index 归位」这层防御 */
class SpringAiEmbeddingAdapterTest {

    private SpringAiEmbeddingAdapter adapter(EmbeddingModel model) {
        return new SpringAiEmbeddingAdapter(model, "test-model");
    }

    @Test
    void batch_results_are_reordered_by_index_not_by_response_order() {
        EmbeddingModel model = mock(EmbeddingModel.class);
        // 故意打乱返回顺序，模拟上游按完成顺序返回
        when(model.embedForResponse(anyList())).thenReturn(new EmbeddingResponse(List.of(
                new Embedding(new float[]{2f}, 2),
                new Embedding(new float[]{0f}, 0),
                new Embedding(new float[]{1f}, 1))));

        List<float[]> out = adapter(model)
                .embedBatch(List.of("第一段", "第二段", "第三段"));

        assertEquals(3, out.size());
        assertArrayEquals(new float[]{0f}, out.get(0));
        assertArrayEquals(new float[]{1f}, out.get(1));
        assertArrayEquals(new float[]{2f}, out.get(2));
    }

    @Test
    void mismatched_result_count_is_rejected() {
        EmbeddingModel model = mock(EmbeddingModel.class);
        when(model.embedForResponse(anyList())).thenReturn(new EmbeddingResponse(List.of(
                new Embedding(new float[]{0f}, 0))));

        assertThrows(IllegalStateException.class,
                () -> adapter(model).embedBatch(List.of("第一段", "第二段")));
    }

    @Test
    void model_name_is_exposed_for_index_filtering() {
        assertEquals("test-model", adapter(mock(EmbeddingModel.class)).model());
    }
}
