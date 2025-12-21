package io.github.hide212131.langchain4j.claude.skills.runtime.visibility;

/** トークン使用量。 */
public record TokenUsage(Long inputTokens, Long outputTokens, Long totalTokens) implements VisibilityPayload {
}
