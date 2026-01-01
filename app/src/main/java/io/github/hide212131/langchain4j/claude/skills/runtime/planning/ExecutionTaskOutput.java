package io.github.hide212131.langchain4j.claude.skills.runtime.planning;

import dev.langchain4j.model.output.structured.Description;

import java.util.Locale;

/**
 * 実行タスクの出力情報。
 */
public record ExecutionTaskOutput(
        @Description("出力種別。text/stdout/file/none のいずれか。") String type,
        @Description("ファイル出力の場合のパス。該当しない場合は空文字列。") String path, @Description("出力内容の説明。空で問題ない。") String description) {

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
