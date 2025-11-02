package io.github.hide212131.langchain4j.claude.skills.infra.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Configures OpenTelemetry for LangFuse observability integration.
 * Uses OTLP HTTP exporter with Basic authentication to send traces to LangFuse.
 */
public final class ObservabilityConfig {

    private static final String DEFAULT_SERVICE_NAME = "langchain4j-skills-agent";
    
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
     * - LANGFUSE_OTLP_ENDPOINT: LangFuse OTLP HTTP endpoint (required; observability is disabled if not set)
    * - LANGFUSE_OTLP_USERNAME or LANGFUSE_PUBLIC_KEY: LangFuse public key for Basic auth
    * - LANGFUSE_OTLP_PASSWORD or LANGFUSE_SECRET_KEY: LangFuse secret key for Basic auth
     * - LANGFUSE_SERVICE_NAME: Service name for tracing (default: langchain4j-skills-agent)
    * - LANGFUSE_PROJECT_ID: LangFuse project identifier (highly recommended)
    * - LANGFUSE_ENVIRONMENT: LangFuse environment name (default: default)
     * 
     * Observability is enabled only if LANGFUSE_OTLP_ENDPOINT is set.
     * GenAI semantic conventions are enabled via OTEL_SEMCONV_STABILITY_OPT_IN=gen_ai_latest_experimental.
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
        
        // Enable GenAI semantic conventions
        System.setProperty("otel.semconv-stability.opt-in", "gen_ai_latest_experimental");
        
        String serviceName = System.getenv("LANGFUSE_SERVICE_NAME");
        if (serviceName == null || serviceName.isBlank()) {
            serviceName = DEFAULT_SERVICE_NAME;
        }
        
        String username = firstNonBlank(
            System.getenv("LANGFUSE_OTLP_USERNAME"),
            System.getenv("LANGFUSE_PUBLIC_KEY")
        );
        String password = firstNonBlank(
            System.getenv("LANGFUSE_OTLP_PASSWORD"),
            System.getenv("LANGFUSE_SECRET_KEY")
        );

        String projectId = System.getenv("LANGFUSE_PROJECT_ID");
        String environment = System.getenv("LANGFUSE_ENVIRONMENT");
        if (environment == null || environment.isBlank()) {
            environment = "default";
        }

        AttributesBuilder resourceAttributes = Attributes.builder()
            .put(AttributeKey.stringKey("service.name"), serviceName)
            .put(AttributeKey.stringKey("langfuse.environment"), environment);

        if (projectId != null && !projectId.isBlank()) {
            resourceAttributes.put(AttributeKey.stringKey("langfuse.projectId"), projectId);
        } else {
            System.err.println("[Observability] LANGFUSE_PROJECT_ID is not set; traces may be discarded by LangFuse.");
        }
        
        Resource resource = Resource.getDefault()
            .merge(Resource.create(resourceAttributes.build()));
        
        // Build HTTP exporter with Basic authentication for LangFuse
        var exporterBuilder = OtlpHttpSpanExporter.builder()
            .setEndpoint(endpoint)
            .setTimeout(30, TimeUnit.SECONDS);
        
        // Add Basic authentication headers if credentials are provided
        if (username != null && !username.isBlank() && password != null && !password.isBlank()) {
            String credentials = username + ":" + password;
            String encodedCredentials = Base64.getEncoder().encodeToString(
                credentials.getBytes(StandardCharsets.UTF_8)
            );
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Basic " + encodedCredentials);
            exporterBuilder.setHeaders(() -> headers);
        } else {
            System.err.println("[Observability] LangFuse credentials are not fully configured; spans will not be accepted.");
        }
        
        var spanExporter = exporterBuilder.build();
        
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
            .setResource(resource)
            .build();
        
        // Build without global registration to avoid conflicts in tests and multi-model scenarios
        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .build();
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                tracerProvider.close();
                openTelemetry.close();
            } catch (Exception e) {
                // Ignore shutdown errors
            }
        }, "opentelemetry-shutdown"));
        
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

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }
}
