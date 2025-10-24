package io.github.hide212131.langchain4j.claude.skills.runtime.human;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.workflow.HumanInTheLoop;

/**
 * Provides access to LangChain4j's Human-in-the-loop builder.
 */
public final class HumanReviewAgentFactory {

    public HumanInTheLoop.HumanInTheLoopBuilder createBuilder() {
        return AgenticServices.humanInTheLoopBuilder();
    }
}
