package io.github.hide212131.langchain4j.claude.skills.runtime.planning;

import java.util.Objects;

/**
 * 実行計画のタスク。
 */
public record ExecutionTask(String id, ExecutionTaskStatus status, ExecutionTaskLocation location, String input,
        String action, String command, ExecutionTaskOutput output) {

    public ExecutionTask(String id, ExecutionTaskStatus status, ExecutionTaskLocation location, String input,
            String action, String command, ExecutionTaskOutput output) {
        this.id = normalize(id);
        this.status = status == null ? ExecutionTaskStatus.PENDING : status;
        this.location = location == null ? ExecutionTaskLocation.LOCAL : location;
        this.input = input == null ? "" : input;
        this.action = action == null ? "" : action;
        this.command = command == null ? "" : command;
        this.output = output == null ? new ExecutionTaskOutput("none", "", "") : output;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim();
    }

    public ExecutionTask withId(String newId) {
        Objects.requireNonNull(newId, "newId");
        return new ExecutionTask(newId, status, location, input, action, command, output);
    }

    public String formatForLog() {
        StringBuilder sb = new StringBuilder(128);
        sb.append('[').append(status.label()).append("] ").append(location.label());
        if (!action.isBlank()) {
            sb.append(" / ").append(action);
        }
        if (!input.isBlank()) {
            sb.append(" / 入力: ").append(input);
        }
        if (location == ExecutionTaskLocation.REMOTE && !command.isBlank()) {
            sb.append(" / command: ").append(command);
        }
        if (!"none".equals(output.type())) {
            sb.append(" / 出力: ").append(output.type());
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
