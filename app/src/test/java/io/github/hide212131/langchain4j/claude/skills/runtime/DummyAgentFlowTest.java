package io.github.hide212131.langchain4j.claude.skills.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.hide212131.langchain4j.claude.skills.runtime.AgentFlow.AgentFlowResult;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.AgentStatePayload;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.PromptPayload;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.SkillEvent;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.SkillEventCollector;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.SkillEventType;
import java.util.List;
import java.util.logging.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@SuppressWarnings("PMD.JUnitTestContainsTooManyAsserts")
class DummyAgentFlowTest {

    private static final String DEMO_GOAL = "demo goal";

    @SuppressWarnings("PMD.UnnecessaryConstructor")
    DummyAgentFlowTest() {
        // default
    }

    @Test
    @DisplayName("Goal を含む固定レスポンスで Plan/Act/Reflect を返す")
    void runDummyFlow() {
        SkillDocument document = new SkillDocument("sample", "Sample", "Desc", "Body content");
        DummyAgentFlow flow = new DummyAgentFlow();

        AgentFlowResult result = flow.run(document, DEMO_GOAL);

        assertThat(result.planLog()).contains(DEMO_GOAL);
        assertThat(result.actLog()).contains("Sample");
        assertThat(result.reflectLog()).contains(DEMO_GOAL);
        assertThat(result.artifactContent()).contains("Body content").contains("Goal: " + DEMO_GOAL);
    }

    @Test
    @DisplayName("Plan/Act/Reflect のイベントを可視化スキーマで出力する")
    void runEmitsSkillEvents() {
        SkillDocument document = new SkillDocument("sample", "Sample", "Desc", "Body content");
        DummyAgentFlow flow = new DummyAgentFlow();
        try (SkillEventCollector collector = new SkillEventCollector()) {
            SkillLog log = new SkillLog(Logger.getLogger(DummyAgentFlow.class.getName()));

            flow.run(document, DEMO_GOAL, log, true, "run-1", "dummy-skill", null, collector);

            List<SkillEvent> events = collector.events();
            assertThat(events).hasSizeGreaterThanOrEqualTo(3);
            assertThat(events).extracting(SkillEvent::type).contains(SkillEventType.PROMPT, SkillEventType.AGENT_STATE);

            SkillEvent planEvent = events.stream().filter(event -> "plan.prompt".equals(event.metadata().step()))
                    .findFirst().orElseThrow();
            assertThat(((PromptPayload) planEvent.payload()).prompt()).contains(DEMO_GOAL);
            assertThat(planEvent.metadata().runId()).isEqualTo("run-1");

            SkillEvent actEvent = events.stream().filter(event -> "act.call".equals(event.metadata().step()))
                    .findFirst().orElseThrow();
            assertThat(((AgentStatePayload) actEvent.payload()).decision()).contains("Act:");
            assertThat(actEvent.metadata().skillId()).isEqualTo("sample");
        }
    }
}
