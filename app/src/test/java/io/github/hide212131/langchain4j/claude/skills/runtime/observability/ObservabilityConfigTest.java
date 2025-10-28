package io.github.hide212131.langchain4j.claude.skills.runtime.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;

class ObservabilityConfigTest {

    @Test
    void disabledConfigShouldReturnNoopOpenTelemetry() {
        ObservabilityConfig config = ObservabilityConfig.disabled();
        
        assertThat(config.isEnabled()).isFalse();
        assertThat(config.getOtlpEndpoint()).isNull();
        assertThat(config.getServiceName()).isEqualTo("langchain4j-claude-skills-agent");
        
        OpenTelemetry openTelemetry = config.createOpenTelemetry();
        assertThat(openTelemetry).isNotNull();
    }

    @Test
    void forLangFuseShouldCreateEnabledConfig() {
        ObservabilityConfig config = ObservabilityConfig.forLangFuse(
            "http://localhost:4317", "test-service");
        
        assertThat(config.isEnabled()).isTrue();
        assertThat(config.getOtlpEndpoint()).isEqualTo("http://localhost:4317");
        assertThat(config.getServiceName()).isEqualTo("test-service");
    }

    @Test
    void forLangFuseShouldFailWithBlankEndpoint() {
        assertThatThrownBy(() -> ObservabilityConfig.forLangFuse("", "test-service"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("otlpEndpoint");
    }

    @Test
    void fromEnvironmentShouldCreateEnabledConfigWhenEndpointSet() {
        ObservabilityConfig.EnvironmentVariables env = key -> {
            if ("LANGFUSE_OTLP_ENDPOINT".equals(key)) {
                return "http://localhost:4317";
            }
            if ("LANGFUSE_SERVICE_NAME".equals(key)) {
                return "test-service";
            }
            return null;
        };
        
        ObservabilityConfig config = ObservabilityConfig.fromEnvironment(env);
        
        assertThat(config.isEnabled()).isTrue();
        assertThat(config.getOtlpEndpoint()).isEqualTo("http://localhost:4317");
        assertThat(config.getServiceName()).isEqualTo("test-service");
    }

    @Test
    void fromEnvironmentShouldUseDefaultServiceNameWhenNotSet() {
        ObservabilityConfig.EnvironmentVariables env = key -> {
            if ("LANGFUSE_OTLP_ENDPOINT".equals(key)) {
                return "http://localhost:4317";
            }
            return null;
        };
        
        ObservabilityConfig config = ObservabilityConfig.fromEnvironment(env);
        
        assertThat(config.isEnabled()).isTrue();
        assertThat(config.getServiceName()).isEqualTo("langchain4j-claude-skills-agent");
    }

    @Test
    void fromEnvironmentShouldCreateDisabledConfigWhenEndpointNotSet() {
        ObservabilityConfig.EnvironmentVariables env = key -> null;
        
        ObservabilityConfig config = ObservabilityConfig.fromEnvironment(env);
        
        assertThat(config.isEnabled()).isFalse();
    }
}
