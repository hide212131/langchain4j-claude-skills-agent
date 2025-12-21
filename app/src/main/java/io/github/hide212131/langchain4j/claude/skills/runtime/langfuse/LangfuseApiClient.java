package io.github.hide212131.langchain4j.claude.skills.runtime.langfuse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;

/** LangFuse Public API を叩く最小クライアント。 */
public final class LangfuseApiClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LangfuseConfiguration config;

    public LangfuseApiClient(LangfuseConfiguration config) {
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
        HttpURLConnection connection;
        try {
            connection = openGet(URI.create(url).toURL());
        } catch (IOException e) {
            throw new IllegalStateException("LangFuse API 呼び出しに失敗しました: " + e.getMessage(), e);
        }
        try {
            int statusCode = connection.getResponseCode();
            String body = readBody(connection, statusCode);
            if (statusCode < 200 || statusCode >= 300) {
                LangfuseApiError apiError = LangfuseApiError.tryParse(body);
                String message = apiError == null ? body : apiError.message();
                throw new IllegalStateException("LangFuse API がエラーを返しました (status=" + statusCode + "): " + message);
            }
            try {
                return MAPPER.readTree(body);
            } catch (IOException e) {
                throw new IllegalStateException("LangFuse API の JSON を解析できません: " + e.getMessage(), e);
            }
        } catch (IOException e) {
            throw new IllegalStateException("LangFuse API 呼び出しに失敗しました: " + e.getMessage(), e);
        } finally {
            connection.disconnect();
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

    private HttpURLConnection openGet(URL url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout((int) TIMEOUT.toMillis());
        connection.setReadTimeout((int) TIMEOUT.toMillis());
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Authorization", basicAuth());
        return connection;
    }

    private String readBody(HttpURLConnection connection, int statusCode) throws IOException {
        boolean success = statusCode >= 200 && statusCode < 300;
        try (InputStream stream = success ? connection.getInputStream() : connection.getErrorStream()) {
            if (stream == null) {
                return "";
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
