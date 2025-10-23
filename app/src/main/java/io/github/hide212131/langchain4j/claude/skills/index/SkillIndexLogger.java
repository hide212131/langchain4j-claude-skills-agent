package io.github.hide212131.langchain4j.claude.skills.index;

/**
 * Minimal logging abstraction used by {@link SkillIndexLoader} so callers can capture warnings in tests
 * and route them to their preferred logging framework in production.
 */
@FunctionalInterface
public interface SkillIndexLogger {

    SkillIndexLogger NO_OP = (skillId, message) -> {
        // intentionally empty
    };

    /**
     * Records a warning associated with a specific skill identifier.
     *
     * @param skillId the logical identifier of the skill (relative path in the skills tree)
     * @param message the warning message to log
     */
    void warn(String skillId, String message);
}
