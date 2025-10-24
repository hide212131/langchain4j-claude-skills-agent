package io.github.hide212131.langchain4j.claude.skills.runtime.blackboard;

import java.util.Objects;

public record ActCurrentStepState(Object value) {

    public static final String KEY = "act.currentStep";

    public ActCurrentStepState {
        Objects.requireNonNull(value, "value");
    }
}
