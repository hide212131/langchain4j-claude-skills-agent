package io.github.hide212131.langchain4j.claude.skills.runtime.blackboard;

import java.util.Objects;

public record ReflectFinalSummaryState(String value) {

    public static final String KEY = "reflect.finalSummary";

    public ReflectFinalSummaryState {
        Objects.requireNonNull(value, "value");
    }
}
