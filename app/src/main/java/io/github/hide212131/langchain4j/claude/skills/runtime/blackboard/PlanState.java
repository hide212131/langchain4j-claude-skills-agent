package io.github.hide212131.langchain4j.claude.skills.runtime.blackboard;

import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.AgentStateKey;
import java.util.Objects;

public record PlanState(String goal) {

    public static final String KEY = "plan.goal";
    public static final AgentStateKey<PlanState> STATE = AgentStateKey.of(KEY, PlanState.class);

    public PlanState {
        Objects.requireNonNull(goal, "goal");
    }
}
