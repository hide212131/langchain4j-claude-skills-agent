package io.github.hide212131.langchain4j.claude.skills.runtime.blackboard;

import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.AgentStateKey;
import java.util.Objects;

public record SharedMetricsState(Object value) {

    public static final String KEY = "shared.metrics";
    public static final AgentStateKey<SharedMetricsState> STATE =
            AgentStateKey.of(KEY, SharedMetricsState.class);

    public SharedMetricsState {
        Objects.requireNonNull(value, "value");
    }
}
