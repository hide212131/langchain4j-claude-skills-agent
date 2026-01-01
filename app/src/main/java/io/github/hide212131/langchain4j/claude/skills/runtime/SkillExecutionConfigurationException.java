package io.github.hide212131.langchain4j.claude.skills.runtime;

/**
 * 実行に必要な設定の読み込みに失敗した場合の例外。
 */
public class SkillExecutionConfigurationException extends RuntimeException {

    public SkillExecutionConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
