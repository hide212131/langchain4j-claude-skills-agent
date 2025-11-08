package io.github.hide212131.langchain4j.claude.skills.runtime.blackboard;

import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.AgentStateKey;

public record ReflectRetryAdviceState(String advice) {

    public static final String KEY = "reflect.retryAdvice";
    public static final AgentStateKey<ReflectRetryAdviceState> STATE =
            AgentStateKey.of(KEY, ReflectRetryAdviceState.class);

    public ReflectRetryAdviceState {
        if (advice == null) {
            advice = "";
        }
    }
}
