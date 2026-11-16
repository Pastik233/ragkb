package com.example.ragkb.note;

import java.util.List;

/**
 * 精排能力的接口。
 *
 * <p>Spring AI 2.0 GA 核心没有开箱即用的 rerank 模型，社区版
 * spring-ai-alibaba 2.0.0-M1.1 仍是里程碑版（对应 Spring AI 2.0.0-M1），
 * 与本项目 2.0.0 GA 版本错配且会带进一整套自动配置。因此保留自研实现，
 * 用接口隔离，将来框架提供 GA 版时换一个实现即可，检索链不用动。
 */
public interface RerankPort {

    boolean enabled();

    /** 返回按相关度降序排列的原始下标 */
    List<Integer> rerank(String query, List<String> documents);
}
