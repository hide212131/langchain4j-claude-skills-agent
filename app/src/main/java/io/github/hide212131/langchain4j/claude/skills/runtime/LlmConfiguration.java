package io.github.hide212131.langchain4j.claude.skills.runtime;

import java.util.Objects;

/**
 * LLM プロバイダ切替に必要な設定値。
 */
public record LlmConfiguration(LlmProvider provider, String openAiApiKey, String openAiBaseUrl, String openAiModel) {

    public LlmConfiguration {
        Objects.requireNonNull(provider, "provider");
    }

    public String maskedApiKey() {
        if (openAiApiKey == null || openAiApiKey.isBlank()) {
            return "(none)";
        }
        if (openAiApiKey.length() <= 8) {
            return "****";
        }
        String last = openAiApiKey.substring(openAiApiKey.length() - 4);
        return "****" + last;
    }
}
