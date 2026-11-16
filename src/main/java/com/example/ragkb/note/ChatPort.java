package com.example.ragkb.note;

/**
 * 生成层抽象：业务代码只依赖这个接口，不关心底层接的是哪家模型、哪个 SDK。
 *
 * <p>当前实现是 {@link SpringAiChatAdapter}（Spring AI 2.0 ChatClient）。
 * 换厂商时只需新增一个实现并改配置，检索/重排/引用逻辑完全不用动。
 */
public interface ChatPort {

    String chat(String prompt);
}
