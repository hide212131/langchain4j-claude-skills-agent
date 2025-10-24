package io.github.hide212131.langchain4j.claude.skills.provider;

import java.util.List;
import java.util.Objects;

public record ChatInteraction(List<ChatMessage> messages, List<ToolDefinition> tools) {

    public ChatInteraction(List<ChatMessage> messages, List<ToolDefinition> tools) {
        this.messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        this.tools = List.copyOf(Objects.requireNonNull(tools, "tools"));
    }
}
