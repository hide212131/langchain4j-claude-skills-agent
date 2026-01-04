package io.github.hide212131.langchain4j.claude.skills.runtime.planning;

import java.util.List;

public final class ExecutionTaskFixtures {

    private ExecutionTaskFixtures() {
    }

    /** テスト用の実行タスク/タスクリストを簡潔に組み立てるためのフィクスチャ。 */
    public static ExecutionTaskList singleCommandPlan(String goal, ExecutionTask task) {
        return new ExecutionTaskList(goal, List.of(task));
    }

    public static ExecutionTask commandTask(String id, String title, String command, ExecutionTaskOutput output) {
        return new ExecutionTask(id, title, "テスト用タスク", ExecutionTaskStatus.PENDING, "", "コマンド実行", command, output);
    }
}
