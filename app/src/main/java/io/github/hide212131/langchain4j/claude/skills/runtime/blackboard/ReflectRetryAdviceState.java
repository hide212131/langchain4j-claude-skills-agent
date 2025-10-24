package io.github.hide212131.langchain4j.claude.skills.runtime.blackboard;

import java.util.Objects;

public record ReflectRetryAdviceState(Object value) {

    public static final String KEY = "reflect.retryAdvice";

    public ReflectRetryAdviceState {
        Objects.requireNonNull(value, "value");
    }
}
