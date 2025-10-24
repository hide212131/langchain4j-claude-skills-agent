package io.github.hide212131.langchain4j.claude.skills.provider;

import java.util.Objects;

public record ToolCall(String id, String name, String argumentsJson) {

    public ToolCall {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(argumentsJson, "argumentsJson");
    }
}
