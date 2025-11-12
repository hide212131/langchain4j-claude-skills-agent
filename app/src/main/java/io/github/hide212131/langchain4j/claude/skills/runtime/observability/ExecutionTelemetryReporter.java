package io.github.hide212131.langchain4j.claude.skills.runtime.observability;

import io.github.hide212131.langchain4j.claude.skills.infra.logging.WorkflowLogger;
import io.github.hide212131.langchain4j.claude.skills.infra.observability.WorkflowTracer;
import io.github.hide212131.langchain4j.claude.skills.runtime.provider.LangChain4jLlmClient;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.AgentService;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.act.DefaultInvoker;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.plan.PlanModels;

import io.opentelemetry.api.trace.Span;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Emits workflow-level observability for the complete Plan → Act → Reflect execution.
 * Consolidates logging and tracing so downstream dashboards receive a single summary signal.
 */
public final class ExecutionTelemetryReporter {

    private static final int MAX_PREVIEW = 4096;

    private final WorkflowTracer tracer;
    private final WorkflowLogger logger;

    public ExecutionTelemetryReporter(WorkflowTracer tracer, WorkflowLogger logger) {
        this.tracer = Objects.requireNonNull(tracer, "tracer");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void report(
            AgentService.AgentRunRequest request,
            PlanModels.PlanResult finalPlan,
            LangChain4jLlmClient.CompletionResult finalPlanCompletion,
            DefaultInvoker.ActResult finalActResult,
            LangChain4jLlmClient.ProviderMetrics metricsSnapshot,
            int stageVisitCount) {

        String planSkills = joinComma(finalPlan == null ? List.of() : finalPlan.orderedSkillIds());
        String invokedSkills = finalActResult == null ? "" : joinComma(finalActResult.invokedSkills());
        String artifactPath = finalActResult != null && finalActResult.hasArtifact()
                ? finalActResult.finalArtifact().toString()
                : "";

        ResultDescriptor descriptor = describeResult(finalPlanCompletion, finalActResult, finalPlan);

        Map<String, String> eventAttributes = new LinkedHashMap<>();
        eventAttributes.put("workflow.goal", request.goal());
        eventAttributes.put("workflow.result_type", descriptor.type());
        eventAttributes.put("workflow.stage_visits", Integer.toString(stageVisitCount));
        if (!planSkills.isEmpty()) {
            eventAttributes.put("plan.skills", planSkills);
        }
        if (!descriptor.output().isEmpty()) {
            eventAttributes.put("workflow.output_preview", descriptor.output());
        }
        if (!invokedSkills.isEmpty()) {
            eventAttributes.put("act.invoked_skills", invokedSkills);
        }
        if (!artifactPath.isEmpty()) {
            eventAttributes.put("act.artifact_path", artifactPath);
        }
        appendMetricAttributes(eventAttributes, metricsSnapshot);
        tracer.addEvent("execution.summary", eventAttributes);

        logSummary(request.goal(), descriptor, planSkills, invokedSkills, artifactPath, metricsSnapshot, stageVisitCount);
        annotateRootSpan(request.goal(), descriptor, planSkills, invokedSkills, artifactPath, metricsSnapshot, stageVisitCount);
    }

    private void logSummary(
            String goal,
            ResultDescriptor descriptor,
            String planSkills,
            String invokedSkills,
            String artifactPath,
            LangChain4jLlmClient.ProviderMetrics metricsSnapshot,
            int stageVisitCount) {

        long totalDuration = metricsSnapshot != null ? metricsSnapshot.totalDurationMs() : 0L;
        int callCount = metricsSnapshot != null ? metricsSnapshot.callCount() : 0;
        int inputTokens = metricsSnapshot != null ? metricsSnapshot.totalInputTokens() : 0;
        int outputTokens = metricsSnapshot != null ? metricsSnapshot.totalOutputTokens() : 0;

        logger.info(
                "Execution summary goal='{}' resultType={} planSkills={} invokedSkills={} artifact={} stageVisits={} llmCalls={} tokens(in/out)={}/{} durationMs={}",
                goal,
                descriptor.type(),
                planSkills,
                invokedSkills,
                artifactPath,
                stageVisitCount,
                callCount,
                inputTokens,
                outputTokens,
                totalDuration);
    }

    private void annotateRootSpan(
            String goal,
            ResultDescriptor descriptor,
            String planSkills,
            String invokedSkills,
            String artifactPath,
            LangChain4jLlmClient.ProviderMetrics metricsSnapshot,
            int stageVisitCount) {

        Span span = Span.current();
        if (!span.getSpanContext().isValid() || !span.isRecording()) {
            return;
        }
        span.setAttribute("workflow.execution.goal", goal);
        span.setAttribute("workflow.execution.result_type", descriptor.type());
        span.setAttribute("workflow.execution.stage_visits", stageVisitCount);
        if (!descriptor.output().isEmpty()) {
            span.setAttribute("workflow.execution.output", descriptor.output());
        }
        if (!planSkills.isEmpty()) {
            span.setAttribute("workflow.execution.plan_skills", planSkills);
        }
        if (!invokedSkills.isEmpty()) {
            span.setAttribute("workflow.execution.invoked_skills", invokedSkills);
        }
        if (!artifactPath.isEmpty()) {
            span.setAttribute("workflow.execution.artifact_path", artifactPath);
        }
        if (metricsSnapshot != null) {
            span.setAttribute("workflow.execution.llm.call_count", metricsSnapshot.callCount());
            span.setAttribute("workflow.execution.llm.tokens_input", metricsSnapshot.totalInputTokens());
            span.setAttribute("workflow.execution.llm.tokens_output", metricsSnapshot.totalOutputTokens());
            span.setAttribute("workflow.execution.llm.tokens_total", metricsSnapshot.totalTokenCount());
            span.setAttribute("workflow.execution.llm.duration_ms", metricsSnapshot.totalDurationMs());
        }
    }

    private static ResultDescriptor describeResult(
            LangChain4jLlmClient.CompletionResult planCompletion,
            DefaultInvoker.ActResult actResult,
            PlanModels.PlanResult plan) {

        if (actResult != null) {
            if (actResult.hasArtifact()) {
                return new ResultDescriptor("artifact", actResult.finalArtifact().toString());
            }
            if (!actResult.invokedSkills().isEmpty()) {
                return new ResultDescriptor("act-invocations", joinComma(actResult.invokedSkills()));
            }
        }
        if (planCompletion != null && hasText(planCompletion.content())) {
            return new ResultDescriptor("plan-draft", limit(planCompletion.content()));
        }
        if (plan != null && !plan.orderedSkillIds().isEmpty()) {
            return new ResultDescriptor("plan", joinComma(plan.orderedSkillIds()));
        }
        return new ResultDescriptor("none", "");
    }

    private static void appendMetricAttributes(
            Map<String, String> attributes,
            LangChain4jLlmClient.ProviderMetrics metrics) {
        if (metrics == null) {
            return;
        }
        attributes.put("llm.calls", Integer.toString(metrics.callCount()));
        attributes.put("llm.tokens_input", Integer.toString(metrics.totalInputTokens()));
        attributes.put("llm.tokens_output", Integer.toString(metrics.totalOutputTokens()));
        attributes.put("llm.tokens_total", Integer.toString(metrics.totalTokenCount()));
        attributes.put("llm.duration_ms", Long.toString(metrics.totalDurationMs()));
    }

    private static String joinComma(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return String.join(",", values);
    }

    private static String limit(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.strip();
        if (trimmed.length() <= MAX_PREVIEW) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_PREVIEW);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record ResultDescriptor(String type, String output) {
        private ResultDescriptor {
            type = Objects.requireNonNull(type, "type");
            output = output == null ? "" : output;
        }
    }
}
