package io.github.hide212131.langchain4j.claude.skills.runtime.blackboard;

import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.AgentStateKey;
import java.util.List;
import java.util.Objects;

public record SharedBlackboardIndexState(List<String> invokedSkillIds) {

    public static final String KEY = "shared.blackboardIndex";
    public static final AgentStateKey<SharedBlackboardIndexState> STATE =
            AgentStateKey.of(KEY, SharedBlackboardIndexState.class);

    public SharedBlackboardIndexState {
        Objects.requireNonNull(invokedSkillIds, "invokedSkillIds");
        invokedSkillIds = List.copyOf(invokedSkillIds);
    }
}
