package io.github.hide212131.langchain4j.claude.skills.infra.config;

/**
 * Runtime configuration placeholder. Subsequent tasks will populate it with model/budget settings.
 */
public record RuntimeConfig(String defaultModel) {

    public RuntimeConfig() {
        this("gpt-5-mini");
    }
}
