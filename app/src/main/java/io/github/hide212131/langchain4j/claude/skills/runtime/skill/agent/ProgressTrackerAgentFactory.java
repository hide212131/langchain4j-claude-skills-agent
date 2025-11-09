package io.github.hide212131.langchain4j.claude.skills.runtime.skill.agent;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.model.chat.ChatModel;
import java.util.Objects;

/**
 * Factory for creating ProgressTrackerAgent instances.
 */
public final class ProgressTrackerAgentFactory {
    
    private final ChatModel chatModel;
    
    public ProgressTrackerAgentFactory(ChatModel chatModel) {
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
    }
    
    /**
     * Creates a ProgressTrackerAgent instance using the declarative agent builder.
     * 
     * @return a configured ProgressTrackerAgent
     */
    public ProgressTrackerAgent create() {
        return AgenticServices.agentBuilder(ProgressTrackerAgent.class)
                .chatModel(chatModel)
                .build();
    }
}
