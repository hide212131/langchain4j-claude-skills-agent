package io.github.hide212131.langchain4j.claude.skills.runtime.blackboard;

import java.util.Objects;

public record ActState(String skillId, Object output) {

    public ActState {
        if (skillId == null || skillId.isBlank()) {
            throw new IllegalArgumentException("skillId must be provided");
        }
        Objects.requireNonNull(output, "output");
    }

    public static String outputKey(String skillId) {
        if (skillId == null || skillId.isBlank()) {
            throw new IllegalArgumentException("skillId must be provided");
        }
        return "act.output." + skillId;
    }
}
