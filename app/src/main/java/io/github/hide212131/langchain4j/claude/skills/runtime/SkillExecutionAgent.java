package io.github.hide212131.langchain4j.claude.skills.runtime;

/**
 * スキル実行エージェントの公開インターフェース。
 */
public interface SkillExecutionAgent {

    SkillExecutionResult execute(SkillExecutionRequest request);
}
