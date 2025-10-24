package io.github.hide212131.langchain4j.claude.skills.runtime.blackboard;

import java.util.Objects;

public record PlanCandidateStepsState(Object value) {

    public static final String KEY = "plan.candidateSteps";

    public PlanCandidateStepsState {
        Objects.requireNonNull(value, "value");
    }
}
