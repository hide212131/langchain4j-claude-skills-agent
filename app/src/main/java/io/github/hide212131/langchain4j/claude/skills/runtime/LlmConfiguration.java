package io.github.hide212131.langchain4j.claude.skills.runtime;

import java.util.Objects;

/** LLM プロバイダ切替に必要な設定値。 */
public record LlmConfiguration(LlmProvider provider, String openAiApiKey, String openAiBaseUrl, String openAiModel) {

    private static final int MASK_THRESHOLD = 8;
    private static final int MASK_SUFFIX_LENGTH = 4;

    public LlmConfiguration {
        Objects.requireNonNull(provider, "provider");
    }

    public String maskedApiKey() {
        if (openAiApiKey == null || openAiApiKey.isBlank()) {
            return "(none)";
        }
        if (openAiApiKey.length() <= MASK_THRESHOLD) {
            return "****";
        }
        String last = openAiApiKey.substring(openAiApiKey.length() - MASK_SUFFIX_LENGTH);
        return "****" + last;
    }
}
