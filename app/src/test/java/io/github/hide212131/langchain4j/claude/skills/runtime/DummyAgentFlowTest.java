package io.github.hide212131.langchain4j.claude.skills.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.hide212131.langchain4j.claude.skills.runtime.AgentFlow.AgentFlowResult;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.AgentStatePayload;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.PromptPayload;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.VisibilityEvent;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.VisibilityEventCollector;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.VisibilityEventType;
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
    void runEmitsVisibilityEvents() {
        SkillDocument document = new SkillDocument("sample", "Sample", "Desc", "Body content");
        DummyAgentFlow flow = new DummyAgentFlow();
        VisibilityEventCollector collector = new VisibilityEventCollector();
        VisibilityLog log = new VisibilityLog(Logger.getLogger(DummyAgentFlow.class.getName()));

        flow.run(document, DEMO_GOAL, log, true, "run-1", collector);

        List<VisibilityEvent> events = collector.events();
        assertThat(events).hasSizeGreaterThanOrEqualTo(3);
        assertThat(events).extracting(VisibilityEvent::type).contains(VisibilityEventType.PROMPT,
                VisibilityEventType.AGENT_STATE);

        VisibilityEvent planEvent = events.stream().filter(event -> "plan.prompt".equals(event.metadata().step()))
                .findFirst().orElseThrow();
        assertThat(((PromptPayload) planEvent.payload()).prompt()).contains(DEMO_GOAL);
        assertThat(planEvent.metadata().runId()).isEqualTo("run-1");

        VisibilityEvent actEvent = events.stream().filter(event -> "act.call".equals(event.metadata().step()))
                .findFirst().orElseThrow();
        assertThat(((AgentStatePayload) actEvent.payload()).decision()).contains("Act:");
        assertThat(actEvent.metadata().skillId()).isEqualTo("sample");
    }
}
