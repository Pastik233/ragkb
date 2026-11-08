package com.example.ragkb.common;

import java.time.Duration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 统一构造对 DashScope 的 {@link RestClient}。
 *
 * <p>关键点：必须显式设置连接/读取超时。默认的 JDK 客户端没有读超时，
 * 上游卡住时请求线程会一直挂着，直到容器线程耗尽。
 */
public final class HttpClients {

    private HttpClients() {
    }

    public static RestClient dashScope(
            String baseUrl,
            String apiKey,
            int connectTimeoutSeconds,
            int readTimeoutSeconds) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }
}