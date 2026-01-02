package io.github.hide212131.langchain4j.claude.skills.runtime;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatModel;
import io.github.hide212131.langchain4j.claude.skills.runtime.execution.CodeExecutionEnvironmentFactory;
import io.github.hide212131.langchain4j.claude.skills.runtime.execution.ExecutionBackend;
import io.github.hide212131.langchain4j.claude.skills.runtime.execution.PlanExecutorAgent;
import io.github.hide212131.langchain4j.claude.skills.runtime.execution.PlanExecutorAgent.PlanExecutionResult;
import io.github.hide212131.langchain4j.claude.skills.runtime.planning.ExecutionEnvironmentTool;
import io.github.hide212131.langchain4j.claude.skills.runtime.planning.ExecutionPlanningAgent;
import io.github.hide212131.langchain4j.claude.skills.runtime.planning.ExecutionTaskList;
import io.github.hide212131.langchain4j.claude.skills.runtime.planning.LocalResourceTool;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.MetricsPayload;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.VisibilityEvent;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.VisibilityEventMetadata;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.VisibilityEventPublisher;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.VisibilityEventType;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 実行計画作成とタスク実行をまとめて行う LLM フロー。 */
@SuppressWarnings({ "PMD.AvoidDuplicateLiterals", "PMD.GuardLogStatement", "PMD.CouplingBetweenObjects",
        "PMD.UseObjectForClearerAPI", "PMD.AvoidCatchingGenericException" })
final class ExecutionPlanExecutionFlow implements AgentFlow {

    private final LlmConfiguration configuration;
    private final ExecutionBackend executionBackend;

    ExecutionPlanExecutionFlow(LlmConfiguration configuration, ExecutionBackend executionBackend) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.executionBackend = Objects.requireNonNull(executionBackend, "executionBackend");
    }

    @Override
    @SuppressWarnings("checkstyle:ParameterNumber")
    public AgentFlowResult run(SkillDocument document, String goal, VisibilityLog log, boolean basicLog, String runId,
            String skillPath, String artifactsDir) {
        return run(document, goal, log, basicLog, runId, skillPath, artifactsDir, VisibilityEventPublisher.noop());
    }

    @Override
    @SuppressWarnings({ "PMD.AvoidCatchingGenericException", "checkstyle:ParameterNumber" })
    public AgentFlowResult run(SkillDocument document, String goal, VisibilityLog log, boolean basicLog, String runId,
            String skillPath, String artifactsDir, VisibilityEventPublisher events) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(skillPath, "skillPath");
        Objects.requireNonNull(events, "events");

        String safeGoal = goal == null ? "" : goal.trim();
        ChatModel chatModel = buildChatModel(log, basicLog, runId, document.id(), events);
        CodeExecutionEnvironmentFactory environmentFactory = new CodeExecutionEnvironmentFactory(executionBackend);
        ExecutionEnvironmentTool environmentTool = new ExecutionEnvironmentTool(environmentFactory, Path.of(skillPath));
        long start = System.nanoTime();
        try {
            ExecutionTaskList taskList = buildTaskList(chatModel, document, safeGoal, skillPath, "", log, runId,
                    environmentTool);
            String planLog = taskList.formatForLog();
            if (taskList.tasks().isEmpty()) {
                String note = "実行計画のみを作成しました。";
                log.info(basicLog, runId, document.id(), "plan", "plan.tasks", "実行計画を作成しました", "", planLog);
                publishWorkflowMetrics(events, runId, document.id(), nanosToMillis(start));
                return new AgentFlowResult(planLog, note, note, "");
            }

            PlanExecutorAgent executor = new PlanExecutorAgent(chatModel, environmentTool);
            PlanExecutionResult execution = executor.execute(taskList, safeGoal, document.id(), runId, log, basicLog,
                    events);
            String actLog = execution.reportLog();
            String reflectLog = "実行後ステータス:" + System.lineSeparator() + execution.taskList().formatForLog();
            publishWorkflowMetrics(events, runId, document.id(), nanosToMillis(start));
            return new AgentFlowResult(planLog, actLog, reflectLog, "");
        } catch (RuntimeException ex) {
            log.error(runId, document.id(), "error", "execution.run",
                    "実行計画の作成またはタスク実行中に例外が発生しました (apiKey=" + configuration.maskedApiKey() + ")", "", "", ex);
            throw ex;
        }
    }

    private ChatModel buildChatModel(VisibilityLog log, boolean basicLog, String runId, String skillId,
            VisibilityEventPublisher events) {
        OpenAiOfficialChatModel.Builder builder = OpenAiOfficialChatModel.builder().apiKey(configuration.openAiApiKey())
                .listeners(List.of(new VisibilityChatModelListener(log, basicLog, runId, skillId, events,
                        configuration.openAiModel())))
                .supportedCapabilities(Set.of(Capability.RESPONSE_FORMAT_JSON_SCHEMA)).strictJsonSchema(true);
        if (configuration.openAiBaseUrl() != null) {
            builder.baseUrl(configuration.openAiBaseUrl());
        }
        if (configuration.openAiModel() != null) {
            builder.modelName(configuration.openAiModel());
        }
        return builder.build();
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private ExecutionTaskList buildTaskList(ChatModel chatModel, SkillDocument document, String goal, String skillPath,
            String planSummary, VisibilityLog log, String runId, ExecutionEnvironmentTool environmentTool) {
        try {
            Path skillMdPath = Path.of(skillPath);
            LocalResourceTool resourceTool = new LocalResourceTool(skillMdPath);
            ExecutionPlanningAgent planner = new ExecutionPlanningAgent(chatModel, resourceTool, environmentTool);
            return planner.plan(document, goal, skillPath, planSummary);
        } catch (RuntimeException ex) {
            log.warn(runId, document.id(), "plan", "plan.tasks", "実行計画の作成に失敗しました", "", "", ex);
            return ExecutionTaskList.empty(goal);
        }
    }

    private static void publishWorkflowMetrics(VisibilityEventPublisher events, String runId, String skillId,
            long latencyMillis) {
        VisibilityEventMetadata metadata = new VisibilityEventMetadata(runId, skillId, "workflow", "workflow.done",
                null);
        events.publish(new VisibilityEvent(VisibilityEventType.METRICS, metadata,
                new MetricsPayload(null, null, latencyMillis, null)));
    }

    private static long nanosToMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
