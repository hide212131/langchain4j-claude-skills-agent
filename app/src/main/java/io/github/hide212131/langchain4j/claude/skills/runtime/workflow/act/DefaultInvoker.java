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
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.WorkflowStateKeys;
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
        List<String> orderedSkillIds = plan.orderedSkillIds();
        ActWindowState windowState = ActWindowState.initial(plan.goal(), orderedSkillIds);
        ActWindowState.STATE.write(scope, windowState);

        // Initialize results collection in scope
        Map<String, Object> skillOutputs = new LinkedHashMap<>();
        
        // Create dynamic sequence of skill agents
        List<SkillExecutionAgent> skillAgents = steps.stream()
                .map(step -> new SkillExecutionAgent(step, invokeSkillTool, guard, logger, skillOutputs))
                .collect(Collectors.toList());

        // Build dynamic sequence that executes all skills
        UntypedAgent dynamicSequence = AgenticServices.sequenceBuilder()
                .name("dynamic-skills-execution")
                .subAgents(skillAgents.toArray())
                .output(s -> assembleActResult(s, plan, skillOutputs))
                .build();

        // Execute the dynamic sequence
        ActResult result = (ActResult) dynamicSequence.invoke(Map.of("goal", plan.goal()));
        
        // Write final state to parent scope
        SharedBlackboardIndexState.STATE.write(scope, new SharedBlackboardIndexState(result.invokedSkills()));
        WorkflowStateKeys.ACT_RESULT.write(scope, result);
        
        return result;
    }

    /**
     * Agent that executes a single skill step.
     * Uses @Agent annotation to be recognized by the agent framework.
     */
    public static final class SkillExecutionAgent {
        private final PlanModels.PlanStep step;
        private final InvokeSkillTool invokeSkillTool;
        private final SkillInvocationGuard guard;
        private final WorkflowLogger logger;
        private final Map<String, Object> sharedOutputs;

        SkillExecutionAgent(
                PlanModels.PlanStep step,
                InvokeSkillTool invokeSkillTool,
                SkillInvocationGuard guard,
                WorkflowLogger logger,
                Map<String, Object> sharedOutputs) {
            this.step = Objects.requireNonNull(step, "step");
            this.invokeSkillTool = Objects.requireNonNull(invokeSkillTool, "invokeSkillTool");
            this.guard = Objects.requireNonNull(guard, "guard");
            this.logger = Objects.requireNonNull(logger, "logger");
            this.sharedOutputs = Objects.requireNonNull(sharedOutputs, "sharedOutputs");
        }

        @Agent(name = "skill-execution")
        public void execute(AgenticScope agenticScope) {
            // Guard: Ensure skill invocation is allowed
            guard.ensureAllowed(step.skillId());
            guard.checkBudgets(new BudgetSnapshot(1, Integer.MAX_VALUE));

            // State management: Write current step information to AgenticScope
            ActCurrentStepState.STATE.write(agenticScope, 
                new ActCurrentStepState(step.skillId(), step.name()));
            ActInputBundleState.STATE.write(agenticScope, 
                new ActInputBundleState(
                    step.stepGoal(),
                    step.skillId(),
                    step.description(),
                    step.keywords(),
                    step.skillRoot()));

            // Execute skill
            Map<String, Object> invocationInputs = Map.of(
                    "goal", step.stepGoal(),
                    "skillId", step.skillId(),
                    "description", step.description(),
                    "keywords", step.keywords(),
                    "skillRoot", step.skillRoot().toString());
            
            SkillRuntime.ExecutionResult executionResult = invokeSkillTool.invoke(step.skillId(), invocationInputs);
            Object outputValue = executionResult.outputs();
            
            // Store output in AgenticScope and shared outputs map
            ActState.STATE.write(agenticScope, new ActState(step.skillId(), outputValue));
            sharedOutputs.put(step.skillId(), outputValue);
            
            logger.info("Skill {} completed with outputs {}", step.skillId(), executionResult.outputs());
        }
    }

    /**
     * Assembles the final ActResult from the shared outputs collected during execution.
     * Each skill agent writes its output to the shared map, which is then used to build the result.
     */
    private ActResult assembleActResult(AgenticScope scope, PlanModels.PlanResult plan, Map<String, Object> skillOutputs) {
        List<String> invokedSkills = new ArrayList<>();
        Path lastArtifact = null;

        // Collect results from shared outputs map
        for (PlanModels.PlanStep step : plan.steps()) {
            String skillId = step.skillId();
            invokedSkills.add(skillId);
            
            // Check for artifact path in the last skill's output
            Object output = skillOutputs.get(skillId);
            if (output instanceof Map<?, ?> outputMap) {
                Object artifactPath = outputMap.get("artifactPath");
                if (artifactPath != null) {
                    lastArtifact = Path.of(artifactPath.toString());
                }
            }
        }

        return new ActResult(List.copyOf(invokedSkills), Map.copyOf(skillOutputs), lastArtifact);
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
