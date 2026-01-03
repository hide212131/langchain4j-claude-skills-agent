package io.github.hide212131.langchain4j.claude.skills.runtime;

import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.AgentStatePayload;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.PromptPayload;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.SkillEvent;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.SkillEventMetadata;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.SkillEventPublisher;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.SkillEventType;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

/** 固定レスポンスの Plan/Act/Reflect スタブ。ログや外部依存は持たず、決定論的に結果を返す。 */
@SuppressWarnings("PMD.GuardLogStatement")
public final class DummyAgentFlow implements AgentFlow {

    @SuppressWarnings("PMD.UnnecessaryConstructor")
    public DummyAgentFlow() {
        // default
    }

    public AgentFlowResult run(SkillDocument document, String goal) {
        SkillLog log = new SkillLog(Logger.getLogger(DummyAgentFlow.class.getName()));
        return run(document, goal, log, false, UUID.randomUUID().toString(), "", null, SkillEventPublisher.noop());
    }

    @Override
    public AgentFlowResult run(SkillDocument document, String goal, SkillLog log, boolean basicLog, String runId,
            String skillPath, String artifactsDir) {
        return run(document, goal, log, basicLog, runId, skillPath, artifactsDir, SkillEventPublisher.noop());
    }

    @Override
    public AgentFlowResult run(SkillDocument document, String goal, SkillLog log, boolean basicLog, String runId,
            String skillPath, String artifactsDir, SkillEventPublisher events) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(skillPath, "skillPath");
        Objects.requireNonNull(events, "events");
        String safeGoal = goal == null ? "" : goal.trim();

        String plan = "Plan: " + (safeGoal.isEmpty() ? "SKILL.md に従う" : safeGoal);
        log.info(basicLog, runId, document.id(), "plan", "plan.prompt", "Plan を生成しました",
                "goal=" + (safeGoal.isEmpty() ? "(none)" : safeGoal), plan);
        events.publish(new SkillEvent(SkillEventType.PROMPT,
                new SkillEventMetadata(runId, document.id(), "plan", "plan.prompt", null),
                new PromptPayload(safeGoal.isEmpty() ? "goalなし" : safeGoal, plan, null, "assistant", null)));

        String act = "Act: " + document.name() + " を実行";
        log.info(basicLog, runId, document.id(), "act", "act.call", "Act を実行しました", "", act);
        events.publish(new SkillEvent(SkillEventType.AGENT_STATE,
                new SkillEventMetadata(runId, document.id(), "act", "act.call", null),
                new AgentStatePayload(safeGoal, act, "dummy-act")));

        String reflect = "Reflect: 完了 (goal=" + (safeGoal.isEmpty() ? "なし" : safeGoal) + ")";
        log.info(basicLog, runId, document.id(), "reflect", "reflect.eval", "Reflect を実行しました", "", reflect);
        events.publish(new SkillEvent(SkillEventType.PROMPT,
                new SkillEventMetadata(runId, document.id(), "reflect", "reflect.eval", null),
                new PromptPayload("act結果を振り返り", reflect, null, "assistant", null)));

        String artifact = document.body() + System.lineSeparator() + "---" + System.lineSeparator() + "Goal: "
                + (safeGoal.isEmpty() ? "(none)" : safeGoal) + System.lineSeparator() + "Skill: " + document.id();
        return new AgentFlowResult(plan, act, reflect, artifact);
    }
}
