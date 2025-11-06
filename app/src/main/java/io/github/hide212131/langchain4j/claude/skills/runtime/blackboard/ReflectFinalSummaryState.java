package io.github.hide212131.langchain4j.claude.skills.runtime.blackboard;

import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.AgentStateKey;
import java.util.Objects;

public record ReflectFinalSummaryState(String value) {

    public static final String KEY = "reflect.finalSummary";
    public static final AgentStateKey<ReflectFinalSummaryState> STATE =
            AgentStateKey.of(KEY, ReflectFinalSummaryState.class);

    public ReflectFinalSummaryState {
        Objects.requireNonNull(value, "value");
    }
}
