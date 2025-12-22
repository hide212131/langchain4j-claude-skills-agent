package io.github.hide212131.langchain4j.claude.skills.runtime.visibility;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/** 可視化エクスポートの設定。 */
public record ObservabilityConfiguration(ExporterType exporter, String otlpEndpoint, Map<String, String> otlpHeaders) {

    public ObservabilityConfiguration(ExporterType exporter, String otlpEndpoint, Map<String, String> otlpHeaders) {
        this.exporter = exporter == null ? ExporterType.NONE : exporter;
        this.otlpEndpoint = otlpEndpoint;
        this.otlpHeaders = otlpHeaders == null ? Map.of() : Collections.unmodifiableMap(otlpHeaders);
    }

    public boolean otlpEnabled() {
        return exporter == ExporterType.OTLP && otlpEndpoint != null && !otlpEndpoint.isBlank();
    }

    public ObservabilityConfiguration withExporter(ExporterType override) {
        return new ObservabilityConfiguration(Objects.requireNonNullElse(override, exporter), otlpEndpoint,
                otlpHeaders);
    }
}
