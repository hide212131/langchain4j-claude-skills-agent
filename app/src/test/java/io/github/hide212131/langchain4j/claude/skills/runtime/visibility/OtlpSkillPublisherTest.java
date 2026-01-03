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
    private static final String PHASE_PLAN = "plan";
    private static final String PHASE_ACT = "act";
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
            SkillEventMetadata metadata = new SkillEventMetadata(RUN_ID, SKILL_ID, PHASE_PLAN, "plan.prompt",
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
            SkillEventMetadata plan = new SkillEventMetadata(RUN_ID, SKILL_ID, PHASE_PLAN, "plan.prompt",
                    Instant.ofEpochMilli(0));
            publisher.publish(
                    new SkillEvent(SkillEventType.PROMPT, plan, new PromptPayload("p", "r", "gpt", "assistant", null)));

            SkillEventMetadata act = new SkillEventMetadata(RUN_ID, SKILL_ID, PHASE_ACT, "act.call",
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
            publisher.publish(new SkillEvent(SkillEventType.METRICS, metrics, new MetricsPayload(5L, 3L, 42L, 1)));

            SkillEventMetadata error = new SkillEventMetadata(RUN_ID, SKILL_ID, "error", "run.failed",
                    Instant.ofEpochMilli(3));
            publisher.publish(
                    new SkillEvent(SkillEventType.ERROR, error, new ErrorPayload("失敗しました", "IllegalStateException")));

            spansCopy = List.copyOf(exporter.getFinishedSpanItems());
        }

        SpanData metricsSpan = spansCopy.stream().filter(span -> "workflow.done".equals(span.getName())).findFirst()
                .orElseThrow();
        assertThat(metricsSpan.getAttributes().get(AttributeKey.longKey("skill.metrics.latency_ms"))).isEqualTo(42L);
        assertThat(metricsSpan.getAttributes().get(AttributeKey.longKey("skill.metrics.retry_count"))).isEqualTo(1L);
        assertThat(metricsSpan.getAttributes().get(AttributeKey.longKey("gen_ai.usage.input_tokens"))).isEqualTo(5L);
        assertThat(metricsSpan.getAttributes().get(AttributeKey.longKey("gen_ai.usage.output_tokens"))).isEqualTo(3L);

        SpanData errorSpan = spansCopy.stream().filter(span -> "run.failed".equals(span.getName())).findFirst()
                .orElseThrow();
        assertThat(errorSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(errorSpan.getAttributes().get(AttributeKey.stringKey("skill.error.message"))).contains("失敗しました");
        assertThat(errorSpan.getAttributes().get(AttributeKey.stringKey("skill.error.type")))
                .isEqualTo("IllegalStateException");
        assertThat(spansCopy.stream().map(SpanData::getTraceId).distinct()).hasSize(1);
    }

    @Test
    @DisplayName("入力/出力のペイロードを OTLP 属性に変換する")
    void publishInputAndOutputAttributes() {
        List<SpanData> spansCopy;
        try (InMemorySpanExporter exporter = InMemorySpanExporter.create();
                OtlpSkillPublisher publisher = new OtlpSkillPublisher(exporter)) {
            SkillEventMetadata goal = new SkillEventMetadata(RUN_ID, SKILL_ID, PHASE_PLAN, "plan.input.goal",
                    Instant.ofEpochMilli(4));
            publisher.publish(new SkillEvent(SkillEventType.INPUT, goal, new InputPayload("goal", "")));

            SkillEventMetadata upload = new SkillEventMetadata(RUN_ID, SKILL_ID, PHASE_PLAN, "plan.input.upload",
                    Instant.ofEpochMilli(5));
            publisher.publish(new SkillEvent(SkillEventType.INPUT, upload, new InputPayload("", "/tmp/input.txt")));

            SkillEventMetadata download = new SkillEventMetadata(RUN_ID, SKILL_ID, PHASE_ACT, "task.output.download",
                    Instant.ofEpochMilli(6));
            publisher.publish(new SkillEvent(SkillEventType.OUTPUT, download,
                    new OutputPayload("download", "task-1", "/remote/out.txt", "/local/out.txt", "")));

            SkillEventMetadata stdout = new SkillEventMetadata(RUN_ID, SKILL_ID, PHASE_ACT, "task.output.stdout",
                    Instant.ofEpochMilli(7));
            publisher.publish(new SkillEvent(SkillEventType.OUTPUT, stdout,
                    new OutputPayload("stdout", "task-2", "", "", "stdout-text")));

            spansCopy = List.copyOf(exporter.getFinishedSpanItems());
        }

        SpanData goalSpan = spansCopy.stream().filter(span -> "plan.input.goal".equals(span.getName())).findFirst()
                .orElseThrow();
        assertThat(goalSpan.getAttributes().get(AttributeKey.stringKey("skill.input.goal"))).isEqualTo("goal");
        assertThat(goalSpan.getAttributes().get(AttributeKey.stringKey("skill.input.file_path"))).isNull();

        SpanData uploadSpan = spansCopy.stream().filter(span -> "plan.input.upload".equals(span.getName())).findFirst()
                .orElseThrow();
        assertThat(uploadSpan.getAttributes().get(AttributeKey.stringKey("skill.input.goal"))).isNull();
        assertThat(uploadSpan.getAttributes().get(AttributeKey.stringKey("skill.input.file_path")))
                .isEqualTo("/tmp/input.txt");

        SpanData downloadSpan = spansCopy.stream().filter(span -> "task.output.download".equals(span.getName()))
                .findFirst().orElseThrow();
        assertThat(downloadSpan.getAttributes().get(AttributeKey.stringKey("skill.output.type"))).isEqualTo("download");
        assertThat(downloadSpan.getAttributes().get(AttributeKey.stringKey("skill.output.task_id")))
                .isEqualTo("task-1");
        assertThat(downloadSpan.getAttributes().get(AttributeKey.stringKey("skill.output.source_path")))
                .isEqualTo("/remote/out.txt");
        assertThat(downloadSpan.getAttributes().get(AttributeKey.stringKey("skill.output.destination_path")))
                .isEqualTo("/local/out.txt");

        SpanData stdoutSpan = spansCopy.stream().filter(span -> "task.output.stdout".equals(span.getName())).findFirst()
                .orElseThrow();
        assertThat(stdoutSpan.getAttributes().get(AttributeKey.stringKey("skill.output.type"))).isEqualTo("stdout");
        assertThat(stdoutSpan.getAttributes().get(AttributeKey.stringKey("skill.output.task_id"))).isEqualTo("task-2");
        assertThat(stdoutSpan.getAttributes().get(AttributeKey.stringKey("skill.output.content")))
                .isEqualTo("stdout-text");
    }
}
