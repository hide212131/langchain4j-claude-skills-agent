package io.github.hide212131.langchain4j.claude.skills.runtime.visibility;

import io.github.cdimascio.dotenv.Dotenv;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Base64;
import java.util.Objects;

/** 環境変数/CLI オプションから可視化設定を構築する。 */
public final class ObservabilityConfigurationLoader {

    private final Map<String, String> environment;
    private final Dotenv dotenv;

    @SuppressWarnings("PMD.UnnecessaryConstructor")
    public ObservabilityConfigurationLoader() {
        this(System.getenv(), loadDotenvWithFallback());
    }

    ObservabilityConfigurationLoader(Map<String, String> environment, Dotenv dotenv) {
        this.environment = Map.copyOf(Objects.requireNonNull(environment, "environment"));
        this.dotenv = Objects.requireNonNull(dotenv, "dotenv");
    }

    public ObservabilityConfiguration load(ExporterType cliExporter, String endpointOverride, String headersOverride) {
        ExporterType exporter = cliExporter == null ? ExporterType.parse(resolveWithPriority("EXPORTER", "none"))
                : cliExporter;
        String endpoint = firstNonBlank(endpointOverride, resolveWithPriority("OTEL_EXPORTER_OTLP_ENDPOINT", null));
        String headers = firstNonBlank(headersOverride, resolveWithPriority("OTEL_EXPORTER_OTLP_HEADERS", null));
        Map<String, String> parsedHeaders = parseHeaders(headers);
        Map<String, String> effectiveHeaders = enrichHeadersForLangfuse(endpoint, parsedHeaders);
        return new ObservabilityConfiguration(exporter, endpoint, effectiveHeaders);
    }

    private Map<String, String> parseHeaders(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        Map<String, String> headers = new HashMap<>();
        String[] pairs = raw.split(",");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && !kv[0].isBlank()) {
                headers.put(kv[0].trim(), kv[1].trim());
            }
        }
        return headers;
    }

    private Map<String, String> enrichHeadersForLangfuse(String endpoint, Map<String, String> headers) {
        if (endpoint == null || endpoint.isBlank()) {
            return headers;
        }
        if (headersContainKeyIgnoreCase(headers, "authorization")) {
            return headers;
        }
        if (!endpoint.contains("/api/public/otel")) {
            return headers;
        }

        String publicKey = resolveWithPriority("LANGFUSE_PUBLIC_KEY", null);
        String secretKey = resolveWithPriority("LANGFUSE_SECRET_KEY", null);
        if (publicKey == null || publicKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            return headers;
        }

        String credentials = publicKey.trim() + ":" + secretKey.trim();
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        Map<String, String> enriched = new HashMap<>(headers);
        enriched.put("Authorization", "Basic " + encoded);
        return enriched;
    }

    private boolean headersContainKeyIgnoreCase(Map<String, String> headers, String key) {
        if (headers == null || headers.isEmpty()) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        for (String headerKey : headers.keySet()) {
            if (headerKey != null && headerKey.toLowerCase(Locale.ROOT).equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    private String resolveWithPriority(String key, String defaultValue) {
        if (environment.containsKey(key)) {
            return environment.get(key);
        }
        String dotenvValue = dotenv.get(key);
        return dotenvValue == null || dotenvValue.isBlank() ? defaultValue : dotenvValue;
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        if (b != null && !b.isBlank()) {
            return b.trim();
        }
        return null;
    }

    private static Dotenv loadDotenvWithFallback() {
        Path cwd = Path.of("").toAbsolutePath();
        if (Files.exists(cwd.resolve(".env"))) {
            return Dotenv.configure().directory(cwd.toString()).ignoreIfMalformed().ignoreIfMissing().load();
        }
        Path parent = cwd.getParent();
        if (parent != null && Files.exists(parent.resolve(".env"))) {
            return Dotenv.configure().directory(parent.toString()).ignoreIfMalformed().ignoreIfMissing().load();
        }
        return Dotenv.configure().ignoreIfMalformed().ignoreIfMissing().load();
    }
}
