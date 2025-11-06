package io.github.hide212131.langchain4j.claude.skills.runtime.blackboard;

import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.AgentStateKey;
import java.util.List;
import java.util.Objects;

public record ReflectReviewState(List<String> feedback) {

    public static final String KEY = "reflect.review";
    public static final AgentStateKey<ReflectReviewState> STATE =
            AgentStateKey.of(KEY, ReflectReviewState.class);

    public ReflectReviewState {
        Objects.requireNonNull(feedback, "feedback");
        feedback = List.copyOf(feedback);
    }
}
