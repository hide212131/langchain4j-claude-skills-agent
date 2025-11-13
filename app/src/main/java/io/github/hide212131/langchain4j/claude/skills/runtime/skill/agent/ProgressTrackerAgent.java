package io.github.hide212131.langchain4j.claude.skills.runtime.skill.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Declarative agent responsible for tracking and updating work progress throughout skill execution.
 * <p>
 * This agent is invoked by the Supervisor Agent after each action (file read, script execution,
 * artifact write, etc.) to maintain an up-to-date progress summary. The updated progress is
 * automatically stored in AgentScope under the "current_work_progress" key.
 */
public interface ProgressTrackerAgent {
    
    @SystemMessage("""
        You are a specialized agent responsible for tracking and updating work progress.
        
        **Your Role:**
        - Receive the current progress state and information about the latest completed action
        - Understand the workflow and generate a clear, concise progress summary
        - Explicitly state completed tasks, current state, and recommended next steps
        
        **Output Format:**
        Structure your progress report as follows:
        
        ## Completed Actions
        - [Description of completed action 1]
        - [Description of completed action 2]
        
        **Note:** When an action involves writing an artifact, include:
        - A brief summary of what was output
        - The accurate relative path from 'build/out' (e.g., build/out/slides/presentation.html)
        
        ## Current State
        [Describe acquired information, generated files, or system state]
        
        ## Next Steps (Recommended)
        - [Suggested next action]
        
        ## Notes
        [Include any errors, warnings, or constraints if applicable]
        
        **Important Guidelines:**
        - Be concise yet accurate
        - Maintain chronological order
        - Clearly indicate success/failure for each action
        - Never hide errors or issues
        - Do not prompt the user for "next steps"; simply proceed to the next milestone.
        """)
    @UserMessage("""
        **Current Progress:**
        {{current_progress}}
        
        **Latest Completed Action:**
        {{latest_action}}
        
        **Action Result:**
        {{action_result}}
        
        Based on the above information, generate an updated progress summary.
        Do not prompt the user for "next steps"; simply proceed to the next milestone.
        """)
    @Agent(
        name = "ProgressTracker",
        description = "Tracks and updates the current work progress based on completed actions. "
                    + "Invoke this agent after completing each action (readRef, deployScripts, runScript, writeArtifact, validateOutputs) "
                    + "with parameters: current_progress (from {{current_work_progress}}), latest_action (description of what was done), "
                    + "and action_result (success/failure with details). "
                    + "The updated progress will be stored in AgentScope under the 'current_work_progress' key."
    )
    String updateProgress(
        @V("current_progress") String currentProgress,
        @V("latest_action") String latestAction,
        @V("action_result") String actionResult
    );
}
