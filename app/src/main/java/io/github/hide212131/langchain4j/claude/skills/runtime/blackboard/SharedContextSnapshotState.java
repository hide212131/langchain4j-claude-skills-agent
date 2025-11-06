package io.github.hide212131.langchain4j.claude.skills.runtime.blackboard;

import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.AgentStateKey;
import java.util.Objects;

public record SharedContextSnapshotState(Object value) {

    public static final String KEY = "shared.contextSnapshot";
    public static final AgentStateKey<SharedContextSnapshotState> STATE =
            AgentStateKey.of(KEY, SharedContextSnapshotState.class);

    public SharedContextSnapshotState {
        Objects.requireNonNull(value, "value");
    }
}
