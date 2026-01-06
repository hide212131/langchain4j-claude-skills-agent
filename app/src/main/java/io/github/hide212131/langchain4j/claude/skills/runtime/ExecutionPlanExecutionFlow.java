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
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.InputPayload;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.MetricsPayload;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.OutputPayload;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.SkillEvent;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.SkillEventMetadata;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.SkillEventPublisher;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.SkillEventType;
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
        CodeExecutionEnvironmentFactory environmentFactory = new CodeExecutionEnvironmentFactory(executionBackend);
        long start = System.nanoTime();
        try (ExecutionEnvironmentTool environmentTool = new ExecutionEnvironmentTool(environmentFactory,
                Path.of(skillPath), log, basicLog, runId, document.id(), events)) {
            publishInputGoal(events, runId, document.id(), safeGoal);
            String remoteInputFilePath = uploadInputFileIfNeeded(safeInputFilePath, environmentTool, log, basicLog,
                    runId, document.id(), events);
            ExecutionTaskList taskList = buildTaskList(chatModel, document, safeGoal, remoteInputFilePath,
                    safeOutputDirectoryPath, skillPath, "", log, runId, environmentTool);
            String planLog = taskList.formatForLog();
            if (taskList.tasks().isEmpty()) {
                String note = "実行計画のみを作成しました。";
                log.info(basicLog, runId, document.id(), "plan", "plan.tasks", "実行計画を作成しました", "", planLog);
                publishWorkflowMetrics(events, runId, document.id(), nanosToMillis(start));
                return new AgentFlowResult(planLog, note, note, "");
            }

            PlanExecutorAgent executor = new PlanExecutorAgent(chatModel, environmentTool, document.body());
            PlanExecutionResult execution = executor.execute(taskList, safeGoal, document.id(), runId, log, basicLog,
                    events);
            String actLog = execution.reportLog();
            List<String> downloaded = downloadArtifactsIfNeeded(safeOutputDirectoryPath, execution.artifacts(),
                    environmentTool, log, basicLog, runId, document.id(), events);
            String finalOutput = extractFinalStdout(execution.taskList(), execution.results());
            publishOutputResults(events, runId, document.id(), execution.taskList(), execution.results());
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
            Path skillMdPath = Path.of(skillPath);
            LocalResourceTool resourceTool = new LocalResourceTool(skillMdPath);
            ExecutionPlanningAgent planner = new ExecutionPlanningAgent(chatModel, resourceTool, environmentTool);
            return planner.plan(document, goal, inputFilePath, outputDirectoryPath, planSummary);
        } catch (RuntimeException ex) {
            log.warn(runId, document.id(), "plan", "plan.tasks", "実行計画の作成に失敗しました", "", "", ex);
            return ExecutionTaskList.empty(goal);
        }
    }

    private String uploadInputFileIfNeeded(String inputFilePath, ExecutionEnvironmentTool environmentTool, SkillLog log,
            boolean basicLog, String runId, String skillId, SkillEventPublisher events) {
        if (inputFilePath == null || inputFilePath.isBlank()) {
            return "";
        }
        log.info(basicLog, runId, skillId, "plan", "plan.input.upload", "入力ファイルをアップロードします", inputFilePath, "");
        String remotePath = environmentTool.uploadFile(Path.of(inputFilePath));
        publishInputUpload(events, runId, skillId, remotePath);
        return remotePath == null ? "" : remotePath.trim();
    }

    private List<String> downloadArtifactsIfNeeded(String outputDirectoryPath, List<String> artifacts,
            ExecutionEnvironmentTool environmentTool, SkillLog log, boolean basicLog, String runId, String skillId,
            SkillEventPublisher events) {
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
            String normalizedArtifact = normalizeRemoteArtifactPath(artifact);
            Path fileName = Path.of(normalizedArtifact).getFileName();
            if (fileName == null) {
                continue;
            }
            Path destination = outputDir.resolve(fileName.toString());
            log.info(basicLog, runId, skillId, "act", "task.output.download", "成果物をダウンロードします", normalizedArtifact,
                    destination.toString());
            byte[] data = environmentTool.downloadFile(normalizedArtifact);
            try {
                Files.write(destination, data);
            } catch (java.io.IOException ex) {
                throw new IllegalStateException("成果物の書き込みに失敗しました: " + destination, ex);
            }
            publishOutputDownload(events, runId, skillId, normalizedArtifact, destination.toString());
            downloaded.add(destination.toString());
        }
        return List.copyOf(downloaded);
    }

    private static String normalizeRemoteArtifactPath(String artifact) {
        String trimmed = artifact.trim();
        String normalized = trimmed.replace('\\', '/');
        if (normalized.startsWith("/workspace/")) {
            return normalized;
        }
        Path path = Path.of(normalized);
        if (path.isAbsolute()) {
            return normalized;
        }
        Path cleaned = path.normalize();
        if (cleaned.startsWith("..")) {
            return normalized;
        }
        String relative = cleaned.toString().replace('\\', '/');
        if (relative.startsWith("./")) {
            relative = relative.substring(2);
        }
        return "/workspace/" + relative;
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

    private static void publishOutputDownload(SkillEventPublisher events, String runId, String skillId,
            String sourcePath, String destinationPath) {
        SkillEventMetadata metadata = new SkillEventMetadata(runId, skillId, "act", "task.output.download", null);
        events.publish(new SkillEvent(SkillEventType.OUTPUT, metadata,
                new OutputPayload("download", "", sourcePath, destinationPath, "")));
    }

    private static void publishOutputResults(SkillEventPublisher events, String runId, String skillId,
            ExecutionTaskList taskList, List<ExecutionResult> results) {
        if (taskList == null || results == null || results.isEmpty()) {
            return;
        }
        List<ExecutionTask> tasks = taskList.tasks();
        int limit = Math.min(tasks.size(), results.size());
        for (int i = 0; i < limit; i++) {
            ExecutionTask task = tasks.get(i);
            ExecutionTaskOutput output = task.output();
            if (output == null || !isStdoutOutput(output.type())) {
                continue;
            }
            String outputType = output.type() == ExecutionTaskOutput.OutputType.TEXT ? "llm" : "stdout";
            SkillEventMetadata metadata = new SkillEventMetadata(runId, skillId, "act", "task.output." + outputType,
                    null);
            events.publish(new SkillEvent(SkillEventType.OUTPUT, metadata,
                    new OutputPayload(outputType, task.id(), "", "", results.get(i).stdout())));
        }
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

    private static boolean isStdoutOutput(ExecutionTaskOutput.OutputType type) {
        return type == ExecutionTaskOutput.OutputType.STDOUT || type == ExecutionTaskOutput.OutputType.TEXT;
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
