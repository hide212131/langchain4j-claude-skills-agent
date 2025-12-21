package io.github.hide212131.langchain4j.claude.skills.runtime.visibility;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** OTLP へ可視化イベントを送信するパブリッシャ。 */
@SuppressWarnings({ "PMD.AvoidCatchingGenericException", "PMD.CloseResource" })
public final class OtlpVisibilityPublisher implements VisibilityEventPublisher, AutoCloseable {

    private static final String INSTRUMENTATION_NAME = "langchain4j-claude-skills-visibility";

    private final OpenTelemetry openTelemetry;
    private final Tracer tracer;
    private final SdkTracerProvider tracerProvider;

    public OtlpVisibilityPublisher(String endpoint, Map<String, String> headers) {
        this(buildExporter(endpoint, headers));
    }

    OtlpVisibilityPublisher(SpanExporter exporter) {
        Objects.requireNonNull(exporter, "exporter");
        tracerProvider = SdkTracerProvider.builder()
                .setResource(Resource.create(
                        Attributes.of(AttributeKey.stringKey("service.name"), "langchain4j-claude-skills-agent")))
                .addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
        openTelemetry = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();
        tracer = openTelemetry.getTracer(INSTRUMENTATION_NAME);
    }

    @Override
    public void publish(VisibilityEvent event) {
        Objects.requireNonNull(event, "event");
        VisibilityEventMetadata metadata = event.metadata();
        Span span = tracer.spanBuilder(metadata.step()).setSpanKind(SpanKind.INTERNAL)
                .setAttribute("visibility.type", event.type().name()).setAttribute("visibility.phase", metadata.phase())
                .setAttribute("visibility.skill_id", safe(metadata.skillId()))
                .setAttribute("visibility.run_id", safe(metadata.runId())).startSpan();
        try {
            addPayloadAttributes(span, event);
            if (metadata.timestamp() != null) {
                span.addEvent("timestamp", Attributes.of(AttributeKey.stringKey("value"),
                        String.valueOf(metadata.timestamp().toEpochMilli())));
            }
        } catch (RuntimeException ex) {
            span.setStatus(StatusCode.ERROR, ex.getMessage());
            throw ex;
        } finally {
            span.end();
        }
    }

    @Override
    public void close() {
        tracerProvider.close();
        if (openTelemetry instanceof OpenTelemetrySdk sdk) {
            sdk.getSdkTracerProvider().shutdown().join(5, TimeUnit.SECONDS);
        }
    }

    private void addPayloadAttributes(Span span, VisibilityEvent event) {
        VisibilityPayload payload = event.payload();
        if (payload instanceof ParsePayload parse) {
            span.setAttribute("visibility.parse.path", parse.path());
            span.setAttribute("visibility.parse.body_preview", safe(parse.bodyPreview()));
            span.setAttribute("visibility.parse.validated", parse.validated());
            span.setAttribute("visibility.parse.front_matter", parse.frontMatter().toString());
        } else if (payload instanceof PromptPayload prompt) {
            span.setAttribute("visibility.prompt.content", prompt.prompt());
            span.setAttribute("visibility.prompt.response", safe(prompt.response()));
            span.setAttribute("visibility.prompt.model", safe(prompt.model()));
            span.setAttribute("visibility.prompt.role", prompt.role());
            span.setAttribute("gen_ai.request.model", safe(prompt.model()));
            span.setAttribute("gen_ai.request.prompt", prompt.prompt());
            span.setAttribute("gen_ai.response.text", safe(prompt.response()));
            TokenUsage usage = prompt.usage();
            if (usage != null) {
                span.setAttribute("visibility.usage.input_tokens", safeLong(usage.inputTokens()));
                span.setAttribute("visibility.usage.output_tokens", safeLong(usage.outputTokens()));
                span.setAttribute("visibility.usage.total_tokens", safeLong(usage.totalTokens()));
                span.setAttribute("gen_ai.usage.input_tokens", safeLong(usage.inputTokens()));
                span.setAttribute("gen_ai.usage.output_tokens", safeLong(usage.outputTokens()));
                span.setAttribute("gen_ai.usage.total_tokens", safeLong(usage.totalTokens()));
            }
        } else if (payload instanceof AgentStatePayload state) {
            span.setAttribute("visibility.agent.goal", safe(state.goal()));
            span.setAttribute("visibility.agent.decision", state.decision());
            span.setAttribute("visibility.agent.state", safe(state.stateSummary()));
        } else if (payload instanceof MetricsPayload metrics) {
            span.setAttribute("visibility.metrics.input_tokens", safeLong(metrics.inputTokens()));
            span.setAttribute("visibility.metrics.output_tokens", safeLong(metrics.outputTokens()));
            span.setAttribute("visibility.metrics.latency_ms", safeLong(metrics.latencyMillis()));
            span.setAttribute("visibility.metrics.retry_count", safeLong(metrics.retryCount()));
            span.setAttribute("gen_ai.usage.input_tokens", safeLong(metrics.inputTokens()));
            span.setAttribute("gen_ai.usage.output_tokens", safeLong(metrics.outputTokens()));
        } else if (payload instanceof ErrorPayload error) {
            span.setStatus(StatusCode.ERROR, error.message());
            span.setAttribute("visibility.error.message", error.message());
            span.setAttribute("visibility.error.type", error.errorType());
        }
    }

    private static SpanExporter buildExporter(String endpoint, Map<String, String> headers) {
        io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporterBuilder builder = OtlpGrpcSpanExporter.builder();
        if (endpoint != null && !endpoint.isBlank()) {
            builder.setEndpoint(endpoint);
        }
        if (headers != null) {
            headers.forEach(builder::addHeader);
        }
        return builder.build();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private long safeLong(Number value) {
        return value == null ? 0L : value.longValue();
    }
}
