package io.github.hide212131.langchain4j.claude.skills.runtime.visibility;

/** 設定に応じて可視化イベントの出力先を作成する。 */
public final class VisibilityPublisherFactory {

    private VisibilityPublisherFactory() {
    }

    public static VisibilityEventPublisher create(ObservabilityConfiguration configuration) {
        if (configuration != null && configuration.otlpEnabled()) {
            return new OtlpVisibilityPublisher(configuration.otlpEndpoint(), configuration.otlpHeaders());
        }
        return VisibilityEventPublisher.noop();
    }
}
