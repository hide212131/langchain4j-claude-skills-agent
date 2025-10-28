package io.github.hide212131.langchain4j.claude.skills.runtime.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.semconv.ServiceAttributes;
import java.util.Objects;

/**
 * Configuration for OpenTelemetry observability.
 * Supports exporting traces to LangFuse via OTLP.
 */
public final class ObservabilityConfig {

    private final String otlpEndpoint;
    private final String serviceName;
    private final boolean enabled;
    private OpenTelemetry openTelemetry;
    private WorkflowTracer workflowTracer;

    private ObservabilityConfig(String otlpEndpoint, String serviceName, boolean enabled) {
        this.otlpEndpoint = otlpEndpoint;
        this.serviceName = Objects.requireNonNull(serviceName, "serviceName");
        this.enabled = enabled;
    }

    public static ObservabilityConfig disabled() {
        return new ObservabilityConfig(null, "langchain4j-claude-skills-agent", false);
    }

    public static ObservabilityConfig forLangFuse(String otlpEndpoint, String serviceName) {
        if (otlpEndpoint == null || otlpEndpoint.isBlank()) {
            throw new IllegalArgumentException("otlpEndpoint must not be blank");
        }
        return new ObservabilityConfig(otlpEndpoint, serviceName, true);
    }

    public static ObservabilityConfig fromEnvironment(EnvironmentVariables environment) {
        String endpoint = environment.get("LANGFUSE_OTLP_ENDPOINT");
        String serviceName = environment.get("LANGFUSE_SERVICE_NAME");
        
        if (endpoint == null || endpoint.isBlank()) {
            return disabled();
        }
        
        if (serviceName == null || serviceName.isBlank()) {
            serviceName = "langchain4j-claude-skills-agent";
        }
        
        return forLangFuse(endpoint, serviceName);
    }

    public OpenTelemetry createOpenTelemetry() {
        if (openTelemetry == null) {
            openTelemetry = initializeOpenTelemetry();
        }
        return openTelemetry;
    }

    private OpenTelemetry initializeOpenTelemetry() {
        if (!enabled) {
            return OpenTelemetry.noop();
        }

        Resource resource = Resource.getDefault().toBuilder()
                .put(ServiceAttributes.SERVICE_NAME, serviceName)
                .build();

        OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint(otlpEndpoint)
                .build();

        SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
                .setResource(resource)
                .build();

        return OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .buildAndRegisterGlobal();
    }

    public WorkflowTracer createWorkflowTracer() {
        if (workflowTracer == null) {
            workflowTracer = new WorkflowTracer(createOpenTelemetry(), enabled);
        }
        return workflowTracer;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getOtlpEndpoint() {
        return otlpEndpoint;
    }

    public String getServiceName() {
        return serviceName;
    }

    @FunctionalInterface
    public interface EnvironmentVariables {
        String get(String name);
    }
}
