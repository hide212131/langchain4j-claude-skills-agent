package io.github.hide212131.langchain4j.claude.skills.runtime.execution;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.github.hide212131.langchain4j.claude.skills.runtime.SkillLog;
import io.github.hide212131.langchain4j.claude.skills.runtime.planning.ExecutionEnvironmentTool;
import io.github.hide212131.langchain4j.claude.skills.runtime.planning.ExecutionTask;
import io.github.hide212131.langchain4j.claude.skills.runtime.planning.ExecutionTaskList;
import io.github.hide212131.langchain4j.claude.skills.runtime.planning.ExecutionTaskOutput;
import io.github.hide212131.langchain4j.claude.skills.runtime.planning.ExecutionTaskStatus;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.AgentStatePayload;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.ErrorPayload;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.SkillEvent;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.SkillEventMetadata;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.SkillEventPublisher;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.SkillEventType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 実行計画のタスクを順次実行するエージェント。
 */
@SuppressWarnings({ "PMD.GuardLogStatement", "PMD.TooManyMethods", "PMD.AvoidDuplicateLiterals",
        "PMD.AvoidCatchingGenericException", "PMD.ExceptionAsFlowControl" })
public final class PlanExecutorAgent {

    private static final String PHASE_ACT = "act";
    private static final String OUTPUT_TYPE_FILE = "file";
    private static final String OUTPUT_TYPE_NONE = "none";

    private final TaskExecutionAgent agent;

    public PlanExecutorAgent(ChatModel chatModel, ExecutionEnvironmentTool environmentTool) {
        Objects.requireNonNull(chatModel, "chatModel");
        Objects.requireNonNull(environmentTool, "environmentTool");
        this.agent = AgenticServices.agentBuilder(TaskExecutionAgent.class).chatModel(chatModel).tools(environmentTool)
                .build();
    }

    public PlanExecutionResult execute(ExecutionTaskList taskList, String goal, String skillId, String runId,
            SkillLog log, boolean basicLog, SkillEventPublisher events) {
        Objects.requireNonNull(taskList, "taskList");
        Objects.requireNonNull(skillId, "skillId");
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(events, "events");

        List<ExecutionTask> completed = new ArrayList<>();
        List<ExecutionResult> results = new ArrayList<>();
        List<String> artifacts = new ArrayList<>();
        for (ExecutionTask task : taskList.tasks()) {
            publishTaskState(events, runId, skillId, "task.start", goal, task, "実行中");
            log.info(basicLog, runId, skillId, PHASE_ACT, "task.start", "タスクを実行します", taskSummary(task), "");
            try {
                ExecutionResult result = agent.execute(goalValue(goal), task.id(), task.title(), task.description(),
                        task.input(), task.action(), describeOutput(task.output()));
                log.info(basicLog, runId, skillId, PHASE_ACT, "task.execute", "タスクを実行しました", taskSummary(task),
                        resultSummary(result));
                results.add(result);
                collectArtifact(task.output(), artifacts);
                publishTaskState(events, runId, skillId, "task.complete", goal, task, "完了");
                log.info(basicLog, runId, skillId, PHASE_ACT, "task.complete", "タスクが完了しました", taskSummary(task),
                        resultSummary(result));
                completed.add(task.withStatus(ExecutionTaskStatus.COMPLETED));
            } catch (RuntimeException ex) {
                publishTaskError(events, runId, skillId, task, ex);
                log.error(runId, skillId, "error", "task.failed", "タスク実行が失敗しました", taskSummary(task), "", ex);
                throw new IllegalStateException("タスク実行が失敗しました: " + taskSummary(task), ex);
            }
        }
        ExecutionTaskList updated = new ExecutionTaskList(taskList.goal(), completed);
        String report = ExecutionReportFormatter.format(results, artifacts);
        return new PlanExecutionResult(updated, results, artifacts, report);
    }

    private static void collectArtifact(ExecutionTaskOutput output, List<String> artifacts) {
        if (output == null) {
            return;
        }
        if (OUTPUT_TYPE_FILE.equals(output.type()) && !output.path().isBlank()) {
            artifacts.add(output.path());
        }
    }

