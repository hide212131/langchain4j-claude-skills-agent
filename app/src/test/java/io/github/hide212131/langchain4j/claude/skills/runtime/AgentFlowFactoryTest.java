package io.github.hide212131.langchain4j.claude.skills.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AgentFlowFactoryTest {

    @Test
    @DisplayName("LLM_PROVIDER=openai の場合は OpenAiAgentFlow を生成する")
    void createOpenAiFlow() {
        LlmConfiguration config =
                new LlmConfiguration(LlmProvider.OPENAI, "sk-test-12345678", "https://api.openai.example", "gpt-4o");

        AgentFlow flow = new AgentFlowFactory(config).create();

        assertThat(flow).isInstanceOf(OpenAiAgentFlow.class);
    }
}
