package io.github.hide212131.langchain4j.claude.skills.runtime.planning;

import dev.langchain4j.model.output.structured.Description;
import java.util.Objects;

/**
 * 実行計画のタスク。
 */
public record ExecutionTask(@Description("タスク識別子。未指定の場合は連番を付与する。") String id,
        @Description("短いタスク名。一覧で識別できる粒度で記述する。") String title, @Description("タスク内容の説明。自然言語で簡潔に記述する。") String description,
        @Description("タスクの状態。計画作成時は PENDING を用いる。") ExecutionTaskStatus status,
        @Description("入力情報。自然言語の指示やファイルパスを記述する。") String input,
        @Description("具体的なタスク実施内容。コマンド実行かまたはLLM推論による生成") String action,
        @Description("コマンド実行が必要な場合の具体的なコマンド。パスはフルパスで記述する。") String command,
        @Description("出力情報。標準出力やファイル出力などの種別と詳細を記述する。") ExecutionTaskOutput output) {

    @SuppressWarnings("checkstyle:ParameterNumber")
    public ExecutionTask(String id, String title, String description, ExecutionTaskStatus status, String input,
            String action, String command, ExecutionTaskOutput output) {
        this.id = normalize(id);
        this.title = normalize(title);
        this.description = normalize(description);
        this.status = status == null ? ExecutionTaskStatus.PENDING : status;
        this.input = input == null ? "" : input;
        this.action = action == null ? "" : action;
        this.command = command == null ? "" : command;
        this.output = output == null ? new ExecutionTaskOutput(ExecutionTaskOutput.OutputType.NONE, "", "") : output;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim();
    }

    public ExecutionTask withId(String newId) {
        Objects.requireNonNull(newId, "newId");
        return new ExecutionTask(newId, title, description, status, input, action, command, output);
    }

    public ExecutionTask withStatus(ExecutionTaskStatus newStatus) {
        Objects.requireNonNull(newStatus, "newStatus");
        return new ExecutionTask(id, title, description, newStatus, input, action, command, output);
    }

    public String formatForLog() {
        StringBuilder sb = new StringBuilder(128);
        sb.append('[').append(status.label()).append(']');
        if (!title.isBlank()) {
            sb.append(" / タイトル: ").append(title);
        }
        if (!action.isBlank()) {
            sb.append(" / ").append(action);
        }
        if (!description.isBlank()) {
            sb.append(" / 詳細: ").append(description);
        }
        if (!input.isBlank()) {
            sb.append(" / 入力: ").append(input);
        }
        if (!command.isBlank()) {
            sb.append(" / command: ").append(command);
        }
        if (output.type() != ExecutionTaskOutput.OutputType.NONE) {
            sb.append(" / 出力: ").append(output.type().name());
            if (!output.path().isBlank()) {
                sb.append(" (").append(output.path()).append(')');
            }
            if (!output.description().isBlank()) {
                sb.append(" - ").append(output.description());
            }
        }
        return sb.toString();
    }
}
