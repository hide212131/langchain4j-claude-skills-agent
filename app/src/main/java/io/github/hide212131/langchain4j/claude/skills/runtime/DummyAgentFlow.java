package io.github.hide212131.langchain4j.claude.skills.runtime;

import java.util.Objects;

/**
 * 固定レスポンスの Plan/Act/Reflect スタブ。ログや外部依存は持たず、決定論的に結果を返す。
 */
public final class DummyAgentFlow {

    public Result run(SkillDocument document, String goal) {
        Objects.requireNonNull(document, "document");
        String safeGoal = goal == null ? "" : goal.trim();
        String plan = "Plan: " + (safeGoal.isEmpty() ? "SKILL.md に従う" : safeGoal);
        String act = "Act: " + document.name() + " を実行";
        String reflect = "Reflect: 完了 (goal=" + (safeGoal.isEmpty() ? "なし" : safeGoal) + ")";
        String artifact = document.body()
                + System.lineSeparator()
                + "---"
                + System.lineSeparator()
                + "Goal: "
                + (safeGoal.isEmpty() ? "(none)" : safeGoal)
                + System.lineSeparator()
                + "Skill: "
                + document.id();
        return new Result(plan, act, reflect, artifact);
    }

    public record Result(String planLog, String actLog, String reflectLog, String artifactContent) {

        public String formatted() {
            return planLog
                    + System.lineSeparator()
                    + actLog
                    + System.lineSeparator()
                    + reflectLog
                    + System.lineSeparator()
                    + "---"
                    + System.lineSeparator()
                    + artifactContent;
        }
    }
}
