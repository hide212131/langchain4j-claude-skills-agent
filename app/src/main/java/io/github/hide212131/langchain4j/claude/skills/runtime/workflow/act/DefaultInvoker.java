package io.github.hide212131.langchain4j.claude.skills.runtime.workflow.act;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agentic.scope.AgenticScope;
import io.github.hide212131.langchain4j.claude.skills.infra.logging.WorkflowLogger;
import io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.ActCurrentStepState;
import io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.ActInputBundleState;
import io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.ActState;
import io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.ActWindowState;
import io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.BlackboardStore;
import io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.SharedBlackboardIndexState;
import io.github.hide212131.langchain4j.claude.skills.runtime.guard.SkillInvocationGuard;
import io.github.hide212131.langchain4j.claude.skills.runtime.guard.SkillInvocationGuard.BudgetSnapshot;
import io.github.hide212131.langchain4j.claude.skills.runtime.skill.SkillRuntime;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.plan.DefaultPlanner;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Minimal Act-stage invoker that executes planned skills sequentially via the {@link InvokeSkillTool}.
 */
public final class DefaultInvoker {

    private final InvokeSkillTool invokeSkillTool;
    private final SkillInvocationGuard guard;
    private final BlackboardStore blackboardStore;
    private final WorkflowLogger logger;

    public DefaultInvoker(
            InvokeSkillTool invokeSkillTool,
            SkillInvocationGuard guard,
            BlackboardStore blackboardStore,
            WorkflowLogger logger) {
        this.invokeSkillTool = Objects.requireNonNull(invokeSkillTool, "invokeSkillTool");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.blackboardStore = Objects.requireNonNull(blackboardStore, "blackboardStore");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public ActResult invoke(AgenticScope scope, DefaultPlanner.PlanResult plan) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(plan, "plan");
        List<DefaultPlanner.PlanStep> steps = plan.steps();
        List<String> invokedSkills = new ArrayList<>(steps.size());
        Map<String, Object> outputs = new LinkedHashMap<>();
        Path lastArtifact = null;

        scope.writeState(
                ActWindowState.KEY,
                Map.of(
                        "goal", plan.goal(),
                        "plannedSkillIds", plan.orderedSkillIds(),
                        "remainingToolCalls", steps.size()));

        for (DefaultPlanner.PlanStep step : steps) {
            guard.ensureAllowed(step.skillId());
            guard.checkBudgets(new BudgetSnapshot(steps.size() - invokedSkills.size(), Integer.MAX_VALUE));

            Map<String, Object> inputBundle = Map.of(
                    "goal", plan.goal(),
                    "skillId", step.skillId(),
                    "description", step.description(),
                    "keywords", step.keywords());
            scope.writeState(
                    ActCurrentStepState.KEY,
                    Map.of("skillId", step.skillId(), "name", step.name()));
            scope.writeState(ActInputBundleState.KEY, inputBundle);

            SkillRuntime.ExecutionResult executionResult = invokeSkillTool.invoke(step.skillId(), inputBundle);
            Object outputValue = executionResult.outputs();
            blackboardStore.put(ActState.outputKey(step.skillId()), outputValue);
            outputs.put(step.skillId(), outputValue);
            invokedSkills.add(step.skillId());
            scope.writeState(
                    ActWindowState.KEY,
                    Map.of(
                            "goal", plan.goal(),
                            "plannedSkillIds", plan.orderedSkillIds(),
                            "executedSkillCount", invokedSkills.size()));
            if (executionResult.hasArtifact()) {
                lastArtifact = executionResult.artifactPath();
            }
            logger.info("Skill {} completed with outputs {}", step.skillId(), executionResult.outputs());
        }

        scope.writeState(SharedBlackboardIndexState.KEY, List.copyOf(invokedSkills));
        return new ActResult(List.copyOf(invokedSkills), Map.copyOf(outputs), lastArtifact);
    }

    public BlackboardStore blackboardStore() {
        return blackboardStore;
    }

    public ToolMetadata toolMetadata() {
        return new ToolMetadata(invokeSkillTool.specification());
    }

    public record ActResult(List<String> invokedSkills, Map<String, Object> outputs, Path finalArtifact) {
        public ActResult {
            Objects.requireNonNull(invokedSkills, "invokedSkills");
            Objects.requireNonNull(outputs, "outputs");
        }

        public boolean hasArtifact() {
            return finalArtifact != null;
        }
    }

    public record ToolMetadata(ToolSpecification specification) {
        public ToolMetadata {
            Objects.requireNonNull(specification, "specification");
        }
    }
}
