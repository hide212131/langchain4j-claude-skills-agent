package io.github.hide212131.langchain4j.claude.skills.infra.observability;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Helper class for creating workflow-level traces for Plan, Act, and Reflect stages.
 */
public final class WorkflowTracer {

    private final Tracer tracer;
    private final boolean enabled;

    public WorkflowTracer(Tracer tracer, boolean enabled) {
        this.tracer = tracer;
        this.enabled = enabled;
    }

    /**
     * Executes an operation with tracing.
     */
    public <T> T trace(String operationName, Map<String, Object> attributes, Supplier<T> operation) {
        if (!enabled) {
            return operation.get();
        }

        Span span = tracer.spanBuilder(operationName)
            .setSpanKind(SpanKind.INTERNAL)
            .startSpan();

        // Add attributes
        if (attributes != null) {
            attributes.forEach((key, value) -> {
                if (value instanceof String str) {
                    span.setAttribute(key, str);
                } else if (value instanceof Long l) {
                    span.setAttribute(key, l);
                } else if (value instanceof Integer i) {
                    span.setAttribute(key, i.longValue());
                } else if (value instanceof Boolean b) {
                    span.setAttribute(key, b);
                } else if (value != null) {
                    span.setAttribute(key, value.toString());
                }
            });
        }

        try (Scope scope = span.makeCurrent()) {
            T result = operation.get();
            span.setStatus(StatusCode.OK);
            return result;
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    /**
     * Executes a void operation with tracing.
     */
    public void trace(String operationName, Map<String, Object> attributes, Runnable operation) {
        trace(operationName, attributes, () -> {
            operation.run();
            return null;
        });
    }

    /**
     * Creates a new span and returns it for manual management.
     */
    public Span startSpan(String operationName, Map<String, Object> attributes) {
        if (!enabled) {
            return Span.getInvalid();
        }

        Span span = tracer.spanBuilder(operationName)
            .setSpanKind(SpanKind.INTERNAL)
            .startSpan();

        if (attributes != null) {
            attributes.forEach((key, value) -> {
                if (value instanceof String str) {
                    span.setAttribute(key, str);
                } else if (value instanceof Long l) {
                    span.setAttribute(key, l);
                } else if (value instanceof Integer i) {
                    span.setAttribute(key, i.longValue());
                } else if (value instanceof Boolean b) {
                    span.setAttribute(key, b);
                } else if (value != null) {
                    span.setAttribute(key, value.toString());
                }
            });
        }

        return span;
    }

    /**
     * Adds an event to the current span.
     */
    public void addEvent(String eventName, Map<String, String> attributes) {
        if (!enabled) {
            return;
        }

        Span currentSpan = Span.current();
        if (currentSpan.isRecording()) {
            if (attributes == null || attributes.isEmpty()) {
                currentSpan.addEvent(eventName);
            } else {
                io.opentelemetry.api.common.AttributesBuilder builder = 
                    io.opentelemetry.api.common.Attributes.builder();
                attributes.forEach(builder::put);
                currentSpan.addEvent(eventName, builder.build());
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}
