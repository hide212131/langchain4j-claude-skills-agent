package io.github.hide212131.langchain4j.claude.skills.runtime.blackboard;

import java.util.Objects;

public record SharedContextSnapshotState(Object value) {

    public static final String KEY = "shared.contextSnapshot";

    public SharedContextSnapshotState {
        Objects.requireNonNull(value, "value");
    }
}
