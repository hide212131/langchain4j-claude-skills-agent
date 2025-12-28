package io.github.hide212131.langchain4j.claude.skills.bundle;

import java.util.List;
import java.util.Objects;

public record SkillDependency(String skillId, List<String> commands, List<String> warnings) {

    public SkillDependency {
        Objects.requireNonNull(skillId, "skillId");
        commands = List.copyOf(Objects.requireNonNull(commands, "commands"));
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
    }
}
