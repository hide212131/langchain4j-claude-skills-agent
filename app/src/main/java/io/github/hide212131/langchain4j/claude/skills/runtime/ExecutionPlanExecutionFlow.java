package io.github.hide212131.langchain4j.claude.skills.runtime;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatModel;
import io.github.hide212131.langchain4j.claude.skills.runtime.execution.CodeExecutionEnvironmentFactory;
import io.github.hide212131.langchain4j.claude.skills.runtime.execution.ExecutionResult;
import io.github.hide212131.langchain4j.claude.skills.runtime.execution.ExecutionBackend;
import io.github.hide212131.langchain4j.claude.skills.runtime.execution.PlanExecutorAgent;
import io.github.hide212131.langchain4j.claude.skills.runtime.execution.PlanExecutorAgent.PlanExecutionResult;
import io.github.hide212131.langchain4j.claude.skills.runtime.planning.ExecutionEnvironmentTool;
import io.github.hide212131.langchain4j.claude.skills.runtime.planning.ExecutionPlanningAgent;
import io.github.hide212131.langchain4j.claude.skills.runtime.planning.ExecutionTask;
import io.github.hide212131.langchain4j.claude.skills.runtime.planning.ExecutionTaskList;
import io.github.hide212131.langchain4j.claude.skills.runtime.planning.ExecutionTaskOutput;
import io.github.hide212131.langchain4j.claude.skills.runtime.planning.LocalResourceTool;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.MetricsPayload;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.VisibilityEvent;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.VisibilityEventMetadata;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.VisibilityEventPublisher;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.VisibilityEventType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
        return run(document, goal, null, null, log, basicLog, runId, skillPath, artifactsDir,
                VisibilityEventPublisher.noop());
    }

    @Override
    @SuppressWarnings({ "PMD.AvoidCatchingGenericException", "checkstyle:ParameterNumber" })
    public AgentFlowResult run(SkillDocument document, String goal, VisibilityLog log, boolean basicLog, String runId,
            String skillPath, String artifactsDir, VisibilityEventPublisher events) {
        return run(document, goal, null, null, log, basicLog, runId, skillPath, artifactsDir, events);
    }

    @Override
    @SuppressWarnings({ "PMD.AvoidCatchingGenericException", "checkstyle:ParameterNumber" })
    public AgentFlowResult run(SkillDocument document, String goal, String inputFilePath, String outputDirectoryPath,
            VisibilityLog log, boolean basicLog, String runId, String skillPath, String artifactsDir,
            VisibilityEventPublisher events) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(skillPath, "skillPath");
        Objects.requireNonNull(events, "events");

        String safeGoal = goal == null ? "" : goal.trim();
        String safeInputFilePath = inputFilePath == null ? "" : inputFilePath.trim();
        String safeOutputDirectoryPath = outputDirectoryPath == null ? "" : outputDirectoryPath.trim();
        ChatModel chatModel = buildChatModel(log, basicLog, runId, document.id(), events);
        CodeExecutionEnvironmentFactory environmentFactory = new CodeExecutionEnvironmentFactory(executionBackend);
        ExecutionEnvironmentTool environmentTool = new ExecutionEnvironmentTool(environmentFactory, Path.of(skillPath));
        long start = System.nanoTime();
        try {
            uploadInputFileIfNeeded(safeInputFilePath, environmentTool, log, basicLog, runId, document.id());
            ExecutionTaskList taskList = buildTaskList(chatModel, document, safeGoal, safeInputFilePath,
                    safeOutputDirectoryPath, skillPath, "", log, runId, environmentTool);
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
            List<String> downloaded = downloadArtifactsIfNeeded(safeOutputDirectoryPath, execution.artifacts(),
                    environmentTool, log, basicLog, runId, document.id());
            String finalOutput = extractFinalStdout(execution.taskList(), execution.results());
            String reflectLog = "実行後ステータス:" + System.lineSeparator() + execution.taskList().formatForLog();
            if (!downloaded.isEmpty()) {
                reflectLog = reflectLog + System.lineSeparator() + "ダウンロード成果物:" + System.lineSeparator()
                        + String.join(System.lineSeparator(), downloaded);
            }
            publishWorkflowMetrics(events, runId, document.id(), nanosToMillis(start));
            return new AgentFlowResult(planLog, actLog, reflectLog, finalOutput);
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
    private ExecutionTaskList buildTaskList(ChatModel chatModel, SkillDocument document, String goal,
            String inputFilePath, String outputDirectoryPath, String skillPath, String planSummary, VisibilityLog log,
            String runId, ExecutionEnvironmentTool environmentTool) {
        try {
            Path skillMdPath = Path.of(skillPath);
            LocalResourceTool resourceTool = new LocalResourceTool(skillMdPath);
            ExecutionPlanningAgent planner = new ExecutionPlanningAgent(chatModel, resourceTool, environmentTool);
            return planner.plan(document, goal, inputFilePath, outputDirectoryPath, skillPath, planSummary);
        } catch (RuntimeException ex) {
            log.warn(runId, document.id(), "plan", "plan.tasks", "実行計画の作成に失敗しました", "", "", ex);
            return ExecutionTaskList.empty(goal);
        }
    }

    private void uploadInputFileIfNeeded(String inputFilePath, ExecutionEnvironmentTool environmentTool,
            VisibilityLog log, boolean basicLog, String runId, String skillId) {
        if (inputFilePath == null || inputFilePath.isBlank()) {
            return;
        }
        log.info(basicLog, runId, skillId, "plan", "plan.input.upload", "入力ファイルをアップロードします", inputFilePath, "");
        environmentTool.uploadFile(Path.of(inputFilePath));
    }

    private List<String> downloadArtifactsIfNeeded(String outputDirectoryPath, List<String> artifacts,
            ExecutionEnvironmentTool environmentTool, VisibilityLog log, boolean basicLog, String runId,
            String skillId) {
        if (outputDirectoryPath == null || outputDirectoryPath.isBlank() || artifacts.isEmpty()) {
            return List.of();
        }
        List<String> downloaded = new ArrayList<>();
        Path outputDir = Path.of(outputDirectoryPath);
        try {
            Files.createDirectories(outputDir);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("出力フォルダの作成に失敗しました: " + outputDirectoryPath, ex);
        }
        for (String artifact : artifacts) {
            if (artifact == null || artifact.isBlank()) {
                continue;
            }
            Path fileName = Path.of(artifact).getFileName();
            if (fileName == null) {
                continue;
            }
            Path destination = outputDir.resolve(fileName.toString());
            log.info(basicLog, runId, skillId, "act", "task.output.download", "成果物をダウンロードします", artifact,
                    destination.toString());
            byte[] data = environmentTool.downloadFile(artifact);
            try {
                Files.write(destination, data);
            } catch (java.io.IOException ex) {
                throw new IllegalStateException("成果物の書き込みに失敗しました: " + destination, ex);
            }
            downloaded.add(destination.toString());
        }
        return List.copyOf(downloaded);
    }

    private static String extractFinalStdout(ExecutionTaskList taskList, List<ExecutionResult> results) {
        if (taskList == null || results == null || results.isEmpty()) {
            return "";
        }
        List<ExecutionTask> tasks = taskList.tasks();
        int limit = Math.min(tasks.size(), results.size());
        String finalOutput = "";
        for (int i = limit - 1; i >= 0 && finalOutput.isEmpty(); i--) {
            ExecutionTaskOutput output = tasks.get(i).output();
            if (output != null && isStdoutOutput(output.type())) {
                String stdout = results.get(i).stdout();
                finalOutput = stdout == null ? "" : stdout;
            }
        }
        return finalOutput;
    }

    private static boolean isStdoutOutput(String type) {
        return "stdout".equals(type) || "text".equals(type);
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
