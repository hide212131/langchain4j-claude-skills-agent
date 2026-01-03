package io.github.hide212131.langchain4j.claude.skills.runtime.visibility;

/** 設定に応じて可視化イベントの出力先を作成する。 */
public final class SkillPublisherFactory {

    private SkillPublisherFactory() {
    }

    public static SkillEventPublisher create(ObservabilityConfiguration configuration) {
        if (configuration != null && configuration.otlpEnabled()) {
            return new OtlpSkillPublisher(configuration.otlpEndpoint(), configuration.otlpHeaders());
        }
        return SkillEventPublisher.noop();
    }
}
