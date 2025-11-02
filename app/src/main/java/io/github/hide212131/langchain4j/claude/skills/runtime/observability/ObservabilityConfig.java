package io.github.hide212131.langchain4j.claude.skills.runtime.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Configuration for OpenTelemetry observability.
 * Supports exporting traces to LangFuse via OTLP.
 */
public final class ObservabilityConfig {

    private final String otlpEndpoint;
    private final String serviceName;
    private final String basicAuthHeader;
    private final boolean enabled;
    private OpenTelemetry openTelemetry;

    private ObservabilityConfig(String otlpEndpoint, String serviceName, String basicAuthHeader, boolean enabled) {
        this.otlpEndpoint = otlpEndpoint;
        this.serviceName = Objects.requireNonNull(serviceName, "serviceName");
        this.basicAuthHeader = basicAuthHeader;
        this.enabled = enabled;
    }

    public static ObservabilityConfig disabled() {
        return new ObservabilityConfig(null, "langchain4j-claude-skills-agent", null, false);
    }

    public static ObservabilityConfig forLangFuse(String otlpEndpoint, String serviceName, String basicAuthHeader) {
        if (otlpEndpoint == null || otlpEndpoint.isBlank()) {
            throw new IllegalArgumentException("otlpEndpoint must not be blank");
        }
        return new ObservabilityConfig(otlpEndpoint, serviceName, basicAuthHeader, true);
    }

    public static ObservabilityConfig fromEnvironment(EnvironmentVariables environment) {
        String endpoint = environment.get("LANGFUSE_OTLP_ENDPOINT");
        String serviceName = environment.get("LANGFUSE_SERVICE_NAME");
        String baseUrl = environment.get("LANGFUSE_BASE_URL");
        String publicKey = environment.get("LANGFUSE_PUBLIC_KEY");
        String secretKey = environment.get("LANGFUSE_SECRET_KEY");
        
        if (endpoint == null || endpoint.isBlank()) {
            if (baseUrl != null && !baseUrl.isBlank()) {
                endpoint = normalizeBaseUrl(baseUrl) + "/api/public/otel/v1/traces";
            }
        }
        
        if (endpoint == null || endpoint.isBlank()) {
            return disabled();
        }

        if (serviceName == null || serviceName.isBlank()) {
            serviceName = "langchain4j-claude-skills-agent";
        }
        
        String basicAuthHeader = null;
        if (publicKey != null && !publicKey.isBlank() && secretKey != null && !secretKey.isBlank()) {
            basicAuthHeader = buildBasicAuthHeader(publicKey, secretKey);
        }

        System.setProperty("otel.semconv-stability.opt-in", "gen_ai_latest_experimental");

        return forLangFuse(endpoint, serviceName, basicAuthHeader);
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
        .put(AttributeKey.stringKey("service.name"), serviceName)
        .build();

    var exporterBuilder = OtlpHttpSpanExporter.builder()
        .setEndpoint(otlpEndpoint)
        .setTimeout(30, TimeUnit.SECONDS);

    if (basicAuthHeader != null) {
        exporterBuilder.setHeaders(() -> Map.of("Authorization", basicAuthHeader));
    }

    OtlpHttpSpanExporter spanExporter = exporterBuilder.build();

        SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
                .setResource(resource)
                .build();

        return OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .buildAndRegisterGlobal();
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

    private static String normalizeBaseUrl(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private static String buildBasicAuthHeader(String username, String password) {
        String credentials = username + ":" + password;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    @FunctionalInterface
    public interface EnvironmentVariables {
        String get(String name);
    }
}
