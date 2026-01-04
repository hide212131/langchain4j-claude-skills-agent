package io.github.hide212131.langchain4j.claude.skills.runtime.execution;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.github.hide212131.langchain4j.claude.skills.runtime.SkillLog;
import io.github.hide212131.langchain4j.claude.skills.runtime.planning.ExecutionEnvironmentTool;
import io.github.hide212131.langchain4j.claude.skills.runtime.planning.ExecutionTask;
import io.github.hide212131.langchain4j.claude.skills.runtime.planning.ExecutionTaskFixtures;
import io.github.hide212131.langchain4j.claude.skills.runtime.planning.ExecutionTaskList;
import io.github.hide212131.langchain4j.claude.skills.runtime.planning.ExecutionTaskOutput;
import io.github.hide212131.langchain4j.claude.skills.runtime.planning.ExecutionTaskStatus;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.SkillEvent;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.SkillEventAssertions;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.SkillEventCollector;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.SkillEventType;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@SuppressWarnings("PMD.JUnitTestContainsTooManyAsserts")
class PlanExecutorAgentTest {

    private static final String GOAL = "goal";
    private static final String SKILL_ID = "skill-1";

    @SuppressWarnings("PMD.UnnecessaryConstructor")
    PlanExecutorAgentTest() {
        // default
    }

    @Test
    @DisplayName("タスク成功時に完了ステータスと成果物を返し、イベントを発行する")
    void executeHappyPath() {
        StubChatModel chatModel = new StubChatModel("""
                {"command":"echo ok","exitCode":0,"stdout":"ok","stderr":"","elapsedMs":5}
                """);
        try (ExecutionEnvironmentTool tool = new ExecutionEnvironmentTool(
                new CodeExecutionEnvironmentFactory(ExecutionBackend.DOCKER), Path.of("skill.md"));
                SkillEventCollector collector = new SkillEventCollector()) {
            PlanExecutorAgent agent = new PlanExecutorAgent(chatModel, tool);
            ExecutionTaskOutput output = new ExecutionTaskOutput("file", "/workspace/out.txt", "成果物");
            ExecutionTask task = ExecutionTaskFixtures.commandTask("task-1", "サンプル", "echo ok", output);
            ExecutionTaskList plan = ExecutionTaskFixtures.singleCommandPlan(GOAL, task);

            PlanExecutorAgent.PlanExecutionResult result = agent.execute(plan, GOAL, SKILL_ID, "run-1", silentLog(),
                    true, collector);

            assertThat(result.taskList().tasks()).hasSize(1);
            assertThat(result.taskList().tasks().get(0).status()).isEqualTo(ExecutionTaskStatus.COMPLETED);
            assertThat(result.artifacts()).containsExactly("/workspace/out.txt");

            List<SkillEvent> events = collector.events();
            SkillEvent start = SkillEventAssertions.findByStep(events, SkillEventType.AGENT_STATE, "task.start");
            SkillEvent complete = SkillEventAssertions.findByStep(events, SkillEventType.AGENT_STATE, "task.complete");
            assertThat(start.metadata().runId()).isEqualTo("run-1");
            assertThat(complete.metadata().skillId()).isEqualTo(SKILL_ID);
        }
    }

    @Test
    @DisplayName("失敗時にエラーイベントを出してタスクを中断する")
    void executeFailure() {
        StubChatModel chatModel = new StubChatModel("""
                {"command":"exit 1","exitCode":1,"stdout":"","stderr":"boom","elapsedMs":2}
                """);
        try (ExecutionEnvironmentTool tool = new ExecutionEnvironmentTool(
                new CodeExecutionEnvironmentFactory(ExecutionBackend.DOCKER), Path.of("skill.md"));
                SkillEventCollector collector = new SkillEventCollector()) {
            PlanExecutorAgent agent = new PlanExecutorAgent(chatModel, tool);
            ExecutionTaskOutput output = new ExecutionTaskOutput("stdout", "", "");
            ExecutionTask task = ExecutionTaskFixtures.commandTask("task-1", "失敗ケース", "exit 1", output);
            ExecutionTaskList plan = ExecutionTaskFixtures.singleCommandPlan(GOAL, task);

            PlanExecutorAgent.PlanExecutionResult result = agent.execute(plan, GOAL, SKILL_ID, "run-2", silentLog(),
                    true, collector);

            assertThat(result.taskList().tasks()).hasSize(1);
            assertThat(result.taskList().tasks().get(0).status()).isEqualTo(ExecutionTaskStatus.FAILED);
            assertThat(collector.events()).extracting(SkillEvent::type).contains(SkillEventType.ERROR);
            SkillEventAssertions.findByStep(collector.events(), SkillEventType.ERROR, "task.failed");
        }
    }

    @Test
    @DisplayName("出力パスが空の file 出力は成果物として扱わない")
    void ignoreBlankFileOutput() {
        StubChatModel chatModel = new StubChatModel("""
                {"command":"echo ok","exitCode":0,"stdout":"ok","stderr":"","elapsedMs":1}
                """);
        try (ExecutionEnvironmentTool tool = new ExecutionEnvironmentTool(
                new CodeExecutionEnvironmentFactory(ExecutionBackend.DOCKER), Path.of("skill.md"));
                SkillEventCollector collector = new SkillEventCollector()) {
            PlanExecutorAgent agent = new PlanExecutorAgent(chatModel, tool);
            ExecutionTaskOutput output = new ExecutionTaskOutput("file", "", "");
            ExecutionTask task = ExecutionTaskFixtures.commandTask("task-1", "空パス", "echo ok", output);
            ExecutionTaskList plan = ExecutionTaskFixtures.singleCommandPlan(GOAL, task);

            PlanExecutorAgent.PlanExecutionResult result = agent.execute(plan, GOAL, SKILL_ID, "run-3", silentLog(),
                    true, collector);

            assertThat(result.artifacts()).isEmpty();
        }
    }

    private SkillLog silentLog() {
        Logger logger = Logger.getLogger("plan-executor-test");
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.OFF);
        return new SkillLog(logger);
    }

    private static final class StubChatModel implements ChatModel {
        private final List<String> responses;
        private int index;

        private StubChatModel(String... responses) {
            this.responses = List.of(responses);
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            String response = responses.isEmpty() ? "" : responses.get(Math.min(index, responses.size() - 1));
            index++;
            return ChatResponse.builder().aiMessage(AiMessage.from(response)).build();
        }
    }

}
