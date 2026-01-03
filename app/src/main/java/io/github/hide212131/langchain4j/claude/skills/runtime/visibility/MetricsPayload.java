package io.github.hide212131.langchain4j.claude.skills.runtime.visibility;

/** トークン数やレイテンシなどのメトリクスを可視化するペイロード。 */
public record MetricsPayload(Long inputTokens, Long outputTokens, Long latencyMillis, Integer retryCount)
        implements SkillPayload {
}
