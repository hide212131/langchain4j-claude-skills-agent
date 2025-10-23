package io.github.hide212131.langchain4j.claude.skills.index;

import java.util.List;
import java.util.Objects;

/**
 * Represents a stage definition from a SKILL.md front matter.
 */
public record SkillStage(String id, String purpose, List<String> resources, List<String> scripts) {

    public SkillStage {
        id = Objects.requireNonNull(id, "id");
        purpose = Objects.requireNonNull(purpose, "purpose");
        resources = List.copyOf(resources == null ? List.of() : resources);
        scripts = List.copyOf(scripts == null ? List.of() : scripts);
    }
}
