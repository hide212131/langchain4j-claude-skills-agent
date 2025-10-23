package io.github.hide212131.langchain4j.claude.skills.index;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Aggregated metadata for a single skill, as produced by {@link SkillIndexLoader}.
 */
public record SkillIndexEntry(
        String skillId,
        String name,
        String description,
        Optional<String> version,
        List<SkillIO> inputs,
        List<SkillIO> outputs,
        List<String> keywords,
        List<SkillStage> stages,
        List<String> resourceFiles,
        List<String> scriptFiles,
        String l1Summary) {

    public SkillIndexEntry {
        skillId = Objects.requireNonNull(skillId, "skillId");
        name = Objects.requireNonNull(name, "name");
        description = Objects.requireNonNull(description, "description");
        version = version == null ? Optional.empty() : version;
        inputs = List.copyOf(inputs == null ? List.of() : inputs);
        outputs = List.copyOf(outputs == null ? List.of() : outputs);
        keywords = List.copyOf(keywords == null ? List.of() : keywords);
        stages = List.copyOf(stages == null ? List.of() : stages);
        resourceFiles = List.copyOf(resourceFiles == null ? List.of() : resourceFiles);
        scriptFiles = List.copyOf(scriptFiles == null ? List.of() : scriptFiles);
        l1Summary = Objects.requireNonNull(l1Summary, "l1Summary");
    }
}
