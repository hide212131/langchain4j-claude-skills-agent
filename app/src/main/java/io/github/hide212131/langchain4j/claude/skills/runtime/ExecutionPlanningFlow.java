package io.github.hide212131.langchain4j.claude.skills.runtime;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatModel;
import io.github.hide212131.langchain4j.claude.skills.runtime.execution.CodeExecutionEnvironmentFactory;
import io.github.hide212131.langchain4j.claude.skills.runtime.execution.ExecutionBackend;
import io.github.hide212131.langchain4j.claude.skills.runtime.planning.ExecutionEnvironmentTool;
import io.github.hide212131.langchain4j.claude.skills.runtime.planning.ExecutionPlanningAgent;
import io.github.hide212131.langchain4j.claude.skills.runtime.planning.ExecutionTaskList;
import io.github.hide212131.langchain4j.claude.skills.runtime.planning.LocalResourceTool;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.InputPayload;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.MetricsPayload;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.SkillEvent;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.SkillEventMetadata;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.SkillEventPublisher;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.SkillEventType;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 実行計画作成にフォーカスした LLM フロー。 */
@SuppressWarnings({ "PMD.AvoidDuplicateLiterals", "PMD.GuardLogStatement", "PMD.CouplingBetweenObjects",
        "PMD.UseObjectForClearerAPI", "PMD.AvoidCatchingGenericException" })
final class ExecutionPlanningFlow implements AgentFlow {

    private final LlmConfiguration configuration;
    private final ExecutionBackend executionBackend;

    ExecutionPlanningFlow(LlmConfiguration configuration, ExecutionBackend executionBackend) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.executionBackend = Objects.requireNonNull(executionBackend, "executionBackend");
    }

    @Override
    @SuppressWarnings("checkstyle:ParameterNumber")
    public AgentFlowResult run(SkillDocument document, String goal, SkillLog log, boolean basicLog, String runId,
            String skillPath, String artifactsDir) {
        return run(document, goal, null, null, log, basicLog, runId, skillPath, artifactsDir,
                SkillEventPublisher.noop());
    }

    @Override
    @SuppressWarnings({ "PMD.AvoidCatchingGenericException", "checkstyle:ParameterNumber" })
    public AgentFlowResult run(SkillDocument document, String goal, SkillLog log, boolean basicLog, String runId,
            String skillPath, String artifactsDir, SkillEventPublisher events) {
        return run(document, goal, null, null, log, basicLog, runId, skillPath, artifactsDir, events);
    }

    @Override
    @SuppressWarnings({ "PMD.AvoidCatchingGenericException", "checkstyle:ParameterNumber" })
    public AgentFlowResult run(SkillDocument document, String goal, String inputFilePath, String outputDirectoryPath,
            SkillLog log, boolean basicLog, String runId, String skillPath, String artifactsDir,
            SkillEventPublisher events) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(skillPath, "skillPath");
        Objects.requireNonNull(events, "events");

        String safeGoal = goal == null ? "" : goal.trim();
        String safeInputFilePath = inputFilePath == null ? "" : inputFilePath.trim();
        String safeOutputDirectoryPath = outputDirectoryPath == null ? "" : outputDirectoryPath.trim();
        ChatModel chatModel = buildChatModel(log, basicLog, runId, document.id(), events);
        long start = System.nanoTime();
        try {
            Path skillMdPath = Path.of(skillPath);
            ExecutionEnvironmentTool environmentTool = new ExecutionEnvironmentTool(
                    new CodeExecutionEnvironmentFactory(executionBackend), skillMdPath, log, basicLog, runId,
                    document.id(), events);
            publishInputGoal(events, runId, document.id(), safeGoal);
            uploadInputFileIfNeeded(safeInputFilePath, environmentTool, log, basicLog, runId, document.id(), events);
            ExecutionTaskList taskList = buildTaskList(chatModel, document, safeGoal, safeInputFilePath,
                    safeOutputDirectoryPath, skillPath, "", log, runId, environmentTool);
            String planLog = taskList.formatForLog();
            log.info(basicLog, runId, document.id(), "plan", "plan.tasks", "実行計画を作成しました", "", planLog);
            publishWorkflowMetrics(events, runId, document.id(), nanosToMillis(start));
            String note = "実行計画のみを作成しました。";
            return new AgentFlowResult(planLog, note, note, "");
        } catch (RuntimeException ex) {
            log.error(runId, document.id(), "error", "planning.run",
                    "実行計画の作成中に例外が発生しました (apiKey=" + configuration.maskedApiKey() + ")", "", "", ex);
            throw ex;
        }
    }

    private ChatModel buildChatModel(SkillLog log, boolean basicLog, String runId, String skillId,
            SkillEventPublisher events) {
        OpenAiOfficialChatModel.Builder builder = OpenAiOfficialChatModel.builder().apiKey(configuration.openAiApiKey())
                .listeners(List.of(
                        new SkillChatModelListener(log, basicLog, runId, skillId, events, configuration.openAiModel())))
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
    private ExecutionTaskList buildTaskList(ChatModel chatModel, SkillDocument document, String goal,
            String inputFilePath, String outputDirectoryPath, String skillPath, String planSummary, SkillLog log,
            String runId, ExecutionEnvironmentTool environmentTool) {
        try {
            LocalResourceTool resourceTool = new LocalResourceTool(Path.of(skillPath));
            ExecutionPlanningAgent planner = new ExecutionPlanningAgent(chatModel, resourceTool, environmentTool);
            return planner.plan(document, goal, inputFilePath, outputDirectoryPath, skillPath, planSummary);
        } catch (RuntimeException ex) {
            log.warn(runId, document.id(), "plan", "plan.tasks", "実行計画の作成に失敗しました", "", "", ex);
            return ExecutionTaskList.empty(goal);
        }
    }

    private void uploadInputFileIfNeeded(String inputFilePath, ExecutionEnvironmentTool environmentTool, SkillLog log,
            boolean basicLog, String runId, String skillId, SkillEventPublisher events) {
        if (inputFilePath == null || inputFilePath.isBlank()) {
            return;
        }
        log.info(basicLog, runId, skillId, "plan", "plan.input.upload", "入力ファイルをアップロードします", inputFilePath, "");
        environmentTool.uploadFile(Path.of(inputFilePath));
        publishInputUpload(events, runId, skillId, inputFilePath);
    }

    private static void publishInputGoal(SkillEventPublisher events, String runId, String skillId, String goal) {
        SkillEventMetadata metadata = new SkillEventMetadata(runId, skillId, "plan", "plan.input.goal", null);
        events.publish(new SkillEvent(SkillEventType.INPUT, metadata, new InputPayload(goal, "")));
    }

    private static void publishInputUpload(SkillEventPublisher events, String runId, String skillId,
            String inputFilePath) {
        SkillEventMetadata metadata = new SkillEventMetadata(runId, skillId, "plan", "plan.input.upload", null);
        events.publish(new SkillEvent(SkillEventType.INPUT, metadata, new InputPayload("", inputFilePath)));
    }

    private static void publishWorkflowMetrics(SkillEventPublisher events, String runId, String skillId,
            long latencyMillis) {
        SkillEventMetadata metadata = new SkillEventMetadata(runId, skillId, "workflow", "workflow.done", null);
        events.publish(
                new SkillEvent(SkillEventType.METRICS, metadata, new MetricsPayload(null, null, latencyMillis, null)));
    }

    private static long nanosToMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
