package io.github.hide212131.langchain4j.claude.skills.runtime.blackboard;

import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.Objects;

/**
 * Helper class responsible for translating between {@link AgenticScope} entries and strongly typed
 * DTOs consumed by the runtime layers.
 */
public final class AgenticScopeBridge {

    public PlanState readPlanGoal(AgenticScope scope) {
        String goal = (String) readRequiredState(scope, PlanState.GOAL_KEY);
        return new PlanState(goal);
    }

    public PlanInputsState readPlanInputs(AgenticScope scope) {
        return new PlanInputsState(readRequiredState(scope, PlanInputsState.KEY));
    }

    public PlanCandidateStepsState readPlanCandidateSteps(AgenticScope scope) {
        return new PlanCandidateStepsState(readRequiredState(scope, PlanCandidateStepsState.KEY));
    }

    public PlanConstraintsState readPlanConstraints(AgenticScope scope) {
        return new PlanConstraintsState(readRequiredState(scope, PlanConstraintsState.KEY));
    }

    public PlanEvaluationCriteriaState readPlanEvaluationCriteria(AgenticScope scope) {
        return new PlanEvaluationCriteriaState(readRequiredState(scope, PlanEvaluationCriteriaState.KEY));
    }

    public ActState readActState(AgenticScope scope, String skillId) {
        AgenticScope nonNullScope = Objects.requireNonNull(scope, "scope");
        String key = ActState.outputKey(skillId);
        requireKey(nonNullScope, key);
        Object output = nonNullScope.readState(key);
        return new ActState(skillId, output);
    }

    public ActWindowState readActWindowState(AgenticScope scope) {
        return new ActWindowState(readRequiredState(scope, ActWindowState.KEY));
    }

    public ActCurrentStepState readActCurrentStep(AgenticScope scope) {
        return new ActCurrentStepState(readRequiredState(scope, ActCurrentStepState.KEY));
    }

    public ActInputBundleState readActInputBundle(AgenticScope scope) {
        return new ActInputBundleState(readRequiredState(scope, ActInputBundleState.KEY));
    }

    public SharedBlackboardIndexState readSharedBlackboardIndex(AgenticScope scope) {
        return new SharedBlackboardIndexState(readRequiredState(scope, SharedBlackboardIndexState.KEY));
    }

    public ReflectReviewState readReflectReview(AgenticScope scope) {
        return new ReflectReviewState(readRequiredState(scope, ReflectReviewState.KEY));
    }

    public ReflectRetryAdviceState readReflectRetryAdvice(AgenticScope scope) {
        return new ReflectRetryAdviceState(readRequiredState(scope, ReflectRetryAdviceState.KEY));
    }

    public ReflectFinalSummaryState readReflectFinalSummary(AgenticScope scope) {
        String summary = (String) readRequiredState(scope, ReflectFinalSummaryState.KEY);
        return new ReflectFinalSummaryState(summary);
    }

    public SharedContextSnapshotState readSharedContextSnapshot(AgenticScope scope) {
        return new SharedContextSnapshotState(readRequiredState(scope, SharedContextSnapshotState.KEY));
    }

    public SharedGuardState readSharedGuardState(AgenticScope scope) {
        return new SharedGuardState(readRequiredState(scope, SharedGuardState.KEY));
    }

    public SharedMetricsState readSharedMetrics(AgenticScope scope) {
        return new SharedMetricsState(readRequiredState(scope, SharedMetricsState.KEY));
    }

    private Object readRequiredState(AgenticScope scope, String key) {
        AgenticScope nonNullScope = Objects.requireNonNull(scope, "scope");
        requireKey(nonNullScope, key);
        return nonNullScope.readState(key);
    }

    private void requireKey(AgenticScope scope, String key) {
        if (!scope.hasState(key)) {
            throw new IllegalStateException("Missing required AgenticScope key: " + key);
        }
    }
}
