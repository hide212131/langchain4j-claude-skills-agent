package io.github.hide212131.langchain4j.claude.skills.runtime.blackboard;

import java.util.Objects;

public record PlanInputsState(Object value) {

    public static final String KEY = "plan.inputs";

    public PlanInputsState {
        Objects.requireNonNull(value, "value");
    }
}
