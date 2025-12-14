package io.github.hide212131.langchain4j.claude.skills.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DummyAgentFlowTest {

    @Test
    @DisplayName("Goal を含む固定レスポンスで Plan/Act/Reflect を返す")
    void runDummyFlow() {
        SkillDocument document = new SkillDocument("sample", "Sample", "Desc", "Body content");
        DummyAgentFlow flow = new DummyAgentFlow();

        DummyAgentFlow.Result result = flow.run(document, "demo goal");

        assertThat(result.planLog()).contains("demo goal");
        assertThat(result.actLog()).contains("Sample");
        assertThat(result.reflectLog()).contains("demo goal");
        assertThat(result.artifactContent()).contains("Body content").contains("Goal: demo goal");
    }
}
