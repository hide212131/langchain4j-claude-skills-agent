package io.github.hide212131.langchain4j.claude.skills.runtime.visibility;

import java.util.Objects;

/** 可視化イベントの共通形式。 */
public record SkillEvent(SkillEventType type, SkillEventMetadata metadata, SkillPayload payload) {

    public SkillEvent {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(payload, "payload");
    }
}
