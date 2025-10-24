package io.github.hide212131.langchain4j.claude.skills.provider;

import java.util.Map;
import java.util.Objects;

public record ToolDefinition(String name, String description, Map<String, Object> parameters) {

    public ToolDefinition(String name, String description, Map<String, Object> parameters) {
        this.name = Objects.requireNonNull(name, "name");
        this.description = Objects.requireNonNull(description, "description");
        this.parameters = Map.copyOf(Objects.requireNonNull(parameters, "parameters"));
    }
}
