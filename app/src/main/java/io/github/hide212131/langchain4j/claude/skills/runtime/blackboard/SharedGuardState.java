package io.github.hide212131.langchain4j.claude.skills.runtime.blackboard;

import java.util.Objects;

public record SharedGuardState(Object value) {

    public static final String KEY = "shared.guardState";

    public SharedGuardState {
        Objects.requireNonNull(value, "value");
    }
}
