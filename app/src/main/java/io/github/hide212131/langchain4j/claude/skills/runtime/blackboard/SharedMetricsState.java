package io.github.hide212131.langchain4j.claude.skills.runtime.blackboard;

import java.util.Objects;

public record SharedMetricsState(Object value) {

    public static final String KEY = "shared.metrics";

    public SharedMetricsState {
        Objects.requireNonNull(value, "value");
    }
}
