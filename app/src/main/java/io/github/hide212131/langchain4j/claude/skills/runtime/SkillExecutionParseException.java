package io.github.hide212131.langchain4j.claude.skills.runtime;

/**
 * SKILL.md の解析に失敗した場合の例外。
 */
public class SkillExecutionParseException extends RuntimeException {

    public SkillExecutionParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
