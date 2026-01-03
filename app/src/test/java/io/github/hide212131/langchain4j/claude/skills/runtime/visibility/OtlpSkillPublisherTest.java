package io.github.hide212131.langchain4j.claude.skills.runtime.visibility;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@SuppressWarnings("PMD.JUnitTestContainsTooManyAsserts")
class OtlpSkillPublisherTest {

    private static final String RUN_ID = "run-1";
    private static final String SKILL_ID = "skill-1";
    private static final String PLAN_PROMPT = "plan.prompt";
    private static final String SKILLS_RUN = "skills.run";

    @SuppressWarnings("PMD.UnnecessaryConstructor")
    OtlpSkillPublisherTest() {
        // default
    }

    @Test
    @DisplayName("スキルイベントを Span 属性にマッピングしてエクスポートする")
    void publishMapsAttributes() {
        List<SpanData> spansCopy;
        try (InMemorySpanExporter exporter = InMemorySpanExporter.create();
                OtlpSkillPublisher publisher = new OtlpSkillPublisher(exporter)) {
            SkillEventMetadata metadata = new SkillEventMetadata(RUN_ID, SKILL_ID, "plan", "plan.prompt",
                    Instant.ofEpochMilli(0));
            PromptPayload payload = new PromptPayload("prompt-text", "resp", "gpt", "assistant",
                    new TokenUsage(10L, 5L, 15L));
            publisher.publish(new SkillEvent(SkillEventType.PROMPT, metadata, payload));

            spansCopy = List.copyOf(exporter.getFinishedSpanItems());
        }

        assertThat(spansCopy).hasSize(2);
        SpanData span = spansCopy.stream().filter(s -> PLAN_PROMPT.equals(s.getName())).findFirst().orElseThrow();
        assertThat(span.getName()).isEqualTo(PLAN_PROMPT);
        assertThat(span.getAttributes().get(AttributeKey.stringKey("skill.run_id"))).isEqualTo(RUN_ID);
        assertThat(span.getAttributes().get(AttributeKey.stringKey("skill.prompt.role"))).isEqualTo("assistant");
        assertThat(span.getAttributes().get(AttributeKey.stringKey("gen_ai.request.prompt"))).isEqualTo("prompt-text");
        assertThat(span.getAttributes().get(AttributeKey.stringKey("gen_ai.response.text"))).isEqualTo("resp");
        assertThat(span.getAttributes().get(AttributeKey.longKey("gen_ai.usage.total_tokens"))).isEqualTo(15L);
        assertThat(span.getAttributes().get(AttributeKey.stringKey("skill.prompt.content"))).isNull();
        assertThat(span.getAttributes().get(AttributeKey.longKey("skill.usage.total_tokens"))).isNull();
    }

    @Test
    @DisplayName("同一 runId のイベントは同一 traceId に束ねられる（1 run 1 trace）")
    void sameRunIdSharesTraceId() {
        List<SpanData> spansCopy;
        try (InMemorySpanExporter exporter = InMemorySpanExporter.create();
                OtlpSkillPublisher publisher = new OtlpSkillPublisher(exporter)) {
            SkillEventMetadata plan = new SkillEventMetadata(RUN_ID, SKILL_ID, "plan", "plan.prompt",
                    Instant.ofEpochMilli(0));
            publisher.publish(new SkillEvent(SkillEventType.PROMPT, plan,
                    new PromptPayload("p", "r", "gpt", "assistant", null)));

            SkillEventMetadata act = new SkillEventMetadata(RUN_ID, SKILL_ID, "act", "act.call",
                    Instant.ofEpochMilli(1));
            publisher.publish(new SkillEvent(SkillEventType.AGENT_STATE, act,
                    new AgentStatePayload("goal", "decision", "state")));

            // root span は publisher.close() で end されるため、ここではイベントspan同士の関係のみ検証する。
            spansCopy = List.copyOf(exporter.getFinishedSpanItems());
        }

        // root span (skills.run) + event spans (2)
        assertThat(spansCopy).hasSize(3);
        assertThat(spansCopy.stream().map(SpanData::getTraceId).distinct()).hasSize(1);
        assertThat(spansCopy.stream().filter(s -> SKILLS_RUN.equals(s.getName())).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("メトリクスとエラーのペイロードを OTLP 属性とステータスに変換する")
    void publishMetricsAndErrorAttributes() {
        List<SpanData> spansCopy;
        try (InMemorySpanExporter exporter = InMemorySpanExporter.create();
                OtlpSkillPublisher publisher = new OtlpSkillPublisher(exporter)) {
            SkillEventMetadata metrics = new SkillEventMetadata(RUN_ID, SKILL_ID, "metrics", "workflow.done",
                    Instant.ofEpochMilli(2));
            publisher.publish(
                    new SkillEvent(SkillEventType.METRICS, metrics, new MetricsPayload(5L, 3L, 42L, 1)));

            SkillEventMetadata error = new SkillEventMetadata(RUN_ID, SKILL_ID, "error", "run.failed",
                    Instant.ofEpochMilli(3));
            publisher.publish(new SkillEvent(SkillEventType.ERROR, error,
                    new ErrorPayload("失敗しました", "IllegalStateException")));

            spansCopy = List.copyOf(exporter.getFinishedSpanItems());
        }

        SpanData metricsSpan = spansCopy.stream().filter(span -> "workflow.done".equals(span.getName())).findFirst()
                .orElseThrow();
        assertThat(metricsSpan.getAttributes().get(AttributeKey.longKey("skill.metrics.latency_ms")))
                .isEqualTo(42L);
        assertThat(metricsSpan.getAttributes().get(AttributeKey.longKey("skill.metrics.retry_count")))
                .isEqualTo(1L);
        assertThat(metricsSpan.getAttributes().get(AttributeKey.longKey("gen_ai.usage.input_tokens"))).isEqualTo(5L);
        assertThat(metricsSpan.getAttributes().get(AttributeKey.longKey("gen_ai.usage.output_tokens"))).isEqualTo(3L);

        SpanData errorSpan = spansCopy.stream().filter(span -> "run.failed".equals(span.getName())).findFirst()
                .orElseThrow();
        assertThat(errorSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(errorSpan.getAttributes().get(AttributeKey.stringKey("skill.error.message")))
                .contains("失敗しました");
        assertThat(errorSpan.getAttributes().get(AttributeKey.stringKey("skill.error.type")))
                .isEqualTo("IllegalStateException");
        assertThat(spansCopy.stream().map(SpanData::getTraceId).distinct()).hasSize(1);
    }
}
