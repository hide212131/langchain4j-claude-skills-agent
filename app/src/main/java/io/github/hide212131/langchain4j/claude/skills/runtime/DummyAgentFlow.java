package io.github.hide212131.langchain4j.claude.skills.runtime;

import io.github.hide212131.langchain4j.claude.skills.runtime.AgentFlow.AgentFlowResult;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

/** 固定レスポンスの Plan/Act/Reflect スタブ。ログや外部依存は持たず、決定論的に結果を返す。 */
public final class DummyAgentFlow implements AgentFlow {

  public AgentFlowResult run(SkillDocument document, String goal) {
    VisibilityLog log = new VisibilityLog(Logger.getLogger(DummyAgentFlow.class.getName()));
    return run(document, goal, log, false, UUID.randomUUID().toString());
  }

  @Override
  public AgentFlowResult run(
      SkillDocument document, String goal, VisibilityLog log, boolean basicLog, String runId) {
    Objects.requireNonNull(document, "document");
    Objects.requireNonNull(log, "log");
    Objects.requireNonNull(runId, "runId");
    String safeGoal = goal == null ? "" : goal.trim();

    String plan = "Plan: " + (safeGoal.isEmpty() ? "SKILL.md に従う" : safeGoal);
    log.info(
        basicLog,
        runId,
        document.id(),
        "plan",
        "plan.prompt",
        "Plan を生成しました",
        "goal=" + (safeGoal.isEmpty() ? "(none)" : safeGoal),
        plan);

    String act = "Act: " + document.name() + " を実行";
    log.info(basicLog, runId, document.id(), "act", "act.call", "Act を実行しました", "", act);

    String reflect = "Reflect: 完了 (goal=" + (safeGoal.isEmpty() ? "なし" : safeGoal) + ")";
    log.info(
        basicLog, runId, document.id(), "reflect", "reflect.eval", "Reflect を実行しました", "", reflect);

    String artifact =
        document.body()
            + System.lineSeparator()
            + "---"
            + System.lineSeparator()
            + "Goal: "
            + (safeGoal.isEmpty() ? "(none)" : safeGoal)
            + System.lineSeparator()
            + "Skill: "
            + document.id();
    return new AgentFlowResult(plan, act, reflect, artifact);
  }
}
