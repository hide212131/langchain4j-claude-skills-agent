package io.github.hide212131.langchain4j.claude.skills.runtime.blackboard;

import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.AgentStateKey;

public record ActCurrentStepState(String skillId, String name) {

    public static final String KEY = "act.currentStep";
    public static final AgentStateKey<ActCurrentStepState> STATE = AgentStateKey.of(KEY, ActCurrentStepState.class);

    public ActCurrentStepState {
        if (skillId == null || skillId.isBlank()) {
            throw new IllegalArgumentException("skillId must be provided");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must be provided");
        }
    }
}
