package io.github.hide212131.langchain4j.claude.skills.runtime;

import io.github.hide212131.langchain4j.claude.skills.runtime.AgentFlow.AgentFlowResult;
import java.util.List;
import java.util.Objects;

/**
 * スキル実行エージェントの結果。
 */
public record SkillExecutionResult(AgentFlowResult flowResult, String runId, String skillId, List<String> artifacts) {

    public SkillExecutionResult {
        Objects.requireNonNull(flowResult, "flowResult は必須です");
        Objects.requireNonNull(runId, "runId は必須です");
        Objects.requireNonNull(skillId, "skillId は必須です");
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
    }
}
