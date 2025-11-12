package io.github.hide212131.langchain4j.claude.skills.runtime.workflow.act;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
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
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.plan.PlanModels;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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

    public ActResult invoke(AgenticScope scope, PlanModels.PlanResult plan) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(plan, "plan");
        invokeSkillTool.resetOutputDirectory();

        List<PlanModels.PlanStep> steps = plan.steps();
        List<String> invokedSkills = new ArrayList<>(steps.size());
        Map<String, Object> outputs = new LinkedHashMap<>();
        Path lastArtifact = null;

        List<String> orderedSkillIds = plan.orderedSkillIds();
        ActWindowState windowState = ActWindowState.initial(plan.goal(), orderedSkillIds);
        ActWindowState.STATE.write(scope, windowState);

        // Execute each skill sequentially using helper method
        for (PlanModels.PlanStep step : steps) {
            executeSkillStep(scope, step, steps.size(), invokedSkills);
            
            // Read the output from blackboard
            Object output = blackboardStore.get(ActState.outputKey(step.skillId())).orElse(null);
            if (output != null) {
                outputs.put(step.skillId(), output);
                
                // Check if this output contains an artifact path
                if (output instanceof Map<?, ?> outputMap) {
                    Object artifactPath = outputMap.get("artifactPath");
                    if (artifactPath != null) {
                        lastArtifact = Path.of(artifactPath.toString());
                    }
                }
            }
            
            invokedSkills.add(step.skillId());
            windowState = windowState.progress(invokedSkills.size());
            ActWindowState.STATE.write(scope, windowState);
        }

        SharedBlackboardIndexState.STATE.write(scope, new SharedBlackboardIndexState(invokedSkills));
        return new ActResult(List.copyOf(invokedSkills), Map.copyOf(outputs), lastArtifact);
    }

    /**
     * Executes a single skill step, managing guards, state updates, and skill invocation.
     * This method encapsulates the logic that would be in a skill agent.
     */
    private void executeSkillStep(
            AgenticScope scope,
            PlanModels.PlanStep step,
            int totalSteps,
            List<String> invokedSkills) {
        
        guard.ensureAllowed(step.skillId());
        guard.checkBudgets(new BudgetSnapshot(totalSteps - invokedSkills.size(), Integer.MAX_VALUE));

        ActCurrentStepState.STATE.write(scope, new ActCurrentStepState(step.skillId(), step.name()));
        Map<String, Object> invocationInputs = Map.of(
                "goal", step.stepGoal(),
                "skillId", step.skillId(),
                "description", step.description(),
                "keywords", step.keywords(),
                "skillRoot", step.skillRoot().toString());
        ActInputBundleState.STATE.write(scope, new ActInputBundleState(
                step.stepGoal(),
                step.skillId(),
                step.description(),
                step.keywords(),
                step.skillRoot()));

        SkillRuntime.ExecutionResult executionResult = invokeSkillTool.invoke(step.skillId(), invocationInputs);
        Object outputValue = executionResult.outputs();
        blackboardStore.put(ActState.outputKey(step.skillId()), outputValue);
        ActState.STATE.write(scope, new ActState(step.skillId(), outputValue));
        
        logger.info("Skill {} completed with outputs {}", step.skillId(), executionResult.outputs());
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
