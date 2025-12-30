package io.github.hide212131.langchain4j.claude.skills.runtime.execution;

import java.util.Locale;

public enum ExecutionBackend {
    DOCKER, ACADS;

    public static ExecutionBackend parse(String value) {
        if (value == null || value.isBlank()) {
            return DOCKER;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
        case "docker" -> DOCKER;
        case "acads" -> ACADS;
        default -> throw new IllegalArgumentException("不明な実行バックエンドです: " + value);
        };
    }
}
