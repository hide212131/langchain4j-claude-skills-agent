package io.github.hide212131.langchain4j.claude.skills.runtime.visibility;

import java.util.Objects;

/** AgenticScope 状態や決定内容を可視化するペイロード。 */
public record AgentStatePayload(String goal, String decision, String stateSummary) implements VisibilityPayload {

    public AgentStatePayload {
        Objects.requireNonNull(decision, "decision");
    }
}
