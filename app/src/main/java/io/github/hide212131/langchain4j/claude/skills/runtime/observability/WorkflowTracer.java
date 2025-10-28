package io.github.hide212131.langchain4j.claude.skills.runtime.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

/**
 * Utility for tracing workflow stages (Plan, Act, Reflect) in OpenTelemetry.
 * Enables visualization of the complete processing flow and AgenticScope state.
 */
public final class WorkflowTracer {

    private final Tracer tracer;
    private final boolean enabled;

    public WorkflowTracer(OpenTelemetry openTelemetry, boolean enabled) {
        this.tracer = openTelemetry.getTracer("langchain4j-claude-skills-agent");
        this.enabled = enabled;
    }

    /**
     * Creates a span for a workflow stage (Plan, Act, or Reflect).
     * Returns a TracedStage that must be closed when the stage completes.
     */
    public TracedStage traceStage(String stageName) {
        if (!enabled) {
            return new TracedStage(null, null);
        }
        
        Span span = tracer.spanBuilder("workflow." + stageName.toLowerCase())
                .setParent(Context.current())
                .startSpan();
        Scope scope = span.makeCurrent();
        
        span.setAttribute("workflow.stage", stageName);
        
        return new TracedStage(span, scope);
    }

    /**
     * Represents a traced workflow stage.
     * Must be closed when the stage completes.
     */
    public static final class TracedStage implements AutoCloseable {
        private final Span span;
        private final Scope scope;

        private TracedStage(Span span, Scope scope) {
            this.span = span;
            this.scope = scope;
        }

        public void setAttribute(String key, String value) {
            if (span != null && value != null) {
                span.setAttribute(key, value);
            }
        }

        public void setAttribute(String key, long value) {
            if (span != null) {
                span.setAttribute(key, value);
            }
        }

        public void setAttribute(String key, boolean value) {
            if (span != null) {
                span.setAttribute(key, value);
            }
        }

        public void setSuccess() {
            if (span != null) {
                span.setStatus(StatusCode.OK);
            }
        }

        public void setError(String message) {
            if (span != null) {
                span.setStatus(StatusCode.ERROR, message);
            }
        }

        public void recordException(Exception e) {
            if (span != null) {
                span.recordException(e);
                span.setStatus(StatusCode.ERROR, e.getMessage());
            }
        }

        @Override
        public void close() {
            if (scope != null) {
                scope.close();
            }
            if (span != null) {
                span.end();
            }
        }
    }
}
