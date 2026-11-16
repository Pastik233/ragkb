package com.example.ragkb.note;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 真实链路探针：验证 Spring AI 2.0 的官方 openai-java SDK 能按
 * {@code spring.ai.openai.base-url} 打进百炼的 OpenAI 兼容端点。
 *
 * <p>两个条件同时满足才执行：机器上有 Key，且显式打开 {@code -Dragkb.probe=true}。
 * 日常 {@code mvn test} 因此永远不需要外网、也不产生费用。手动执行：
 * <pre>{@code ./mvnw -Dragkb.probe=true -Dtest=SpringAiChatAdapterProbeTest test}</pre>
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "spring.datasource.url=jdbc:postgresql://localhost:5432/ragkb"
})
@EnabledIfSystemProperty(named = "ragkb.probe", matches = "true")
@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
class SpringAiChatAdapterProbeTest {

    @Autowired
    private ChatPort chat;

    @Test
    void dashscope_compatible_endpoint_works() {
        String answer = chat.chat("只回复两个字：收到。不要输出其他内容。");
        assertNotNull(answer);
        assertFalse(answer.isBlank());
        System.out.println("[probe] answer = " + answer);
    }

    /**
     * ChatClient 的字符串参数会走模板渲染，笔记里的代码/JSON 花括号可能被当成变量。
     * adapter 用 Prompt 对象传 Message，这里用真实内容验证不会被误伤。
     */
    @Test
    void curly_braces_in_note_content_are_not_treated_as_template_vars() {
        String prompt = """
                以下是从笔记库检索到的片段：

                【1】配置示例 {"model": "qwen-flash", "nested": {"k": 1}} 结束。

                用户问题：这段配置里的 model 是什么？只回答模型名。
                """;
        String answer = chat.chat(prompt);
        assertNotNull(answer);
        assertFalse(answer.isBlank());
        System.out.println("[probe] braces answer = " + answer);
    }
}
