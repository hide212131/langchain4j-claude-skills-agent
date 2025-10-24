package io.github.hide212131.langchain4j.claude.skills.runtime.skill;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Placeholder for the skills index that will eventually hold parsed SKILL.md metadata.
 */
public final class SkillIndex {

    private final Map<String, SkillMetadata> skills;

    public SkillIndex() {
        this(Collections.emptyMap());
    }

    public SkillIndex(Map<String, SkillMetadata> skills) {
        this.skills = Collections.unmodifiableMap(skills);
    }

    public Map<String, SkillMetadata> skills() {
        return skills;
    }

    public Optional<SkillMetadata> find(String id) {
        return Optional.ofNullable(skills.get(id));
    }

    public record SkillMetadata(
            String id, String name, String description, List<String> keywords, List<String> warnings) {}
}
