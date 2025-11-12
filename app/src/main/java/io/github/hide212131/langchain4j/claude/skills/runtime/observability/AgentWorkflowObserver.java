package io.github.hide212131.langchain4j.claude.skills.runtime.observability;

import dev.langchain4j.agentic.agent.AgentRequest;
import dev.langchain4j.agentic.agent.AgentResponse;
import dev.langchain4j.agentic.scope.AgenticScope;
import io.github.hide212131.langchain4j.claude.skills.infra.observability.WorkflowTracer;
import io.github.hide212131.langchain4j.claude.skills.runtime.provider.LangChain4jLlmClient;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.AgentService;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.WorkflowStateKeys;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.act.DefaultInvoker;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.plan.PlanModels;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Observes workflow stage transitions via before/after agent invocation hooks.
 * Responsible for span lifecycle and stage visit bookkeeping.
 */
public final class AgentWorkflowObserver {

    private final WorkflowTracer tracer;
    private final AgentService.AgentRunRequest runRequest;
    private final int attemptNumber;
    private final List<AgentService.StageVisit> visits = new ArrayList<>();
    private final Deque<StageContext> stageStack = new ArrayDeque<>();

    public AgentWorkflowObserver(
            WorkflowTracer tracer,
            AgentService.AgentRunRequest runRequest,
            int attemptNumber) {
        this.tracer = Objects.requireNonNull(tracer, "tracer");
        this.runRequest = Objects.requireNonNull(runRequest, "runRequest");
        this.attemptNumber = attemptNumber;
    }

    public void beforeAgentInvocation(AgentRequest request) {
        String stage = request.agentName();
        visits.add(new AgentService.StageVisit(attemptNumber, stage));
        Span span = tracer.startSpan("workflow." + stage, Map.of(
                "attempt", attemptNumber,
                "stage", stage));
        Scope otelScope = span.makeCurrent();
        applyStageInputAttributes(stage, span, request.agenticScope());
        emitScopeSnapshot("input", stage, span, request.agenticScope());
        AgenticScope previousScope = AgenticScopeContext.set(request.agenticScope());
        stageStack.push(new StageContext(stage, span, otelScope, request.agenticScope(), previousScope));
    }

    public void afterAgentInvocation(AgentResponse response) {
        StageContext context = stageStack.poll();
        if (context == null) {
            return;
        }
        try {
            applyStageOutputAttributes(context.stage(), context.span(), response.agenticScope());
            emitScopeSnapshot("output", context.stage(), context.span(), response.agenticScope());
            context.span().setStatus(StatusCode.OK);
        } catch (RuntimeException ex) {
            context.span().setStatus(StatusCode.ERROR, ex.getMessage());
            context.span().recordException(ex);
            throw ex;
        } finally {
            AgenticScopeContext.restore(context.previousScope());
            context.scope().close();
            context.span().end();
        }
    }

    public void onStageError(Throwable error) {
        StageContext context = stageStack.poll();
        if (context == null) {
            return;
        }
        emitScopeSnapshot("error", context.stage(), context.span(), context.agenticScope());
        AgenticScopeContext.restore(context.previousScope());
        context.scope().close();
        context.span().recordException(error);
        context.span().setStatus(StatusCode.ERROR, safeMessage(error));
        context.span().end();
    }

    public List<AgentService.StageVisit> visits() {
        return List.copyOf(visits);
    }

    private void applyStageInputAttributes(String stage, Span span, AgenticScope scope) {
        switch (stage) {
            case "plan" -> annotatePlanInput(span);
            case "act" -> annotateActInput(span, scope);
            default -> { /* no-op */ }
        }
    }

    private void applyStageOutputAttributes(String stage, Span span, AgenticScope scope) {
        switch (stage) {
            case "plan" -> handlePlanSpan(span, scope);
            case "act" -> handleActSpan(span, scope);
            default -> { /* no-op */ }
        }
    }

