package io.github.hide212131.langchain4j.claude.skills.runtime.blackboard;

import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.Objects;

/**
 * Helper class responsible for translating between {@link AgenticScope} entries and strongly typed
 * DTOs consumed by the runtime layers.
 */
public final class AgenticScopeBridge {

    public PlanState readPlanGoal(AgenticScope scope) {
        return PlanState.STATE.readRequired(scope);
    }

    public PlanInputsState readPlanInputs(AgenticScope scope) {
        return PlanInputsState.STATE.readRequired(scope);
    }

    public PlanCandidateStepsState readPlanCandidateSteps(AgenticScope scope) {
        return PlanCandidateStepsState.STATE.readRequired(scope);
    }

    public PlanConstraintsState readPlanConstraints(AgenticScope scope) {
        return PlanConstraintsState.STATE.readRequired(scope);
    }

    public PlanEvaluationCriteriaState readPlanEvaluationCriteria(AgenticScope scope) {
        return PlanEvaluationCriteriaState.STATE.readRequired(scope);
    }

    public ActState readActState(AgenticScope scope, String skillId) {
        AgenticScope nonNullScope = Objects.requireNonNull(scope, "scope");
        String key = ActState.outputKey(skillId);
        if (!nonNullScope.hasState(key)) {
            throw new IllegalStateException("Missing required AgenticScope key: " + key);
        }
        Object output = nonNullScope.readState(key);
        return new ActState(skillId, output);
    }

    public ActWindowState readActWindowState(AgenticScope scope) {
        return ActWindowState.STATE.readRequired(scope);
    }

    public ActCurrentStepState readActCurrentStep(AgenticScope scope) {
        return ActCurrentStepState.STATE.readRequired(scope);
    }

    public ActInputBundleState readActInputBundle(AgenticScope scope) {
        return ActInputBundleState.STATE.readRequired(scope);
    }

    public SharedBlackboardIndexState readSharedBlackboardIndex(AgenticScope scope) {
        return SharedBlackboardIndexState.STATE.readRequired(scope);
    }

    public ReflectReviewState readReflectReview(AgenticScope scope) {
        return ReflectReviewState.STATE.readRequired(scope);
    }

    public ReflectRetryAdviceState readReflectRetryAdvice(AgenticScope scope) {
        return ReflectRetryAdviceState.STATE.readRequired(scope);
    }

    public ReflectFinalSummaryState readReflectFinalSummary(AgenticScope scope) {
        return ReflectFinalSummaryState.STATE.readRequired(scope);
    }

    public SharedContextSnapshotState readSharedContextSnapshot(AgenticScope scope) {
        return SharedContextSnapshotState.STATE.readRequired(scope);
    }

    public SharedGuardState readSharedGuardState(AgenticScope scope) {
        return SharedGuardState.STATE.readRequired(scope);
    }

    public SharedMetricsState readSharedMetrics(AgenticScope scope) {
        return SharedMetricsState.STATE.readRequired(scope);
    }
}
