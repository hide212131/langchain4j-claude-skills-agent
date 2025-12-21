package io.github.hide212131.langchain4j.claude.skills.runtime.visibility;

import java.util.HashMap;
import java.util.Map;

/** 環境変数/CLI オプションから可視化設定を構築する。 */
public final class ObservabilityConfigurationLoader {

    @SuppressWarnings("PMD.UnnecessaryConstructor")
    public ObservabilityConfigurationLoader() {
        // default
    }

    public ObservabilityConfiguration load(ExporterType cliExporter, String endpointOverride, String headersOverride) {
        ExporterType exporter = cliExporter == null ? ExporterType.parse(envOrDefault("EXPORTER", "none"))
                : cliExporter;
        String endpoint = firstNonBlank(endpointOverride, System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT"));
        String headers = firstNonBlank(headersOverride, System.getenv("OTEL_EXPORTER_OTLP_HEADERS"));
        return new ObservabilityConfiguration(exporter, endpoint, parseHeaders(headers));
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

    private String envOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
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
}
