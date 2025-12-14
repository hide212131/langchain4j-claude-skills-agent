package io.github.hide212131.langchain4j.claude.skills.runtime;

import io.github.cdimascio.dotenv.Dotenv;
import java.util.Map;
import java.util.Objects;

/**
 * 環境変数を優先し、未設定の場合のみ .env をフォールバックして LLM 設定を解決する。
 */
public final class LlmConfigurationLoader {

    static final String ENV_LLM_PROVIDER = "LLM_PROVIDER";
    static final String ENV_OPENAI_API_KEY = "OPENAI_API_KEY";
    static final String ENV_OPENAI_BASE_URL = "OPENAI_BASE_URL";
    static final String ENV_OPENAI_MODEL = "OPENAI_MODEL";

    private final Map<String, String> environment;
    private final Dotenv dotenv;

    public LlmConfigurationLoader() {
        this(System.getenv(), Dotenv.configure().ignoreIfMalformed().ignoreIfMissing().load());
    }

    LlmConfigurationLoader(Map<String, String> environment, Dotenv dotenv) {
        this.environment = Map.copyOf(Objects.requireNonNull(environment, "environment"));
        this.dotenv = Objects.requireNonNull(dotenv, "dotenv");
    }

    public LlmConfiguration load() {
        return load(null);
    }

    public LlmConfiguration load(LlmProvider overrideProvider) {
        LlmProvider provider = overrideProvider != null ? overrideProvider : resolveProvider();
        String apiKey = resolveWithPriority(ENV_OPENAI_API_KEY);
        String baseUrl = resolveWithPriority(ENV_OPENAI_BASE_URL);
        String model = resolveWithPriority(ENV_OPENAI_MODEL);

        if (provider == LlmProvider.OPENAI && (apiKey == null || apiKey.isBlank())) {
            throw new IllegalStateException("LLM_PROVIDER=openai の場合、OPENAI_API_KEY が必須です");
        }

        return new LlmConfiguration(provider, trimToNull(apiKey), trimToNull(baseUrl), trimToNull(model));
    }

    private LlmProvider resolveProvider() {
        String value = resolveWithPriority(ENV_LLM_PROVIDER);
        return LlmProvider.from(value);
    }

    private String resolveWithPriority(String key) {
        if (environment.containsKey(key)) {
            return environment.get(key);
        }
        return dotenv.get(key);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
