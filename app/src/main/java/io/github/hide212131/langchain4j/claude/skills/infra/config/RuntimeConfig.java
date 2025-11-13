package io.github.hide212131.langchain4j.claude.skills.infra.config;

/**
 * Runtime configuration placeholder. Subsequent tasks will populate it with model/budget settings.
 */
import java.util.Optional;

public record RuntimeConfig(String defaultModel, String highPerformanceModel) {

    public RuntimeConfig() {
        this(resolveDefaultModel(), resolveHighPerformanceModel());
    }

    private static String resolveDefaultModel() {
        String envModel = System.getenv("OPENAI_MODEL_NAME");
        return Optional.ofNullable(envModel).map(String::trim).filter(s -> !s.isBlank()).orElse("gpt-5-mini");
    }

    private static String resolveHighPerformanceModel() {
        String highPerformance = System.getenv("OPENAI_HIGH_PERFORMANCE_MODEL_NAME");
        return Optional.ofNullable(highPerformance).map(String::trim).filter(s -> !s.isBlank()).orElse(null);
    }
}
