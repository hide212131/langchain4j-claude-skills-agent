package io.github.hide212131.langchain4j.claude.skills.runtime.blackboard;

import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.AgentStateKey;
import java.util.Objects;

public record PlanEvaluationCriteriaState(String systemPromptSummary, String assistantDraft, int attempt) {

    public static final String KEY = "plan.evaluationCriteria";
    public static final AgentStateKey<PlanEvaluationCriteriaState> STATE =
            AgentStateKey.of(KEY, PlanEvaluationCriteriaState.class);

    public PlanEvaluationCriteriaState {
        Objects.requireNonNull(systemPromptSummary, "systemPromptSummary");
        assistantDraft = assistantDraft == null ? "" : assistantDraft;
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be 1-indexed");
        }
    }
}
