package io.github.hide212131.langchain4j.claude.skills.runtime.workflow.act;

import io.github.hide212131.langchain4j.claude.skills.infra.logging.WorkflowLogger;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.plan.PlanModels;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.act.DefaultInvoker.ActResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default implementation of ActorAgent using the declarative Agentic Framework.
 *
 * This class provides the @Tool implementations for skill execution.
 * It acts as a bridge between the declarative ActorAgent interface and the existing
 * DefaultInvoker infrastructure.
 *
 * The actual skill execution is delegated to DefaultInvoker, which manages
 * the dynamic sequence of skill execution with budgets and guards.
 */
public class DefaultActorAgent implements ActorAgent {

    private final WorkflowLogger logger;
    private final Map<String, SkillExecutionResult> executionResults = new LinkedHashMap<>();

    public DefaultActorAgent(WorkflowLogger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public ActResult act(PlanModels.PlanResult plan) {
        // This method serves as the @Agent entry point.
        // The actual execution happens through the @Tool methods below.
        // The Agentic Framework will invoke the tools as needed.

        List<String> skillIds = plan.orderedSkillIds();

        if (skillIds.isEmpty()) {
            logger.warn("Actor received empty skill list");
            return new ActResult(List.of(), Map.of(), null);
        }

        logger.info("Actor starting execution of {} skills", skillIds.size());

        // Reset execution tracking
        executionResults.clear();

        // The actual tool invocation happens in the Agentic Framework
        // This is a placeholder that will be overridden by the framework's agent proxy
        return new ActResult(List.of(), Map.of(), null);
    }

    @Override
    public String getNextSkill(PlanModels.PlanResult plan, String executedSkillIds) {
        List<String> allSkillIds = plan.orderedSkillIds();

        // Parse executed skill IDs
        Set<String> executed = parseExecutedSkillIds(executedSkillIds);

        // Find the first not-yet-executed skill
        for (String skillId : allSkillIds) {
            if (!executed.contains(skillId)) {
                logger.debug("Next skill to execute: {}", skillId);
                return skillId;
            }
        }

        logger.info("All skills executed");
        return "COMPLETE";
    }

    @Override
    public SkillExecutionResult executeSkill(String skillId) {
        try {
            logger.info("Executing skill: {}", skillId);

            // In a real scenario, this would delegate to SkillRuntime
            // For now, we simulate the execution
            String output = "Skill " + skillId + " executed";
            SkillExecutionResult result = new SkillExecutionResult(skillId, true, output, null);

            executionResults.put(skillId, result);
            logger.info("Skill {} completed successfully", skillId);

            return result;
        } catch (Exception ex) {
            logger.warn("Skill {} execution failed: {}", skillId, ex.getMessage());
            SkillExecutionResult result = new SkillExecutionResult(skillId, false, null, ex.getMessage());
            executionResults.put(skillId, result);
            return result;
        }
    }

    @Override
    public ActResult summarizeExecution(String executionResults) {
        // Aggregate all execution results into a final ActResult
        List<String> invokedSkills = new ArrayList<>();
        Map<String, Object> outputs = new LinkedHashMap<>();

        for (String skillId : this.executionResults.keySet()) {
            SkillExecutionResult result = this.executionResults.get(skillId);
            if (result.success()) {
                invokedSkills.add(skillId);
                outputs.put(skillId, result.output());
            }
        }

        logger.info("Actor completed with {} successful executions", invokedSkills.size());

        return new ActResult(invokedSkills, outputs, null);
    }

    private Set<String> parseExecutedSkillIds(String executedSkillIds) {
        if (executedSkillIds == null || executedSkillIds.isBlank()) {
            return new java.util.HashSet<>();
        }

        return java.util.Arrays.stream(executedSkillIds.split(","))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .collect(Collectors.toSet());
    }
}
