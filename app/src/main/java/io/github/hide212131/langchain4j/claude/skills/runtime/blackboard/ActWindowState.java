package io.github.hide212131.langchain4j.claude.skills.runtime.blackboard;

import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.AgentStateKey;
import java.util.List;
import java.util.Objects;

public record ActWindowState(String goal, List<String> plannedSkillIds, int executedSkillCount, int remainingToolCalls) {

    public static final String KEY = "act.windowState";
    public static final AgentStateKey<ActWindowState> STATE = AgentStateKey.of(KEY, ActWindowState.class);

    public ActWindowState {
        if (goal == null || goal.isBlank()) {
            throw new IllegalArgumentException("goal must be provided");
        }
        Objects.requireNonNull(plannedSkillIds, "plannedSkillIds");
        plannedSkillIds = List.copyOf(plannedSkillIds);
        if (executedSkillCount < 0) {
            throw new IllegalArgumentException("executedSkillCount must be non-negative");
        }
        if (remainingToolCalls < 0) {
            throw new IllegalArgumentException("remainingToolCalls must be non-negative");
        }
    }

    public static ActWindowState initial(String goal, List<String> plannedSkillIds) {
        Objects.requireNonNull(plannedSkillIds, "plannedSkillIds");
        return new ActWindowState(goal, plannedSkillIds, 0, plannedSkillIds.size());
    }

    public ActWindowState progress(int executedSkillCount) {
        int remaining = Math.max(0, plannedSkillIds.size() - executedSkillCount);
        return new ActWindowState(goal, plannedSkillIds, executedSkillCount, remaining);
    }
}
