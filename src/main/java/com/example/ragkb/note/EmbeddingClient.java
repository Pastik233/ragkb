package com.example.ragkb.note;

import com.example.ragkb.common.HttpClients;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.StringJoiner;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class EmbeddingClient {

    private final RestClient http;
    private final String model;
    private final String chatModel;

    public EmbeddingClient(
            @Value("${dashscope.api-key}") String apiKey,
            @Value("${app.embedding.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
            String baseUrl,
            @Value("${app.embedding.model:qwen3.7-text-embedding}") String model,
            @Value("${app.chat.model:qwen-flash}") String chatModel,
            @Value("${app.http.connect-timeout-seconds:5}") int connectTimeoutSeconds,
            @Value("${app.http.read-timeout-seconds:60}") int readTimeoutSeconds) {
        this.http = HttpClients.dashScope(
                baseUrl, apiKey, connectTimeoutSeconds, readTimeoutSeconds);
        this.model = model;
        this.chatModel = chatModel;
    }

    public String chat(String prompt) {
        ChatResponse resp = http.post()
                .uri("/chat/completions")
                .body(new ChatRequest(chatModel, List.of(
                        new Message("system", "你是笔记助手，只依据用户提供的笔记回答；笔记里没有就说不知道，不要自由发挥。"),
                        new Message("user", prompt))))
                .retrieve()
                .body(ChatResponse.class);
        if (resp == null || resp.choices() == null || resp.choices().isEmpty()
                || resp.choices().get(0).message() == null) {
            throw new IllegalStateException("对话接口返回为空");
        }
        return resp.choices().get(0).message().content();
    }

    record Message(String role, String content) {}
    record ChatRequest(String model, List<Message> messages) {}
    record ChatResponse(List<Choice> choices) {
        record Choice(Message message) {}
    }


    public float[] embed(String text) {
        EmbeddingResponse resp = http.post()
                .uri("/embeddings")
                .body(new EmbeddingRequest(model, text))
                .retrieve()
                .body(EmbeddingResponse.class);
        if (resp == null || resp.data() == null || resp.data().isEmpty()) {
            throw new IllegalStateException("embedding 返回为空");
        }
        List<Double> vec = resp.data().get(0).embedding();
        float[] out = new float[vec.size()];
        for (int i = 0; i < vec.size(); i++) {
            out[i] = vec.get(i).floatValue();
        }
        return out;
    }

    public static String toPgVector(float[] vec) {
        StringJoiner j = new StringJoiner(",", "[", "]");
        for (float v : vec) j.add(Float.toString(v));
        return j.toString();
    }

    public String model() {
        return model;
    }

    public List<float[]> embedBatch(List<String> texts) {
        EmbeddingResponse resp = http.post()
                .uri("/embeddings")
                .body(new EmbeddingBatchRequest(model, texts))
                .retrieve()
                .body(EmbeddingResponse.class);
        if (resp == null || resp.data() == null || resp.data().size() != texts.size()) {
            throw new IllegalStateException("embedding 返回数量与输入不一致");
        }
        // 批量接口是按 index 定位输入顺序的，不能假设返回顺序和请求顺序一致
        List<EmbeddingResponse.Item> items = resp.data().stream()
                .sorted(Comparator.comparingInt(EmbeddingResponse.Item::index))
                .toList();
        List<float[]> out = new ArrayList<>(texts.size());
        for (EmbeddingResponse.Item item : items) {
            List<Double> vec = item.embedding();
            float[] arr = new float[vec.size()];
            for (int i = 0; i < vec.size(); i++) {
                arr[i] = vec.get(i).floatValue();
            }
            out.add(arr);
        }
        return out;
    }

    record EmbeddingBatchRequest(String model, List<String> input) {}
    record EmbeddingRequest(String model, String input) {}
    record EmbeddingResponse(List<Item> data) {
        record Item(int index, List<Double> embedding) {}
    }
}