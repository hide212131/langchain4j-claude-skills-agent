package io.github.hide212131.langchain4j.claude.skills.index;

import java.util.Objects;

/**
 * Describes an input or output declaration from a SKILL.md front matter section.
 */
public record SkillIO(String id, String type, boolean required, String description) {

    public SkillIO {
        id = Objects.requireNonNull(id, "id");
        type = Objects.requireNonNull(type, "type");
        description = description == null ? "" : description;
    }
}
