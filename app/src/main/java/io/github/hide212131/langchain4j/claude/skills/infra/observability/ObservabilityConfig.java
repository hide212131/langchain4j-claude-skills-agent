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
 * Configures OpenTelemetry for LangFuse observability integration. Uses an OTLP HTTP exporter with
 * Basic authentication to forward spans into LangFuse.
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
     * Builds an {@link ObservabilityConfig} instance based on LangFuse environment variables. Observability is
     * enabled only when the endpoint and both credentials are present.
     *
     * <p>Supported environment variables:</p>
     * <ul>
     *   <li>LANGFUSE_OTLP_ENDPOINT (required) — OTLP HTTP endpoint.</li>
     *   <li>LANGFUSE_OTLP_USERNAME or LANGFUSE_PUBLIC_KEY (required) — public key for Basic auth.</li>
     *   <li>LANGFUSE_OTLP_PASSWORD or LANGFUSE_SECRET_KEY (required) — secret key for Basic auth.</li>
     *   <li>LANGFUSE_SERVICE_NAME — custom service name (defaults to langchain4j-skills-agent).</li>
     *   <li>LANGFUSE_PROJECT_ID — LangFuse project identifier (recommended).</li>
     *   <li>LANGFUSE_ENVIRONMENT — LangFuse environment label (defaults to "default").</li>
     * </ul>
     */
    public static ObservabilityConfig fromEnvironment() {
        return fromEnvironment(System::getenv);
    }

    static ObservabilityConfig fromEnvironment(EnvironmentVariables environment) {
        String endpoint = environment.get("LANGFUSE_OTLP_ENDPOINT");
        if (endpoint == null || endpoint.isBlank()) {
            System.err.println("[Observability] LangFuse endpoint is not configured; spans will not be accepted.");
            return disabled();
        }

        String username = firstNonBlank(environment.get("LANGFUSE_OTLP_USERNAME"), environment.get("LANGFUSE_PUBLIC_KEY"));
        String password = firstNonBlank(environment.get("LANGFUSE_OTLP_PASSWORD"), environment.get("LANGFUSE_SECRET_KEY"));
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            System.err.println("[Observability] LangFuse credentials are not fully configured; spans will not be accepted.");
            return disabled();
        }

        // Enable GenAI semantic conventions supported by LangFuse.
        System.setProperty("otel.semconv-stability.opt-in", "gen_ai_latest_experimental");

        String serviceName = environment.get("LANGFUSE_SERVICE_NAME");
        if (serviceName == null || serviceName.isBlank()) {
            serviceName = DEFAULT_SERVICE_NAME;
        }

        String projectId = environment.get("LANGFUSE_PROJECT_ID");
        String environmentName = environment.get("LANGFUSE_ENVIRONMENT");
        if (environmentName == null || environmentName.isBlank()) {
            environmentName = "default";
        }

        AttributesBuilder resourceAttributes = Attributes.builder()
                .put(AttributeKey.stringKey("service.name"), serviceName)
                .put(AttributeKey.stringKey("langfuse.environment"), environmentName);
        if (projectId != null && !projectId.isBlank()) {
            resourceAttributes.put(AttributeKey.stringKey("langfuse.projectId"), projectId);
        } else {
            System.err.println("[Observability] LANGFUSE_PROJECT_ID is not set; traces may be discarded by LangFuse.");
        }

        Resource resource = Resource.getDefault().merge(Resource.create(resourceAttributes.build()));

        String encodedCredentials = Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        Map<String, String> headers = new HashMap<>();
    headers.put("Authorization", "Basic " + encodedCredentials);

        OtlpHttpSpanExporter spanExporter = OtlpHttpSpanExporter.builder()
                .setEndpoint(endpoint)
                .setTimeout(30, TimeUnit.SECONDS)
                .setHeaders(() -> headers)
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
            try {
                tracerProvider.close();
                openTelemetry.close();
            } catch (Exception ignored) {
                // Ignore shutdown errors during shutdown hook.
            }
        }, "opentelemetry-shutdown"));

        Tracer tracer = openTelemetry.getTracer("langchain4j-skills-agent");
        return new ObservabilityConfig(openTelemetry, tracer, true);
    }

    public OpenTelemetry openTelemetry() {
        return openTelemetry;
    }

    public Tracer tracer() {
        return tracer;
    }

    public boolean isEnabled() {
        return enabled;
    }

    private static ObservabilityConfig disabled() {
        OpenTelemetry noop = OpenTelemetry.noop();
        return new ObservabilityConfig(noop, noop.getTracer("noop"), false);
    }

    private static String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }

    @FunctionalInterface
    interface EnvironmentVariables {
        String get(String key);
    }
}
