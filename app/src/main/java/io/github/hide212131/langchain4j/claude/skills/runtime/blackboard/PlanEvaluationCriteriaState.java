package io.github.hide212131.langchain4j.claude.skills.runtime.blackboard;

import java.util.Objects;

public record PlanEvaluationCriteriaState(Object value) {

    public static final String KEY = "plan.evaluationCriteria";

    public PlanEvaluationCriteriaState {
        Objects.requireNonNull(value, "value");
    }
}
