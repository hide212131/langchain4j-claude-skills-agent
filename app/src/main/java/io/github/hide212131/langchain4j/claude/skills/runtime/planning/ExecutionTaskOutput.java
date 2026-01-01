package io.github.hide212131.langchain4j.claude.skills.runtime.planning;

import java.util.Locale;

/**
 * 実行タスクの出力情報。
 */
public record ExecutionTaskOutput(String type, String path, String description) {

    public ExecutionTaskOutput(String type, String path, String description) {
        this.type = normalizeType(type);
        this.path = path == null ? "" : path;
        this.description = description == null ? "" : description;
    }

    private static String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return "none";
        }
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
        case "text", "stdout", "file", "none" -> normalized;
        default -> throw new IllegalArgumentException("output.type は text/stdout/file/none のいずれかにしてください: " + type);
        };
    }
}
