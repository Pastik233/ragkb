package com.example.ragkb.note;

import com.example.ragkb.common.HttpClients;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RerankClient {

    private final RestClient http;
    private final String model;
    private final boolean enabled;

    public RerankClient(
            @Value("${app.rerank.base-url:https://dashscope.aliyuncs.com/api/v1}") String baseUrl,
            @Value("${app.rerank.model:qwen3-rerank}") String model,
            @Value("${app.rerank.enabled:false}") boolean enabled,
            @Value("${dashscope.api-key}") String apiKey,
            @Value("${app.http.connect-timeout-seconds:5}") int connectTimeoutSeconds,
            @Value("${app.http.read-timeout-seconds:60}") int readTimeoutSeconds) {
        this.http = HttpClients.dashScope(
                baseUrl, apiKey, connectTimeoutSeconds, readTimeoutSeconds);
        this.model = model;
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    /** 返回按相关度降序排列的原始下标 */
    public List<Integer> rerank(String query, List<String> documents) {
        RerankResponse resp = http.post()
                .uri("/services/rerank/text-rerank/text-rerank")
                .body(new RerankRequest(model,
                        new Input(query, documents),
                        new Parameters(documents.size(), false)))
                .retrieve()
                .body(RerankResponse.class);

        if (resp == null || resp.output() == null || resp.output().results() == null) {
            throw new IllegalStateException("rerank 返回为空");
        }
        return resp.output().results().stream()
                .sorted(Comparator.comparingDouble(Result::relevance_score).reversed())
                .map(Result::index)
                .toList();
    }

    record RerankRequest(String model, Input input, Parameters parameters) {}

    record Input(String query, List<String> documents) {}

    record Parameters(int top_n, boolean return_documents) {}

    record RerankResponse(Output output) {
        record Output(List<Result> results) {}
    }

    record Result(int index, double relevance_score) {}
}
