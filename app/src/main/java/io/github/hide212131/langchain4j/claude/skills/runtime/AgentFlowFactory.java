package io.github.hide212131.langchain4j.claude.skills.runtime;

import io.github.hide212131.langchain4j.claude.skills.runtime.execution.ExecutionBackend;
import java.util.Objects;

/** プロバイダ設定に応じて Agent フローを生成するファクトリ。 */
public final class AgentFlowFactory {

    private final LlmConfiguration configuration;
    private final ExecutionBackend executionBackend;

    public AgentFlowFactory(LlmConfiguration configuration, ExecutionBackend executionBackend) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.executionBackend = Objects.requireNonNull(executionBackend, "executionBackend");
    }

    public AgentFlow create() {
        return switch (configuration.provider()) {
        case MOCK -> new DummyAgentFlow();
        case OPENAI -> new ExecutionPlanningFlow(configuration, executionBackend);
        };
    }
}