    private void handlePlanSpan(Span span, AgenticScope scope) {
        PlanModels.PlanResult plan = readPlan(scope);
        if (plan != null) {
            List<String> skills = plan.orderedSkillIds();
            if (!skills.isEmpty()) {
                String selected = String.join(",", skills);
                span.setAttribute("workflow.plan.selected_skills", selected);
                span.setAttribute("workflow.plan.skill_count", skills.size());
            }
            if (hasText(plan.systemPromptSummary())) {
                span.setAttribute("workflow.plan.summary", plan.systemPromptSummary());
            }
        }
        LangChain4jLlmClient.CompletionResult draft = readPlanDraft(scope);
        if (draft != null && hasText(draft.content())) {
            span.setAttribute("workflow.plan.assistant_draft", draft.content());
        } else if (!runRequest.forcedSkillIds().isEmpty()) {
            span.setAttribute("workflow.plan.assistant_draft", "Planner bypassed via forced skill sequence.");
        }
    }

    private void handleActSpan(Span span, AgenticScope scope) {
        DefaultInvoker.ActResult actResult = readAct(scope);
        if (actResult == null) {
            return;
        }
        String invoked = String.join(",", actResult.invokedSkills());
        if (!invoked.isEmpty()) {
            span.setAttribute("workflow.act.invoked_skills", invoked);
            span.setAttribute("workflow.act.skill_call_count", actResult.invokedSkills().size());
        }
        if (actResult.hasArtifact()) {
            span.setAttribute("workflow.act.artifact_path", actResult.finalArtifact().toString());
        }
        String output = actResult.hasArtifact()
                ? actResult.finalArtifact().toString()
                : invoked;
        if (hasText(output)) {
            span.setAttribute("workflow.act.output", output);
        }
    }



    private void annotatePlanInput(Span span) {
        span.setAttribute("workflow.plan.goal", runRequest.goal());
        span.setAttribute("workflow.plan.dry_run", runRequest.dryRun());
        if (!runRequest.forcedSkillIds().isEmpty()) {
            span.setAttribute("workflow.plan.forced_skills", String.join(",", runRequest.forcedSkillIds()));
            span.setAttribute("workflow.plan.forced_skill_count", runRequest.forcedSkillIds().size());
        }
    }

    private void annotateActInput(Span span, AgenticScope scope) {
        PlanModels.PlanResult plan = readPlan(scope);
        if (plan == null || plan.orderedSkillIds().isEmpty()) {
            return;
        }
        span.setAttribute("workflow.act.plan_skills", String.join(",", plan.orderedSkillIds()));
        span.setAttribute("workflow.act.plan_skill_count", plan.orderedSkillIds().size());
    }



    private PlanModels.PlanResult readPlan(AgenticScope scope) {
        if (scope != null && WorkflowStateKeys.PLAN_RESULT.exists(scope)) {
            return WorkflowStateKeys.PLAN_RESULT.readOrNull(scope);
        }
        return null;
    }

    private LangChain4jLlmClient.CompletionResult readPlanDraft(AgenticScope scope) {
        if (scope != null && WorkflowStateKeys.PLAN_DRAFT.exists(scope)) {
            return WorkflowStateKeys.PLAN_DRAFT.readOrNull(scope);
        }
        return null;
    }

    private DefaultInvoker.ActResult readAct(AgenticScope scope) {
        if (scope != null && WorkflowStateKeys.ACT_RESULT.exists(scope)) {
            return WorkflowStateKeys.ACT_RESULT.readOrNull(scope);
        }
        return null;
    }



    private String safeMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        return message;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void emitScopeSnapshot(String phase, String stage, Span span, AgenticScope scope) {
        if (span == null || scope == null || !span.isRecording()) {
            return;
        }
        AgenticScopeSnapshots.snapshot(scope).ifPresent(snapshot -> {
            String eventName = "agentic.scope." + phase;
            span.addEvent(
                    eventName,
                    Attributes.builder()
                            .put("stage", stage)
                            .put("phase", phase)
                            .put("attempt", attemptNumber)
                            .put("state", snapshot)
                            .build());
            span.setAttribute(eventName, snapshot);
        });
    }

    private record StageContext(
            String stage, Span span, Scope scope, AgenticScope agenticScope, AgenticScope previousScope) {}
}
