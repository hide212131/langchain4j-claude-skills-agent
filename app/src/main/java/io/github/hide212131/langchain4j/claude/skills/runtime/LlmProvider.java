package io.github.hide212131.langchain4j.claude.skills.runtime;

/**
 * 利用する LLM プロバイダの種別。
 */
public enum LlmProvider {
    MOCK,
    OPENAI;

    public static LlmProvider from(String value) {
        if (value == null || value.isBlank()) {
            return MOCK;
        }
        String normalized = value.trim().toLowerCase();
        return switch (normalized) {
            case "openai" -> OPENAI;
            case "mock" -> MOCK;
            default -> throw new IllegalArgumentException("LLM_PROVIDER の値が不正です: " + value);
        };
    }
}
