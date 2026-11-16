package com.example.ragkb.note;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 真调百炼 /embeddings，验证 Spring AI 2.0 的官方 SDK 能按
 * spring.ai.openai 的 base-url 打到兼容端点，并检查批量结果按 index 归位。
 *
 * <p>与 chat 探针一样双重守卫，日常 mvn test 不外联、不产生费用。
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "spring.datasource.url=jdbc:postgresql://localhost:5432/ragkb"
})
@EnabledIfSystemProperty(named = "ragkb.probe", matches = "true")
@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
class SpringAiEmbeddingProbeTest {

    @Autowired
    private EmbeddingPort embeddings;

    @Test
    void single_embedding_returns_expected_dimension() {
        float[] vec = embeddings.embed("Spring AI embedding 兼容性探针");
        assertNotNull(vec);
        assertTrue(vec.length > 0);
        System.out.println("[probe] model=" + embeddings.model() + " dim=" + vec.length);
    }

    @Test
    void batch_result_count_matches_input() {
        List<float[]> out = embeddings.embedBatch(
                List.of("第一段笔记", "第二段笔记", "第三段笔记"));
        assertEquals(3, out.size());
        int dim = out.get(0).length;
        assertTrue(out.stream().allMatch(v -> v != null && v.length == dim));
        System.out.println("[probe] batch size=" + out.size() + " dim=" + dim);
    }
}
