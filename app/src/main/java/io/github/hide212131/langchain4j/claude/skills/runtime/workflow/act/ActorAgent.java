package io.github.hide212131.langchain4j.claude.skills.runtime.workflow.act;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agentic.Agent;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.plan.PlanModels;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.act.DefaultInvoker.ActResult;

/**
 * Declarative Agent for skill execution using LangChain4j Agentic Framework.
 *
 * Replaces the imperative DefaultInvoker approach with a declarative @Agent interface
 * using @Tool annotations.
 *
 * This interface defines the contract for skill execution agents.
 * Implementation is dynamically created via AgenticServices.agentBuilder().
 *
 * The agent's primary role is to:
 * 1. Receive a plan with ordered skills
 * 2. Execute skills sequentially
 * 3. Track outputs and handle errors
 * 4. Return an ActResult with execution summary
 */
public interface ActorAgent {

    /**
     * Execute skills according to the provided plan.
     * Runs each skill in the plan sequentially and collects outputs.
     *
     * This is the main entry point method marked with @Agent.
     * The LLM uses available tools to monitor and control skill execution.
     *
     * @param plan The plan containing ordered skills to execute
     * @return ActResult with execution summary and outputs
     */
    @Agent(name = "actor")
    ActResult act(PlanModels.PlanResult plan);

    /**
     * Tool: Get the next skill to execute from the plan.
     * Returns the skill ID of the next unexecuted skill in the plan sequence.
     *
     * @param plan The current plan
     * @param executedSkillIds Comma-separated list of already executed skill IDs
     * @return The next skill ID to execute, or "COMPLETE" if all skills are done
     */
    @Tool(name = "getNextSkill")
    String getNextSkill(PlanModels.PlanResult plan, String executedSkillIds);

    /**
     * Tool: Execute a specific skill and capture its output.
     * Runs the skill and returns the execution result.
     *
     * @param skillId The skill ID to execute
     * @return SkillExecutionResult with status and output
     */
    @Tool(name = "executeSkill")
    SkillExecutionResult executeSkill(String skillId);

    /**
     * Tool: Summarize the execution results.
     * Creates a summary of all executed skills and their outputs.
     *
     * @param executionResults Map of skill ID to execution results
     * @return ActResult with final summary
     */
    @Tool(name = "summarizeExecution")
    ActResult summarizeExecution(String executionResults);

    /**
     * Represents the result of executing a single skill.
     */
    record SkillExecutionResult(
            String skillId,
            boolean success,
            String output,
            String errorMessage) {
    }
}
