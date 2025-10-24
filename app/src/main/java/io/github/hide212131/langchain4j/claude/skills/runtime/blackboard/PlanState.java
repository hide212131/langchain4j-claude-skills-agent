package io.github.hide212131.langchain4j.claude.skills.runtime.blackboard;

import java.util.Objects;

public record PlanState(String goal) {

    public static final String GOAL_KEY = "plan.goal";

    public PlanState {
        Objects.requireNonNull(goal, "goal");
    }
}
