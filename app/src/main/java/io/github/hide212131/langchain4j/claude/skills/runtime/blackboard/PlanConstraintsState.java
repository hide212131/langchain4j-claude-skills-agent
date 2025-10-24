package io.github.hide212131.langchain4j.claude.skills.runtime.blackboard;

import java.util.Objects;

public record PlanConstraintsState(Object value) {

    public static final String KEY = "plan.constraints";

    public PlanConstraintsState {
        Objects.requireNonNull(value, "value");
    }
}
