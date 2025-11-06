package io.github.hide212131.langchain4j.claude.skills.runtime.observability;

import io.opentelemetry.api.trace.Span;

/**
 * Helper for setting common GenAI telemetry attributes on spans.
 */
public final class WorkflowTelemetry {

    private WorkflowTelemetry() {}

    public static void setGenAiAttributes(Span span, String input, String output) {
        if (span == null) {
            return;
        }
        if (input != null && !input.isBlank()) {
            span.setAttribute("gen_ai.request.prompt", input);
        }
        if (output != null && !output.isBlank()) {
            span.setAttribute("gen_ai.response.completion", output);
        }
    }
}
