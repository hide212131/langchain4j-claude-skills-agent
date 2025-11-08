package io.github.hide212131.langchain4j.claude.skills.runtime.blackboard;

import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.AgentStateKey;

public record PlanConstraintsState(int tokenBudget, int maxToolCalls, int maxAttempts, int attempt) {

    public static final String KEY = "plan.constraints";
    public static final AgentStateKey<PlanConstraintsState> STATE = AgentStateKey.of(KEY, PlanConstraintsState.class);

    public PlanConstraintsState {
        if (tokenBudget < 0) {
            throw new IllegalArgumentException("tokenBudget must be non-negative");
        }
        if (maxToolCalls < 0) {
            throw new IllegalArgumentException("maxToolCalls must be non-negative");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be 1-indexed");
        }
    }
}
