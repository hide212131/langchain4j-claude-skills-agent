package io.github.hide212131.langchain4j.claude.skills.runtime.visibility;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** OTLP へスキルイベントを送信するパブリッシャ。 */
@SuppressWarnings({ "PMD.AvoidCatchingGenericException", "PMD.CloseResource" })
public final class OtlpSkillPublisher implements SkillEventPublisher, AutoCloseable {

    private static final String INSTRUMENTATION_NAME = "langchain4j-claude-skills-skill";
    private static final String LANGFUSE_PUBLIC_OTEL_PATH = "/api/public/otel";
    private static final String OTLP_V1_TRACES_PATH = "/v1/traces";

    private final OpenTelemetry openTelemetry;
    private final Tracer tracer;
    private final SdkTracerProvider tracerProvider;

    /** runId ごとの root span context（1 run 1 trace 用）。 */
    private final Map<String, SpanContext> rootSpanContexts = new ConcurrentHashMap<>();

    public OtlpSkillPublisher(String endpoint, Map<String, String> headers) {
        this(buildExporter(endpoint, headers));
    }

    OtlpSkillPublisher(SpanExporter exporter) {
        Objects.requireNonNull(exporter, "exporter");
        tracerProvider = SdkTracerProvider.builder()
                .setResource(Resource.create(
                        Attributes.of(AttributeKey.stringKey("service.name"), "langchain4j-claude-skills-agent")))
                .addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
        openTelemetry = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();
        tracer = openTelemetry.getTracer(INSTRUMENTATION_NAME);
    }

    @Override
    public void publish(SkillEvent event) {
        Objects.requireNonNull(event, "event");
        SkillEventMetadata metadata = event.metadata();
        SpanContext root = rootSpanContextFor(metadata);
        Context parent = Context.current().with(Span.wrap(root));
        Span span = tracer.spanBuilder(metadata.step()).setSpanKind(SpanKind.INTERNAL).setParent(parent)
                .setAttribute("skill.type", event.type().name()).setAttribute("skill.phase", metadata.phase())
                .setAttribute("skill.skill_id", safe(metadata.skillId()))
                .setAttribute("skill.run_id", safe(metadata.runId())).startSpan();
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
        // root span は生成時に end 済み（export順序の安定化のため）。
        rootSpanContexts.clear();
        tracerProvider.shutdown().join(5, TimeUnit.SECONDS);
    }

    private SpanContext rootSpanContextFor(SkillEventMetadata metadata) {
        String runId = safe(metadata.runId());
        return rootSpanContexts.computeIfAbsent(runId, id -> {
            Span root = tracer.spanBuilder("skills.run").setSpanKind(SpanKind.INTERNAL).setAttribute("skill.run_id", id)
                    .startSpan();

            if (metadata.skillId() != null && !metadata.skillId().isBlank()) {
                root.setAttribute("skill.skill_id", metadata.skillId().trim());
            }

            // Langfuse 側で親spanの到着が後になると結合されない可能性があるため、rootを先にexportする。
            root.end();
            return root.getSpanContext();
        });
    }

