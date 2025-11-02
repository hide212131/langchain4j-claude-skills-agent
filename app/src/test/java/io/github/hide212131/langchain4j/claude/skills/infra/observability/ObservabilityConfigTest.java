package io.github.hide212131.langchain4j.claude.skills.infra.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.Test;

class ObservabilityConfigTest {

    @Test
    void fromEnvironment_whenNoEndpoint_shouldReturnDisabledConfig() {
        // When: No LANGFUSE_OTLP_ENDPOINT environment variable is set
        ObservabilityConfig config = ObservabilityConfig.fromEnvironment(name -> null);

        // Then: Observability should be disabled
        assertThat(config.isEnabled()).isFalse();
        assertThat(config.openTelemetry()).isNotNull();
        assertThat(config.tracer()).isNotNull();
    }

    @Test
    void fromEnvironment_shouldCreateValidConfiguration() {
        // When: Creating configuration from environment
        ObservabilityConfig config = ObservabilityConfig.fromEnvironment(name -> switch (name) {
            case "LANGFUSE_OTLP_ENDPOINT" -> "https://example.com";
            case "LANGFUSE_OTLP_USERNAME" -> "public";
            case "LANGFUSE_OTLP_PASSWORD" -> "secret";
            case "LANGFUSE_PROJECT_ID" -> "project-123";
            default -> null;
        });

        // Then: Configuration should be valid
        assertThat(config).isNotNull();
        assertThat(config.openTelemetry()).isNotNull();
        assertThat(config.tracer()).isNotNull();
    }

    @Test
    void openTelemetry_shouldReturnNonNullInstance() {
        // Given: A configuration
        ObservabilityConfig config = ObservabilityConfig.fromEnvironment(name -> switch (name) {
            case "LANGFUSE_OTLP_ENDPOINT" -> "https://example.com";
            case "LANGFUSE_OTLP_USERNAME" -> "public";
            case "LANGFUSE_OTLP_PASSWORD" -> "secret";
            default -> null;
        });

        // When: Getting OpenTelemetry instance
        OpenTelemetry openTelemetry = config.openTelemetry();

        // Then: Instance should be valid
        assertThat(openTelemetry).isNotNull();
    }

    @Test
    void tracer_shouldReturnNonNullInstance() {
        // Given: A configuration
        ObservabilityConfig config = ObservabilityConfig.fromEnvironment(name -> switch (name) {
            case "LANGFUSE_OTLP_ENDPOINT" -> "https://example.com";
            case "LANGFUSE_OTLP_USERNAME" -> "public";
            case "LANGFUSE_OTLP_PASSWORD" -> "secret";
            default -> null;
        });

        // When: Getting tracer instance
        Tracer tracer = config.tracer();

        // Then: Instance should be valid
        assertThat(tracer).isNotNull();
    }
}
