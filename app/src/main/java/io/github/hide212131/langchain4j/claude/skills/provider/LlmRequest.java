package io.github.hide212131.langchain4j.claude.skills.provider;

import java.util.List;
import java.util.Objects;

public record LlmRequest(List<ChatMessage> messages, List<ToolSpecification> tools) {

    public LlmRequest(List<ChatMessage> messages, List<ToolSpecification> tools) {
        this.messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        this.tools = List.copyOf(Objects.requireNonNull(tools, "tools"));
    }
}