    private void addPayloadAttributes(Span span, SkillEvent event) {
        SkillPayload payload = event.payload();
        if (payload instanceof ParsePayload parse) {
            span.setAttribute("skill.parse.path", parse.path());
            span.setAttribute("skill.parse.body_preview", safe(parse.bodyPreview()));
            span.setAttribute("skill.parse.validated", parse.validated());
            span.setAttribute("skill.parse.front_matter", parse.frontMatter().toString());
        } else if (payload instanceof PromptPayload prompt) {
            span.setAttribute("skill.prompt.role", prompt.role());
            span.setAttribute("gen_ai.request.model", safe(prompt.model()));
            span.setAttribute("gen_ai.request.prompt", prompt.prompt());
            span.setAttribute("gen_ai.response.text", safe(prompt.response()));
            TokenUsage usage = prompt.usage();
            if (usage != null) {
                span.setAttribute("gen_ai.usage.input_tokens", safeLong(usage.inputTokens()));
                span.setAttribute("gen_ai.usage.output_tokens", safeLong(usage.outputTokens()));
                span.setAttribute("gen_ai.usage.total_tokens", safeLong(usage.totalTokens()));
            }
        } else if (payload instanceof AgentStatePayload state) {
            span.setAttribute("skill.agent.goal", safe(state.goal()));
            span.setAttribute("skill.agent.decision", state.decision());
            span.setAttribute("skill.agent.state", safe(state.stateSummary()));
        } else if (payload instanceof InputPayload input) {
            if (!input.goal().isBlank()) {
                span.setAttribute("skill.input.goal", input.goal());
            }
            if (!input.inputFilePath().isBlank()) {
                span.setAttribute("skill.input.file_path", input.inputFilePath());
            }
        } else if (payload instanceof OutputPayload output) {
            span.setAttribute("skill.output.type", output.outputType());
            if (!output.taskId().isBlank()) {
                span.setAttribute("skill.output.task_id", output.taskId());
            }
            if (!output.sourcePath().isBlank()) {
                span.setAttribute("skill.output.source_path", output.sourcePath());
            }
            if (!output.destinationPath().isBlank()) {
                span.setAttribute("skill.output.destination_path", output.destinationPath());
            }
            if (!output.content().isBlank()) {
                span.setAttribute("skill.output.content", output.content());
            }
        } else if (payload instanceof MetricsPayload metrics) {
            span.setAttribute("skill.metrics.latency_ms", safeLong(metrics.latencyMillis()));
            span.setAttribute("skill.metrics.retry_count", safeLong(metrics.retryCount()));
            if (metrics.inputTokens() != null) {
                span.setAttribute("gen_ai.usage.input_tokens", metrics.inputTokens());
            }
            if (metrics.outputTokens() != null) {
                span.setAttribute("gen_ai.usage.output_tokens", metrics.outputTokens());
            }
        } else if (payload instanceof ToolPayload tool) {
            span.setAttribute("skill.tool.name", safe(tool.toolName()));
            span.setAttribute("skill.tool.input", safe(tool.input()));
            span.setAttribute("skill.tool.output", safe(tool.output()));
            if (tool.errorType() != null && !tool.errorType().isBlank()) {
                span.setStatus(StatusCode.ERROR, tool.errorMessage());
                span.setAttribute("skill.tool.error_type", tool.errorType());
                span.setAttribute("skill.tool.error_message", safe(tool.errorMessage()));
            }
        } else if (payload instanceof ErrorPayload error) {
            span.setStatus(StatusCode.ERROR, error.message());
            span.setAttribute("skill.error.message", error.message());
            span.setAttribute("skill.error.type", error.errorType());
        }
    }

    private static SpanExporter buildExporter(String endpoint, Map<String, String> headers) {
        if (shouldUseHttpExporter(endpoint)) {
            io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporterBuilder builder = OtlpHttpSpanExporter
                    .builder();
            if (endpoint != null && !endpoint.isBlank()) {
                builder.setEndpoint(normalizeHttpEndpoint(endpoint));
            }
            if (headers != null) {
                headers.forEach(builder::addHeader);
            }
            return builder.build();
        }

        io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporterBuilder builder = OtlpGrpcSpanExporter.builder();
        if (endpoint != null && !endpoint.isBlank()) {
            builder.setEndpoint(endpoint);
        }
        if (headers != null) {
            headers.forEach(builder::addHeader);
        }
        return builder.build();
    }

    private static boolean shouldUseHttpExporter(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return false;
        }
        String value = endpoint.trim();
        if (value.contains(LANGFUSE_PUBLIC_OTEL_PATH)) {
            return true;
        }
        if (value.contains(OTLP_V1_TRACES_PATH)) {
            return true;
        }
        return value.endsWith(":4318") || value.contains(":4318/");
    }

    private static String normalizeHttpEndpoint(String endpoint) {
        String value = endpoint.trim();
        if (value.contains(OTLP_V1_TRACES_PATH)) {
            return value;
        }
        if (value.contains(LANGFUSE_PUBLIC_OTEL_PATH)) {
            return value.endsWith(LANGFUSE_PUBLIC_OTEL_PATH) ? value + OTLP_V1_TRACES_PATH : value;
        }
        return value.endsWith("/") ? value + "v1/traces" : value + OTLP_V1_TRACES_PATH;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private long safeLong(Number value) {
        return value == null ? 0L : value.longValue();
    }
}
