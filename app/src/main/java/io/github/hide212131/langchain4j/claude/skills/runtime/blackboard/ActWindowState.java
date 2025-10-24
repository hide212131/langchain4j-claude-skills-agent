package io.github.hide212131.langchain4j.claude.skills.runtime.blackboard;

import java.util.Objects;

public record ActWindowState(Object value) {

    public static final String KEY = "act.windowState";

    public ActWindowState {
        Objects.requireNonNull(value, "value");
    }
}
