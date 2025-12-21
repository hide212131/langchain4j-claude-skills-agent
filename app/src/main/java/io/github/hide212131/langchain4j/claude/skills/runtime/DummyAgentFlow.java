package io.github.hide212131.langchain4j.claude.skills.runtime;

import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.AgentStatePayload;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.PromptPayload;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.VisibilityEvent;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.VisibilityEventMetadata;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.VisibilityEventPublisher;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.VisibilityEventType;
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
        VisibilityLog log = new VisibilityLog(Logger.getLogger(DummyAgentFlow.class.getName()));
        return run(document, goal, log, false, UUID.randomUUID().toString(), VisibilityEventPublisher.noop());
    }

    @Override
    public AgentFlowResult run(SkillDocument document, String goal, VisibilityLog log, boolean basicLog, String runId) {
        return run(document, goal, log, basicLog, runId, VisibilityEventPublisher.noop());
    }

    @Override
    public AgentFlowResult run(SkillDocument document, String goal, VisibilityLog log, boolean basicLog, String runId,
            VisibilityEventPublisher events) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(events, "events");
        String safeGoal = goal == null ? "" : goal.trim();

        String plan = "Plan: " + (safeGoal.isEmpty() ? "SKILL.md に従う" : safeGoal);
        log.info(basicLog, runId, document.id(), "plan", "plan.prompt", "Plan を生成しました",
                "goal=" + (safeGoal.isEmpty() ? "(none)" : safeGoal), plan);
        events.publish(new VisibilityEvent(VisibilityEventType.PROMPT,
                new VisibilityEventMetadata(runId, document.id(), "plan", "plan.prompt", null),
                new PromptPayload(safeGoal.isEmpty() ? "goalなし" : safeGoal, plan, null, "assistant", null)));

        String act = "Act: " + document.name() + " を実行";
        log.info(basicLog, runId, document.id(), "act", "act.call", "Act を実行しました", "", act);
        events.publish(new VisibilityEvent(VisibilityEventType.AGENT_STATE,
                new VisibilityEventMetadata(runId, document.id(), "act", "act.call", null),
                new AgentStatePayload(safeGoal, act, "dummy-act")));

        String reflect = "Reflect: 完了 (goal=" + (safeGoal.isEmpty() ? "なし" : safeGoal) + ")";
        log.info(basicLog, runId, document.id(), "reflect", "reflect.eval", "Reflect を実行しました", "", reflect);
        events.publish(new VisibilityEvent(VisibilityEventType.PROMPT,
                new VisibilityEventMetadata(runId, document.id(), "reflect", "reflect.eval", null),
                new PromptPayload("act結果を振り返り", reflect, null, "assistant", null)));

        String artifact = document.body() + System.lineSeparator() + "---" + System.lineSeparator() + "Goal: "
                + (safeGoal.isEmpty() ? "(none)" : safeGoal) + System.lineSeparator() + "Skill: " + document.id();
        return new AgentFlowResult(plan, act, reflect, artifact);
    }
}
