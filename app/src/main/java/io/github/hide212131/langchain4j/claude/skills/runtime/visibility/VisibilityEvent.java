package io.github.hide212131.langchain4j.claude.skills.runtime.visibility;

import java.util.Objects;

/** 可視化イベントの共通形式。 */
public record VisibilityEvent(VisibilityEventType type, VisibilityEventMetadata metadata, VisibilityPayload payload) {

    public VisibilityEvent {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(payload, "payload");
    }
}
