package io.github.hide212131.langchain4j.claude.skills.infra.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.semconv.ResourceAttributes;
import java.util.concurrent.TimeUnit;

/**
 * Configures OpenTelemetry for LangFuse observability integration.
 * Uses OTLP gRPC exporter to send traces to LangFuse.
 */
public final class ObservabilityConfig {

    private static final String DEFAULT_SERVICE_NAME = "langchain4j-skills-agent";
    private static final String DEFAULT_OTLP_ENDPOINT = "http://localhost:4317";
    
    private final OpenTelemetry openTelemetry;
    private final Tracer tracer;
    private final boolean enabled;

    private ObservabilityConfig(OpenTelemetry openTelemetry, Tracer tracer, boolean enabled) {
        this.openTelemetry = openTelemetry;
        this.tracer = tracer;
        this.enabled = enabled;
    }

    /**
     * Creates observability configuration from environment variables.
     * 
     * Environment variables:
     * - LANGFUSE_OTLP_ENDPOINT: LangFuse OTLP endpoint (default: http://localhost:4317)
     * - LANGFUSE_SERVICE_NAME: Service name for tracing (default: langchain4j-skills-agent)
     * 
     * Observability is enabled only if LANGFUSE_OTLP_ENDPOINT is set.
     */
    public static ObservabilityConfig fromEnvironment() {
        String endpoint = System.getenv("LANGFUSE_OTLP_ENDPOINT");
        
        if (endpoint == null || endpoint.isBlank()) {
            // Observability disabled - return no-op configuration
            return new ObservabilityConfig(
                OpenTelemetry.noop(),
                OpenTelemetry.noop().getTracer("noop"),
                false
            );
        }
        
        String serviceName = System.getenv("LANGFUSE_SERVICE_NAME");
        if (serviceName == null || serviceName.isBlank()) {
            serviceName = DEFAULT_SERVICE_NAME;
        }
        
        Resource resource = Resource.getDefault()
            .merge(Resource.create(
                Attributes.of(ResourceAttributes.SERVICE_NAME, serviceName)
            ));
        
        OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
            .setEndpoint(endpoint)
            .setTimeout(30, TimeUnit.SECONDS)
            .build();
        
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
            .setResource(resource)
            .build();
        
        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .build();
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            tracerProvider.close();
        }));
        
        Tracer tracer = openTelemetry.getTracer("langchain4j-skills-agent");
        
        return new ObservabilityConfig(openTelemetry, tracer, true);
    }

    /**
     * Returns the OpenTelemetry instance for use with LangChain4j models.
     */
    public OpenTelemetry openTelemetry() {
        return openTelemetry;
    }

    /**
     * Returns a tracer for manual span creation.
     */
    public Tracer tracer() {
        return tracer;
    }

    /**
     * Returns true if observability is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }
}
