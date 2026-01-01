package io.github.hide212131.langchain4j.claude.skills.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;
import io.github.hide212131.langchain4j.claude.skills.runtime.execution.ExecutionBackend;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@SuppressWarnings("PMD.JUnitTestContainsTooManyAsserts")
class AgentFlowFactoryTest {

    @SuppressWarnings("PMD.UnnecessaryConstructor")
    AgentFlowFactoryTest() {
        // default
    }

    @Test
    @DisplayName("LLM_PROVIDER=openai の場合は ExecutionPlanningFlow を生成する")
    void createOpenAiFlow() {
        LlmConfiguration config = new LlmConfiguration(LlmProvider.OPENAI, "sk-test-12345678",
                "https://api.openai.example", "gpt-4o");

        AgentFlow flow = new AgentFlowFactory(config, ExecutionBackend.DOCKER).create();

        assertThat(flow).isInstanceOf(ExecutionPlanningFlow.class);
    }

    @Test
    @DisplayName("mock プロバイダでは決定論的な Plan/Act/Reflect を返す")
    void runMockFlowDeterministically() {
        LlmConfiguration config = new LlmConfiguration(LlmProvider.MOCK, null, null, null);
        AgentFlow flow = new AgentFlowFactory(config, ExecutionBackend.DOCKER).create();
        SkillDocument document = new SkillDocument("mock-skill", "Mock Skill", "Desc", "Body line");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        VisibilityLog log = new VisibilityLog(newLogger(out));

        AgentFlow.AgentFlowResult result = flow.run(document, "mock goal", log, true, "run-mock-1", "dummy-skill",
                null);

        assertThat(result.planLog()).contains("mock goal");
        assertThat(result.actLog()).contains("Mock Skill");
        assertThat(result.reflectLog()).contains("mock goal");
        assertThat(result.artifactContent()).contains("Goal: mock goal").contains("Skill: mock-skill");
        String logs = out.toString(StandardCharsets.UTF_8);
        assertThat(logs).contains("phase=plan").contains("phase=act").contains("phase=reflect")
                .contains("run=run-mock-1").contains("skill=mock-skill");
    }

    private Logger newLogger(ByteArrayOutputStream out) {
        Logger logger = Logger.getLogger("agent-flow-factory-" + UUID.randomUUID());
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        Handler handler = new StreamHandler(out, new SimpleFormatter()) {
            @Override
            public synchronized void publish(LogRecord record) {
                super.publish(record);
                flush();
            }
        };
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);
        return logger;
    }
}
