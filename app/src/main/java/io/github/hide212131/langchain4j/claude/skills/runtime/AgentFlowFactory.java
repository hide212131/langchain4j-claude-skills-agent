package io.github.hide212131.langchain4j.claude.skills.runtime;

import java.util.Objects;

/**
 * プロバイダ設定に応じて Agent フローを生成するファクトリ。
 */
public final class AgentFlowFactory {

    private final LlmConfiguration configuration;

    public AgentFlowFactory(LlmConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
    }

    public AgentFlow create() {
        return switch (configuration.provider()) {
            case MOCK -> new DummyAgentFlow();
            case OPENAI -> new OpenAiAgentFlowPlaceholder();
        };
    }

    private static final class OpenAiAgentFlowPlaceholder implements AgentFlow {

        @Override
        public AgentFlowResult run(
                SkillDocument document, String goal, VisibilityLog log, boolean basicLog, String runId) {
            throw new IllegalStateException("OpenAI プロバイダの実行経路は Phase 2 で実装予定です");
        }
    }
}
