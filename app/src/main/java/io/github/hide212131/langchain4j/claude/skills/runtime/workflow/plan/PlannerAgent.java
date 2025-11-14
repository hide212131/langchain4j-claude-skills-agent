package io.github.hide212131.langchain4j.claude.skills.runtime.workflow.plan;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agentic.Agent;
import io.github.hide212131.langchain4j.claude.skills.runtime.skill.SkillIndex;

/**
 * Declarative Agent for skill planning using LangChain4j Agentic Framework.
 *
 * Replaces the imperative llmClient.complete() approach in DynamicPlanOperator
 * with a declarative @Agent interface using @Tool annotations.
 *
 * This interface defines the contract for skill planning agents.
 * Implementation is dynamically created via AgenticServices.agentBuilder().
 *
 * The agent's primary role is to:
 * 1. Present available skills to the LLM
 * 2. Allow the LLM to select and order skills based on user goal
 * 3. Return a PlanResult with the selected skills
 */
public interface PlannerAgent {

    /**
     * Plan skill execution order based on user goal.
     * Uses LLM to select and order skills from the available skill index.
     *
     * This is the main entry point method marked with @Agent.
     * The LLM uses the available tools to understand skills and make intelligent selections.
     *
     * @param goal User's goal in Japanese or English
     * @param skillIndex Available skills to select from
     * @return PlanResult with ordered skills or empty plan if no suitable skills found
     */
    @Agent(name = "planner")
    PlanModels.PlanResult planSkills(String goal, SkillIndex skillIndex);

    /**
     * Tool: Present available skills to the LLM for selection.
     * Returns a formatted list of skill candidates that the LLM can choose from.
     *
     * This tool provides the skill catalog to the LLM so it can make informed selections.
     *
     * @param skillIndex The skill index containing all available skills
     * @return Formatted string representation of available skills with metadata
     */
    @Tool(name = "listAvailableSkills")
    String listAvailableSkills(SkillIndex skillIndex);

    /**
     * Tool: Execute skill selection based on LLM decision.
     * Takes the LLM's selection and creates a PlanResult with ordered skills.
     *
     * This tool processes the LLM's selection decision and materializes it into a plan.
     *
     * @param goal The user's goal
     * @param skillIndex Available skills
     * @param selectedSkillIds Skill IDs selected by the LLM (can be JSON or comma-separated)
     * @return PlanResult with the selected and ordered skills
     */
    @Tool(name = "executeSkillSelection")
    PlanModels.PlanResult executeSkillSelection(String goal, SkillIndex skillIndex, String selectedSkillIds);
}
