package io.github.hide212131.langchain4j.claude.skills.runtime.workflow.reflect;

import dev.langchain4j.agentic.scope.AgenticScope;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.act.DefaultInvoker;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.plan.PlanModels;
import java.nio.file.Path;
import java.util.List;

/**
 * Minimal stub interface for ReflectEvaluator to allow compilation.
 */
public interface ReflectEvaluator {
    EvaluationResult evaluate(
            AgenticScope scope,
            PlanModels.PlanResult plan,
            DefaultInvoker.ActResult actResult,
            int attemptIndex,
            int maxAttempts);

    record EvaluationResult(
            boolean success,
            boolean needsRetry,
            Path artifact,
            int qualityScore,
            List<String> reviewComments,
            String retryAdvice,
            String finalSummary) {}
}
