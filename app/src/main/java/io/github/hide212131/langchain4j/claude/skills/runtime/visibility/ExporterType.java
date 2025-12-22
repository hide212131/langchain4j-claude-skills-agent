package io.github.hide212131.langchain4j.claude.skills.runtime.visibility;

/** 可視化イベントの送信先。 */
public enum ExporterType {
    NONE, OTLP;

    public static ExporterType parse(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
        case "otlp" -> OTLP;
        case "none" -> NONE;
        default -> throw new IllegalArgumentException("exporter は none|otlp を指定してください: " + value);
        };
    }
}
