package io.github.hide212131.langchain4j.claude.skills.runtime;

import io.github.hide212131.langchain4j.claude.skills.runtime.execution.ExecutionBackend;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.VisibilityEventPublisher;
import java.nio.file.Path;
import java.util.Objects;

/**
 * スキル実行エージェントへの入力。
 */
public record SkillExecutionRequest(Path skillMdPath, String goal, String skillId, String runId,
        ExecutionBackend executionBackend, LlmProvider llmProvider, Path artifactsDir, VisibilityLevel visibilityLevel,
        VisibilityEventPublisher events, VisibilityLog log) {

    public SkillExecutionRequest {
        Objects.requireNonNull(skillMdPath, "skillMdPath は必須です");
        Objects.requireNonNull(executionBackend, "executionBackend は必須です");
        Objects.requireNonNull(visibilityLevel, "visibilityLevel は必須です");
        Objects.requireNonNull(events, "events は必須です");
        Objects.requireNonNull(log, "log は必須です");
    }
}
