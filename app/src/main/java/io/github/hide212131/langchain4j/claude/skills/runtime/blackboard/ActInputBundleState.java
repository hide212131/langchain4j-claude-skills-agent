package io.github.hide212131.langchain4j.claude.skills.runtime.blackboard;

import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.AgentStateKey;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record ActInputBundleState(
        String goal,
        String skillId,
        String description,
        List<String> keywords,
        Path skillRoot) {

    public static final String KEY = "act.inputBundle";
    public static final AgentStateKey<ActInputBundleState> STATE =
            AgentStateKey.of(KEY, ActInputBundleState.class);

    public ActInputBundleState {
        Objects.requireNonNull(goal, "goal");
        if (skillId == null || skillId.isBlank()) {
            throw new IllegalArgumentException("skillId must be provided");
        }
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(keywords, "keywords");
        keywords = List.copyOf(keywords);
        Objects.requireNonNull(skillRoot, "skillRoot");
    }
}
