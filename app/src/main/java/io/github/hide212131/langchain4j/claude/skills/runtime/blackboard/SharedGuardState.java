package io.github.hide212131.langchain4j.claude.skills.runtime.blackboard;

import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.AgentStateKey;
import java.util.Objects;

public record SharedGuardState(Object value) {

    public static final String KEY = "shared.guardState";
    public static final AgentStateKey<SharedGuardState> STATE = AgentStateKey.of(KEY, SharedGuardState.class);

    public SharedGuardState {
        Objects.requireNonNull(value, "value");
    }
}
