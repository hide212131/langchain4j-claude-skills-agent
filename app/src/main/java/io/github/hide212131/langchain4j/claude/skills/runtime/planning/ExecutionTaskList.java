package io.github.hide212131.langchain4j.claude.skills.runtime.planning;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 実行計画のタスクリスト。
 */
public record ExecutionTaskList(String goal, List<ExecutionTask> tasks) {

    public ExecutionTaskList(String goal, List<ExecutionTask> tasks) {
        this.goal = goal == null ? "" : goal;
        this.tasks = normalizeTasks(tasks);
    }

    public static ExecutionTaskList empty(String goal) {
        return new ExecutionTaskList(goal, List.of());
    }

    public String formatForLog() {
        StringBuilder sb = new StringBuilder(256);
        sb.append("実行タスクリスト").append(System.lineSeparator());
        if (!goal.isBlank()) {
            sb.append("ゴール: ").append(goal).append(System.lineSeparator());
        }
        if (tasks.isEmpty()) {
            sb.append("タスクはありません。");
            return sb.toString();
        }
        int index = 1;
        for (ExecutionTask task : tasks) {
            sb.append(index).append(". ").append(task.formatForLog());
            if (index < tasks.size()) {
                sb.append(System.lineSeparator());
            }
            index++;
        }
        return sb.toString();
    }

    private static List<ExecutionTask> normalizeTasks(List<ExecutionTask> tasks) {
        List<ExecutionTask> safe = tasks == null ? List.of() : List.copyOf(tasks);
        if (safe.isEmpty()) {
            return safe;
        }
        List<ExecutionTask> normalized = new ArrayList<>(safe.size());
        int index = 1;
        for (ExecutionTask task : safe) {
            ExecutionTask value = Objects.requireNonNull(task, "task");
            if (value.id().isBlank()) {
                value = value.withId("task-" + index);
            }
            normalized.add(value);
            index++;
        }
        return List.copyOf(normalized);
    }
}
