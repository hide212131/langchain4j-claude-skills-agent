package io.github.hide212131.langchain4j.claude.skills.runtime.blackboard;

import java.util.Objects;

public record SharedBlackboardIndexState(Object value) {

    public static final String KEY = "shared.blackboardIndex";

    public SharedBlackboardIndexState {
        Objects.requireNonNull(value, "value");
    }
}
