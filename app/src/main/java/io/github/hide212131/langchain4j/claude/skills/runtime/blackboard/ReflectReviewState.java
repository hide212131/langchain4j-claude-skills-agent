package io.github.hide212131.langchain4j.claude.skills.runtime.blackboard;

import java.util.Objects;

public record ReflectReviewState(Object value) {

    public static final String KEY = "reflect.review";

    public ReflectReviewState {
        Objects.requireNonNull(value, "value");
    }
}
