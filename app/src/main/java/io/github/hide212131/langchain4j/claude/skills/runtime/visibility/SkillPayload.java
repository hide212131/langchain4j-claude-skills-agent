package io.github.hide212131.langchain4j.claude.skills.runtime.visibility;

/** 各イベントのペイロードが実装するマーカー。 */
public sealed interface SkillPayload permits ParsePayload, PromptPayload, AgentStatePayload, MetricsPayload,
        ErrorPayload, TokenUsage, InputPayload, OutputPayload {
}
