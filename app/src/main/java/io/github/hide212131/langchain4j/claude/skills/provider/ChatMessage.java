package io.github.hide212131.langchain4j.claude.skills.provider;

import java.util.Objects;

public record ChatMessage(Role role, String content) {

    public ChatMessage {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(content, "content");
    }

    public static ChatMessage system(String content) {
        return new ChatMessage(Role.SYSTEM, content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage(Role.USER, content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(Role.ASSISTANT, content);
    }

    public static ChatMessage tool(String content) {
        return new ChatMessage(Role.TOOL, content);
    }

    public enum Role {
        SYSTEM,
        USER,
        ASSISTANT,
        TOOL
    }
}
