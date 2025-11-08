package io.github.hide212131.langchain4j.claude.skills.runtime.blackboard;

import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.AgentStateKey;

public record PlanInputsState(String goal, boolean dryRun, int attempt) {

    public static final String KEY = "plan.inputs";
    public static final AgentStateKey<PlanInputsState> STATE = AgentStateKey.of(KEY, PlanInputsState.class);

    public PlanInputsState {
        if (goal == null || goal.isBlank()) {
            throw new IllegalArgumentException("goal must be provided");
        }
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be 1-indexed");
        }
    }
}
