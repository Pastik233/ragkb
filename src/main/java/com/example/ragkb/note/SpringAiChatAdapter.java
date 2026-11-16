package com.example.ragkb.note;

import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

/**
 * 生成层的 Spring AI 2.0 实现。
 *
 * <p>ChatClient.Builder 由 starter 自动配置（prototype 作用域），底层是官方 openai-java SDK，
 * 通过 {@code spring.ai.openai.base-url} 指向百炼的 OpenAI 兼容端点。
 *
 * <p>这里刻意用 {@code prompt(Prompt)} 传 Message 对象，而不是 {@code .user(String)}：
 * ChatClient 会对字符串参数做模板渲染，笔记正文里的代码/JSON 花括号 {@code {xxx}}
 * 会被当成模板变量，在真正提问时才抛异常。传对象可以绕开模板引擎。
 */
@Component
public class SpringAiChatAdapter implements ChatPort {

    private static final String SYSTEM_PROMPT =
            "你是笔记助手，只依据用户提供的笔记回答；笔记里没有就说不知道，不要自由发挥。";

    private final ChatClient chatClient;

    public SpringAiChatAdapter(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public String chat(String prompt) {
        String answer = chatClient.prompt(new Prompt(List.of(
                        new SystemMessage(SYSTEM_PROMPT),
                        new UserMessage(prompt))))
                .call()
                .content();
        if (answer == null || answer.isBlank()) {
            throw new IllegalStateException("对话接口返回为空");
        }
        return answer;
    }
}
