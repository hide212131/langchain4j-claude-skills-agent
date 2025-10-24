package io.github.hide212131.langchain4j.claude.skills.runtime.blackboard;

import java.util.Objects;

public record ActInputBundleState(Object value) {

    public static final String KEY = "act.inputBundle";

    public ActInputBundleState {
        Objects.requireNonNull(value, "value");
    }
}
