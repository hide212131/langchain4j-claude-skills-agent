package io.github.hide212131.langchain4j.claude.skills.runtime.observability;

import static io.github.hide212131.langchain4j.claude.skills.runtime.observability.WorkflowTelemetry.setGenAiAttributes;

import dev.langchain4j.agentic.agent.AgentRequest;
import dev.langchain4j.agentic.agent.AgentResponse;
import dev.langchain4j.agentic.scope.AgenticScope;
import io.github.hide212131.langchain4j.claude.skills.infra.observability.WorkflowTracer;
import io.github.hide212131.langchain4j.claude.skills.runtime.provider.LangChain4jLlmClient;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.AgentService;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.WorkflowStateKeys;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.act.DefaultInvoker;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.plan.PlanModels;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.reflect.ReflectEvaluator;
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
        Scope scope = span.makeCurrent();
        applyStageInputAttributes(stage, span, request.agenticScope());
        stageStack.push(new StageContext(stage, span, scope));
    }

    public void afterAgentInvocation(AgentResponse response) {
        StageContext context = stageStack.poll();
        if (context == null) {
            return;
        }
        try {
            applyStageOutputAttributes(context.stage(), context.span(), response.agenticScope());
            context.span().setStatus(StatusCode.OK);
        } catch (RuntimeException ex) {
            context.span().setStatus(StatusCode.ERROR, ex.getMessage());
            context.span().recordException(ex);
            throw ex;
        } finally {
            context.scope().close();
            context.span().end();
        }
    }

    public void onStageError(Throwable error) {
        StageContext context = stageStack.poll();
        if (context == null) {
            return;
        }
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
            case "plan" -> {
                span.setAttribute("input", runRequest.goal());
                span.setAttribute("workflow.dryRun", runRequest.dryRun());
                setGenAiAttributes(span, runRequest.goal(), null);
            }
            case "act" -> {
                PlanModels.PlanResult plan = readPlan(scope);
                if (plan != null) {
                    String skills = String.join(",", plan.orderedSkillIds());
                    span.setAttribute("input", skills);
                    setGenAiAttributes(span, skills, null);
                }
            }
            case "reflect" -> {
                DefaultInvoker.ActResult actResult = readAct(scope);
                if (actResult != null) {
                    String input = actResult.hasArtifact()
                            ? actResult.finalArtifact().toString()
                            : String.join(",", actResult.invokedSkills());
                    if (!input.isBlank()) {
                        span.setAttribute("input", input);
                        setGenAiAttributes(span, input, null);
                    }
                }
            }
            default -> { /* no-op */ }
        }
    }

    private void applyStageOutputAttributes(String stage, Span span, AgenticScope scope) {
        switch (stage) {
            case "plan" -> handlePlanSpan(span, scope);
            case "act" -> handleActSpan(span, scope);
            case "reflect" -> handleReflectSpan(span, scope);
            default -> { /* no-op */ }
        }
    }

    private void handlePlanSpan(Span span, AgenticScope scope) {
        PlanModels.PlanResult plan = readPlan(scope);
        if (plan != null) {
            List<String> skills = plan.orderedSkillIds();
            if (!skills.isEmpty()) {
                String selected = String.join(",", skills);
                span.setAttribute("plan.selectedSkills", selected);
                span.setAttribute("plan.skillCount", skills.size());
            }
        }
        LangChain4jLlmClient.CompletionResult draft = readPlanDraft(scope);
        String draftText = draft != null && draft.content() != null
                ? draft.content()
                : runRequest.forcedSkillIds().isEmpty()
                        ? ""
                        : "Planner bypassed via forced skill sequence.";
        span.setAttribute("output", draftText);
        setGenAiAttributes(span, runRequest.goal(), draftText);
    }

    private void handleActSpan(Span span, AgenticScope scope) {
        DefaultInvoker.ActResult actResult = readAct(scope);
        PlanModels.PlanResult plan = readPlan(scope);
        String input = plan != null ? String.join(",", plan.orderedSkillIds()) : "";
        if (actResult == null) {
            return;
        }
        String invoked = String.join(",", actResult.invokedSkills());
        span.setAttribute("act.invokedSkills", invoked);
        span.setAttribute("act.skillCallCount", actResult.invokedSkills().size());
        String output = actResult.hasArtifact()
                ? actResult.finalArtifact().toString()
                : invoked;
        span.setAttribute("output", output);
        setGenAiAttributes(span, input, output);
    }

    private void handleReflectSpan(Span span, AgenticScope scope) {
        ReflectEvaluator.EvaluationResult evaluation = readEvaluation(scope);
        if (evaluation == null) {
            return;
        }
        String summary = evaluation.finalSummary();
        span.setAttribute("reflect.needsRetry", evaluation.needsRetry());
        span.setAttribute("reflect.success", evaluation.success());
        span.setAttribute("output", summary == null ? "" : summary);
        setGenAiAttributes(span, runRequest.goal(), summary);
    }

    private PlanModels.PlanResult readPlan(AgenticScope scope) {
        if (scope != null && scope.hasState(WorkflowStateKeys.PLAN_RESULT)) {
            return (PlanModels.PlanResult) scope.readState(WorkflowStateKeys.PLAN_RESULT);
        }
        return null;
    }

    private LangChain4jLlmClient.CompletionResult readPlanDraft(AgenticScope scope) {
        if (scope != null && scope.hasState(WorkflowStateKeys.PLAN_DRAFT)) {
            return (LangChain4jLlmClient.CompletionResult) scope.readState(WorkflowStateKeys.PLAN_DRAFT);
        }
        return null;
    }

    private DefaultInvoker.ActResult readAct(AgenticScope scope) {
        if (scope != null && scope.hasState(WorkflowStateKeys.ACT_RESULT)) {
            return (DefaultInvoker.ActResult) scope.readState(WorkflowStateKeys.ACT_RESULT);
        }
        return null;
    }

    private ReflectEvaluator.EvaluationResult readEvaluation(AgenticScope scope) {
        if (scope != null && scope.hasState(WorkflowStateKeys.REFLECT_RESULT)) {
            return (ReflectEvaluator.EvaluationResult) scope.readState(WorkflowStateKeys.REFLECT_RESULT);
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

    private record StageContext(String stage, Span span, Scope scope) {}
}
