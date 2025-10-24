package io.github.hide212131.langchain4j.claude.skills.provider;

import java.util.List;
import java.util.Objects;

public record LlmResponse(
        List<ChatMessage> messages, List<ToolCall> toolCalls, int tokensIn, int tokensOut) {

    public LlmResponse(
            List<ChatMessage> messages, List<ToolCall> toolCalls, int tokensIn, int tokensOut) {
        this.messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        this.toolCalls = List.copyOf(Objects.requireNonNull(toolCalls, "toolCalls"));
        if (tokensIn < 0 || tokensOut < 0) {
            throw new IllegalArgumentException("token counts must be non-negative");
        }
        this.tokensIn = tokensIn;
        this.tokensOut = tokensOut;
    }
}
