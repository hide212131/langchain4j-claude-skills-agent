package io.github.hide212131.langchain4j.claude.skills.runtime.visibility;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@SuppressWarnings("PMD.JUnitTestContainsTooManyAsserts")
class OtlpVisibilityPublisherTest {

    @SuppressWarnings("PMD.UnnecessaryConstructor")
    OtlpVisibilityPublisherTest() {
        // default
    }

    @Test
    @DisplayName("可視化イベントを Span 属性にマッピングしてエクスポートする")
    void publishMapsAttributes() {
        List<SpanData> spansCopy;
        try (InMemorySpanExporter exporter = InMemorySpanExporter.create();
                OtlpVisibilityPublisher publisher = new OtlpVisibilityPublisher(exporter)) {
            VisibilityEventMetadata metadata = new VisibilityEventMetadata("run-1", "skill-1", "plan", "plan.prompt",
                    Instant.ofEpochMilli(0));
            PromptPayload payload = new PromptPayload("prompt-text", "resp", "gpt", "assistant",
                    new TokenUsage(10L, 5L, 15L));
            publisher.publish(new VisibilityEvent(VisibilityEventType.PROMPT, metadata, payload));

            spansCopy = List.copyOf(exporter.getFinishedSpanItems());
        }

        assertThat(spansCopy).hasSize(1);
        SpanData span = spansCopy.getFirst();
        assertThat(span.getName()).isEqualTo("plan.prompt");
        assertThat(span.getAttributes().get(AttributeKey.stringKey("visibility.prompt.content")))
                .isEqualTo("prompt-text");
        assertThat(span.getAttributes().get(AttributeKey.longKey("visibility.usage.total_tokens"))).isEqualTo(15L);
        assertThat(span.getAttributes().get(AttributeKey.stringKey("visibility.run_id"))).isEqualTo("run-1");
    }
}
