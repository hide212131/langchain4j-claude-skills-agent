package io.github.hide212131.langchain4j.claude.skills.runtime.workflow.reflect;

import dev.langchain4j.agentic.scope.AgenticScope;
import io.github.hide212131.langchain4j.claude.skills.infra.logging.WorkflowLogger;
import io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.BlackboardStore;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.act.DefaultInvoker;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.plan.DefaultPlanner;
import java.util.List;

/**
 * Minimal stub implementation for DefaultEvaluator to allow compilation.
 */
public final class DefaultEvaluator implements ReflectEvaluator {

    public DefaultEvaluator(BlackboardStore blackboard, WorkflowLogger logger) {
        // Stub constructor
    }

    @Override
    public EvaluationResult evaluate(
            AgenticScope scope,
            DefaultPlanner.PlanResult plan,
            DefaultInvoker.ActResult actResult,
            int attemptIndex,
            int maxAttempts) {
        return new EvaluationResult(
                true, false, null, 100, List.of("Stub evaluation complete"), "none", "Complete");
    }
}
