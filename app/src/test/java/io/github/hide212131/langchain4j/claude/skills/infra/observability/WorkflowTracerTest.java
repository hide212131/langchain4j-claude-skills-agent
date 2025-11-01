package io.github.hide212131.langchain4j.claude.skills.infra.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class WorkflowTracerTest {

    @Test
    void trace_whenDisabled_shouldExecuteOperationWithoutTracing() {
        // Given: A disabled tracer
        Tracer tracer = OpenTelemetry.noop().getTracer("test");
        WorkflowTracer workflowTracer = new WorkflowTracer(tracer, false);
        AtomicBoolean executed = new AtomicBoolean(false);

        // When: Tracing an operation
        workflowTracer.trace("test-operation", Map.of(), () -> {
            executed.set(true);
        });

        // Then: Operation should be executed
        assertThat(executed.get()).isTrue();
    }

    @Test
    void trace_withSupplier_shouldReturnValue() {
        // Given: A disabled tracer
        Tracer tracer = OpenTelemetry.noop().getTracer("test");
        WorkflowTracer workflowTracer = new WorkflowTracer(tracer, false);

        // When: Tracing an operation with a return value
        String result = workflowTracer.trace("test-operation", Map.of("key", "value"), () -> "test-result");

        // Then: Result should be returned
        assertThat(result).isEqualTo("test-result");
    }

    @Test
    void startSpan_whenDisabled_shouldReturnInvalidSpan() {
        // Given: A disabled tracer
        Tracer tracer = OpenTelemetry.noop().getTracer("test");
        WorkflowTracer workflowTracer = new WorkflowTracer(tracer, false);

        // When: Starting a span
        Span span = workflowTracer.startSpan("test-span", Map.of());

        // Then: Span should be invalid (no-op)
        assertThat(span).isNotNull();
        assertThat(span.isRecording()).isFalse();
    }

    @Test
    void addEvent_whenDisabled_shouldNotThrow() {
        // Given: A disabled tracer
        Tracer tracer = OpenTelemetry.noop().getTracer("test");
        WorkflowTracer workflowTracer = new WorkflowTracer(tracer, false);

        // When/Then: Adding an event should not throw
        workflowTracer.addEvent("test-event", Map.of("key", "value"));
    }

    @Test
    void isEnabled_shouldReturnCorrectValue() {
        // Given: Disabled and enabled tracers
        Tracer noopTracer = OpenTelemetry.noop().getTracer("test");
        WorkflowTracer disabledTracer = new WorkflowTracer(noopTracer, false);
        WorkflowTracer enabledTracer = new WorkflowTracer(noopTracer, true);

        // Then: isEnabled should return correct values
        assertThat(disabledTracer.isEnabled()).isFalse();
        assertThat(enabledTracer.isEnabled()).isTrue();
    }

    @Test
    void trace_shouldHandleNullAttributes() {
        // Given: A tracer
        Tracer tracer = OpenTelemetry.noop().getTracer("test");
        WorkflowTracer workflowTracer = new WorkflowTracer(tracer, true);

        // When: Tracing with null attributes
        String result = workflowTracer.trace("test-operation", null, () -> "result");

        // Then: Should not throw and return result
        assertThat(result).isEqualTo("result");
    }
}
