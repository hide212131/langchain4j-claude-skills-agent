package io.github.hide212131.langchain4j.claude.skills.runtime;

import java.util.Objects;

/**
 * SKILL.md を構造化した最小 POJO。
 */
public record SkillDocument(String id, String name, String description, String body) {

    public SkillDocument {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(body, "body");
    }
}