    private static String goalValue(String goal) {
        return goal == null ? "" : goal;
    }

    private static String describeOutput(ExecutionTaskOutput output) {
        if (output == null) {
            return "出力情報なし";
        }
        if (OUTPUT_TYPE_NONE.equals(output.type())) {
            return "出力なし";
        }
        String path = output.path().isBlank() ? "" : " path=" + output.path();
        String desc = output.description().isBlank() ? "" : " (" + output.description() + ")";
        return output.type() + path + desc;
    }

    private static String taskSummary(ExecutionTask task) {
        StringBuilder sb = new StringBuilder(64);
        if (!task.id().isBlank()) {
            sb.append("id=").append(task.id());
        }
        if (!task.title().isBlank()) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append("title=").append(task.title());
        }
        if (!task.command().isBlank()) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append("command=").append(task.command());
        }
        return sb.toString();
    }

    private static String resultSummary(ExecutionResult result) {
        return "exit=" + result.exitCode() + " stdout=" + preview(result.stdout()) + " stderr="
                + preview(result.stderr());
    }

    private static String preview(String text) {
        if (text == null || text.isBlank()) {
            return "(empty)";
        }
        String trimmed = text.trim();
        return trimmed.substring(0, Math.min(trimmed.length(), 200));
    }

    private static void publishTaskState(SkillEventPublisher events, String runId, String skillId, String step,
            String goal, ExecutionTask task, String state) {
        SkillEventMetadata metadata = new SkillEventMetadata(runId, skillId, "act", step, null);
        String summary = "taskId=" + safe(task.id()) + " title=" + safe(task.title()) + " status=" + state;
        events.publish(new SkillEvent(SkillEventType.AGENT_STATE, metadata,
                new AgentStatePayload(goalValue(goal), step, summary)));
    }

    private static void publishTaskError(SkillEventPublisher events, String runId, String skillId, ExecutionTask task,
            Throwable error) {
        SkillEventMetadata metadata = new SkillEventMetadata(runId, skillId, "error", "task.failed", null);
        String message = "タスク実行でエラーが発生しました: " + safe(task.id()) + " " + error.getMessage();
        events.publish(new SkillEvent(SkillEventType.ERROR, metadata,
                new ErrorPayload(message, error.getClass().getSimpleName())));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public record PlanExecutionResult(ExecutionTaskList taskList, List<ExecutionResult> results, List<String> artifacts,
            String reportLog) {
        public PlanExecutionResult(ExecutionTaskList taskList, List<ExecutionResult> results, List<String> artifacts,
                String reportLog) {
            this.taskList = Objects.requireNonNull(taskList, "taskList");
            this.results = results == null ? List.of() : List.copyOf(results);
            this.artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
            this.reportLog = reportLog == null ? "" : reportLog;
        }
    }

    @SuppressWarnings("PMD.UseObjectForClearerAPI")
    public interface TaskExecutionAgent {
        @SystemMessage("""
                タスクの command 有無に従い実行手段を選択する
                - command あり: ExecutionEnvironmentTool でコマンドを実行し、結果を返す
                - command なし: ゴールとタスク情報に基づいて必要な出力を生成する
                command は "(agent)"、exitCode は 0、stdout に生成結果、stderr は空文字列、elapsedMs は 0 を設定する
                """)
        @UserMessage("""
                ゴール: {{goal}}
                タスクID: {{taskId}}
                タイトル: {{taskTitle}}
                説明: {{taskDescription}}
                入力: {{taskInput}}
                アクション: {{taskAction}}
                出力要件: {{taskOutput}}
                """)
        @Agent(value = "planExecutorAgent", description = "実行計画のタスクを実行する")
        ExecutionResult execute(@V("goal") String goal, @V("taskId") String taskId, @V("taskTitle") String taskTitle,
                @V("taskDescription") String taskDescription, @V("taskInput") String taskInput,
                @V("taskAction") String taskAction, @V("taskOutput") String taskOutput);
    }
}
