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
            case OPENAI -> new OpenAiAgentFlow(configuration);
        };
    }
}
