package io.github.hide212131.langchain4j.claude.skills.runtime.langfuse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;

/** LangFuse Public API を叩く最小クライアント。 */
public final class LangfuseApiClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient;
    private final LangfuseConfiguration config;

    public LangfuseApiClient(LangfuseConfiguration config) {
        this(HttpClient.newBuilder().connectTimeout(TIMEOUT).build(), config);
    }

    LangfuseApiClient(HttpClient httpClient, LangfuseConfiguration config) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.config = Objects.requireNonNull(config, "config");
    }

    public JsonNode listTraces(int limit) {
        String query = "limit=" + limit;
        return getJson("/api/public/traces", query);
    }

    public JsonNode getTrace(String traceId) {
        Objects.requireNonNull(traceId, "traceId");
        return getJson("/api/public/traces/" + urlEncode(traceId), null);
    }

    private JsonNode getJson(String path, String query) {
        if (!config.isConfigured()) {
            throw new IllegalStateException(
                    "LangFuse の資格情報が未設定です。LANGFUSE_HOST/LANGFUSE_PUBLIC_KEY/LANGFUSE_SECRET_KEY を設定してください。");
        }
        String url = config.baseUrl() + path;
        if (query != null && !query.isBlank()) {
            url = url + "?" + query;
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET()
                .header("Accept", "application/json").header("Authorization", basicAuth()).build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("LangFuse API 呼び出しに失敗しました: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("LangFuse API 呼び出しが中断されました: " + e.getMessage(), e);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            LangfuseApiError apiError = LangfuseApiError.tryParse(response.body());
            String message = apiError == null ? response.body() : apiError.message();
            throw new IllegalStateException(
                    "LangFuse API がエラーを返しました (status=" + response.statusCode() + "): " + message);
        }
        try {
            return MAPPER.readTree(response.body());
        } catch (IOException e) {
            throw new IllegalStateException("LangFuse API の JSON を解析できません: " + e.getMessage(), e);
        }
    }

    private String basicAuth() {
        String token = config.publicKey() + ":" + config.secretKey();
        String encoded = Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
