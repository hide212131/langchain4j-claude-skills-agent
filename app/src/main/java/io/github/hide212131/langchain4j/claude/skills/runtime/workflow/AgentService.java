package io.github.hide212131.langchain4j.claude.skills.runtime.workflow;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.github.hide212131.langchain4j.claude.skills.infra.logging.WorkflowLogger;
import io.github.hide212131.langchain4j.claude.skills.infra.observability.ObservabilityConfig;
import io.github.hide212131.langchain4j.claude.skills.infra.observability.WorkflowTracer;
import io.github.hide212131.langchain4j.claude.skills.runtime.observability.InstrumentedChatModel;
import io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.BlackboardStore;
import io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.PlanCandidateStepsState;
import io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.PlanConstraintsState;
import io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.PlanEvaluationCriteriaState;
import io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.PlanInputsState;
import io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.PlanState;
import io.github.hide212131.langchain4j.claude.skills.runtime.guard.SkillInvocationGuard;
import io.github.hide212131.langchain4j.claude.skills.runtime.provider.LangChain4jLlmClient;
import io.github.hide212131.langchain4j.claude.skills.runtime.skill.DryRunSkillRuntimeOrchestrator;
import io.github.hide212131.langchain4j.claude.skills.runtime.skill.SkillIndex;
import io.github.hide212131.langchain4j.claude.skills.runtime.skill.SkillRuntime;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.act.DefaultInvoker;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.act.DefaultInvoker.ActResult;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.act.InvokeSkillTool;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.plan.AgenticPlanner;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.plan.PlanModels;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.reflect.DefaultEvaluator;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.reflect.ReflectEvaluator;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.support.WorkflowFactory;
import io.opentelemetry.api.trace.Span;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * High-level entry point that executes the Plan → Act → Reflect workflow.
 */
public final class AgentService {

    private final WorkflowFactory workflowFactory;
    private final LangChain4jLlmClient llmClient;
    private final WorkflowLogger logger;
    private final AgenticPlanner planner;
    private final DefaultInvoker invoker;
    private final BlackboardStore blackboardStore;
    private final ReflectEvaluator evaluator;
    private final WorkflowTracer tracer;

    public static AgentService withDefaults(
            WorkflowFactory workflowFactory,
            LangChain4jLlmClient llmClient,
            SkillIndex skillIndex) {
        return withDefaults(workflowFactory, llmClient, skillIndex, false);
    }

    public static AgentService withDefaults(
            WorkflowFactory workflowFactory,
            LangChain4jLlmClient llmClient,
            SkillIndex skillIndex,
            boolean dryRun) {
        WorkflowLogger logger = new WorkflowLogger();
        BlackboardStore blackboardStore = new BlackboardStore();
        ObservabilityConfig observability = ObservabilityConfig.fromEnvironment();
        WorkflowTracer tracer = new WorkflowTracer(observability.tracer(), observability.isEnabled());
        ChatModel runtimeChatModel = llmClient.chatModel();
        if (!dryRun && observability.isEnabled()) {
            runtimeChatModel = new InstrumentedChatModel(
                    runtimeChatModel,
                    observability.tracer(),
                    determineProvider(runtimeChatModel),
                    llmClient.defaultModelName());
        }
        SkillRuntime runtime = dryRun
                ? new SkillRuntime(
                        skillIndex,
                        Path.of("build", "out"),
                        logger,
                        new DryRunSkillRuntimeOrchestrator())
                : new SkillRuntime(
                        skillIndex,
                        Path.of("build", "out"),
                        logger,
                        runtimeChatModel);
        InvokeSkillTool tool = new InvokeSkillTool(runtime);
        SkillInvocationGuard guard = new SkillInvocationGuard();
        DefaultInvoker invoker = new DefaultInvoker(tool, guard, blackboardStore, logger);
        ReflectEvaluator evaluator = new DefaultEvaluator(blackboardStore, logger);
        return new AgentService(
                workflowFactory,
                llmClient,
                skillIndex,
                logger,
                invoker,
                blackboardStore,
                evaluator,
                new AgenticPlanner(skillIndex, llmClient, logger),
                tracer);
    }

    public AgentService(
            WorkflowFactory workflowFactory,
            LangChain4jLlmClient llmClient,
            SkillIndex skillIndex,
            WorkflowLogger logger,
            DefaultInvoker invoker,
            BlackboardStore blackboardStore,
            ReflectEvaluator evaluator,
            AgenticPlanner planner,
            WorkflowTracer tracer) {
        this.workflowFactory = Objects.requireNonNull(workflowFactory, "workflowFactory");
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
        this.logger = Objects.requireNonNull(logger, "logger");
        Objects.requireNonNull(skillIndex, "skillIndex");
        this.invoker = Objects.requireNonNull(invoker, "invoker");
        this.blackboardStore = Objects.requireNonNull(blackboardStore, "blackboardStore");
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
        this.planner = Objects.requireNonNull(planner, "planner");
        this.tracer = Objects.requireNonNull(tracer, "tracer");
    }

    public ExecutionResult run(AgentRunRequest request) {
        Objects.requireNonNull(request, "request");

        return tracer.trace("agent.execution", Map.ofEntries(
                Map.entry("goal", request.goal()),
                Map.entry("input", request.goal()),
                Map.entry("dryRun", request.dryRun()),
                Map.entry("forcedSkillIds", request.forcedSkillIds().stream()
                        .filter(Objects::nonNull)
                        .collect(java.util.stream.Collectors.joining(",")))),
                () -> {
                    blackboardStore.clear();
                    List<StageVisit> stageVisits = new ArrayList<>();
                    Span.current().setAttribute("input", request.goal());
                    LangChain4jLlmClient.CompletionResult finalPlanCompletion = null;
                    PlanModels.PlanResult finalPlan = null;
                    ActResult finalActResult = null;
                    ReflectEvaluator.EvaluationResult finalEvaluation = null;
                    int maxAttempts = 2;

                    for (int attempt = 0; attempt < maxAttempts; attempt++) {
                        int attemptIndex = attempt;
                        int attemptNumber = attemptIndex + 1;
                        AtomicReference<LangChain4jLlmClient.CompletionResult> planResultRef = new AtomicReference<>();
                        AtomicReference<PlanModels.PlanResult> planRef = new AtomicReference<>();
                        AtomicReference<ActResult> actRef = new AtomicReference<>();
                        AtomicReference<ReflectEvaluator.EvaluationResult> evaluationRef = new AtomicReference<>();

                        UntypedAgent workflow = workflowFactory.createWorkflow(builder -> {
                            builder.name("skills-plan-act-reflect");
                            builder.output(scope -> scope.state());
                            builder.subAgents(
                                    AgenticServices.agentAction(scope -> {
                                        tracer.trace("workflow.plan", Map.of(
                                                "attempt", attemptNumber,
                                                "goal", request.goal(),
                                                "mode", request.dryRun() ? "dry-run" : "live"), () -> {
                                                    Span.current().setAttribute("input", request.goal());
                                                    setGenAiAttributes(Span.current(), request.goal(), null);
                                                    stageVisits.add(new StageVisit(attemptNumber, "plan"));
                                                    writePlanGoal(scope, request.goal());
                                                    scope.writeState(
                                                            PlanInputsState.KEY,
                                                            Map.of(
                                                                    "goal", request.goal(),
                                                                    "mode", request.dryRun() ? "dry-run" : "live",
                                                                    "attempt", attemptNumber));
                                                    scope.writeState(
                                                            PlanConstraintsState.KEY,
                                                            Map.of(
                                                                    "tokenBudget", 0,
                                                                    "maxToolCalls", 3,
                                                                    "maxAttempts", maxAttempts,
                                                                    "attempt", attemptNumber));
                                                    List<String> forcedSkillIds = request.forcedSkillIds();
                                                    boolean hasForcedSkillIds = !forcedSkillIds.isEmpty();
                                                    PlanModels.PlanResult plan = hasForcedSkillIds
                                                            ? planner.planWithFixedOrder(request.goal(), forcedSkillIds)
                                                            : planner.plan(request.goal());
                                                    planRef.set(plan);
                                                    Span.current().setAttribute(
                                                            "plan.selectedSkills",
                                                            String.join(",", plan.orderedSkillIds()));
                                                    scope.writeState(
                                                            PlanCandidateStepsState.KEY,
                                                            plan.steps().stream()
                                                                    .map(step -> Map.of(
                                                                            "skillId", step.skillId(),
                                                                            "name", step.name(),
                                                                            "description", step.description(),
                                                                            "keywords", step.keywords(),
                                                                            "skillRoot", step.skillRoot().toString()))
                                                                    .toList());
                                                    LangChain4jLlmClient.CompletionResult completion = null;
                                                    String assistantDraft;
                                                    if (hasForcedSkillIds) {
                                                        assistantDraft = "Planner bypassed via forced skill sequence.";
                                                        logger.info(
                                                                "Plan attempt {} using forced skill sequence {}",
                                                                attemptNumber,
                                                                plan.orderedSkillIds());
                                                    } else {
                                                        completion = llmClient.complete(request.goal());
                                                        planResultRef.set(completion);
                                                        assistantDraft = completion != null ? completion.content() : "";
                                                        logger.info(
                                                                "Plan attempt {} candidate steps: {}",
                                                                attemptNumber,
                                                                plan.orderedSkillIds());
                                                    }
                                                    Span.current().setAttribute("output", assistantDraft);
                                                    setGenAiAttributes(Span.current(), request.goal(), assistantDraft);
                                                    scope.writeState(
                                                            PlanEvaluationCriteriaState.KEY,
                                                            Map.of(
                                                                    "systemPromptL1", plan.systemPromptSummary(),
                                                                    "assistantDraft",
                                                                    assistantDraft,
                                                                    "attempt", attemptNumber));

                                                    // Add tracing attributes for plan results
                                                    tracer.addEvent("plan.completed", Map.of(
                                                            "selectedSkills", String.join(",", plan.orderedSkillIds()),
                                                            "skillCount",
                                                            String.valueOf(plan.orderedSkillIds().size())));
                                                });
                                    }),
                                    AgenticServices.agentAction(scope -> {
                                        tracer.trace("workflow.act", Map.of(
                                                "attempt", attemptNumber), () -> {
                                                    stageVisits.add(new StageVisit(attemptNumber, "act"));
                                                    PlanModels.PlanResult plan = planRef.get();
                                                    if (plan == null) {
                                                        throw new IllegalStateException(
                                                                "Plan stage must complete before Act");
                                                    }
                                                    Span.current().setAttribute(
                                                            "input",
                                                            String.join(",", plan.orderedSkillIds()));
                                                    ActResult result = invoker.invoke(scope, plan);
                                                    actRef.set(result);
                                                    Span.current().setAttribute(
                                                            "output",
                                                            result.hasArtifact()
                                                                    ? result.finalArtifact().toString()
                                                                    : String.join(",", result.invokedSkills()));
                                                    setGenAiAttributes(
                                                            Span.current(),
                                                            String.join(",", plan.orderedSkillIds()),
                                                            result.hasArtifact()
                                                                    ? result.finalArtifact().toString()
                                                                    : String.join(",", result.invokedSkills()));

                                                    // Add tracing attributes for act results
                                                    tracer.addEvent("act.completed", Map.of(
                                                            "invokedSkills", String.join(",", result.invokedSkills()),
                                                            "hasArtifact", String.valueOf(result.hasArtifact())));
                                                });
                                    }),
                                    AgenticServices.agentAction(scope -> {
                                        tracer.trace("workflow.reflect", Map.of(
                                                "attempt", attemptNumber), () -> {
                                                    stageVisits.add(new StageVisit(attemptNumber, "reflect"));
                                                    PlanModels.PlanResult plan = planRef.get();
                                                    ReflectEvaluator.EvaluationResult evaluation = evaluator.evaluate(
                                                            scope, plan, actRef.get(), attemptIndex, maxAttempts);
                                                    evaluationRef.set(evaluation);
                                                    if (plan != null) {
                                                        Span.current().setAttribute(
                                                                "input",
                                                                String.join(",", plan.orderedSkillIds()));
                                                    }
                                                    if (evaluation != null) {
                                                        Span.current().setAttribute(
                                                                "output",
                                                                evaluation.finalSummary());
                                                        setGenAiAttributes(
                                                                Span.current(),
                                                                plan != null
                                                                        ? String.join(",", plan.orderedSkillIds())
                                                                        : null,
                                                                evaluation.finalSummary());
                                                    }

                                                    // Add tracing attributes for reflect results
                                                    tracer.addEvent("reflect.completed", Map.of(
                                                            "needsRetry", String.valueOf(evaluation.needsRetry()),
                                                            "summary", evaluation.finalSummary()));
                                                });
                                    }));
                        });

                        workflow.invoke(Map.of("goal", request.goal()));

                        finalPlanCompletion = planResultRef.get();
                        finalPlan = planRef.get();
                        finalActResult = actRef.get();
                        finalEvaluation = evaluationRef.get();

                        if (finalEvaluation == null || !finalEvaluation.needsRetry()) {
                            break;
                        }

                        if (attemptNumber < maxAttempts) {
                            logger.info(
                                    "Reflect requested retry after attempt {} — clearing blackboard and retrying.",
                                    attemptNumber);
                            blackboardStore.clear();
                        }
                    }

                    LangChain4jLlmClient.ProviderMetrics metricsSnapshot = llmClient.metrics();
                    if (finalPlanCompletion != null) {
                        logger.info(
                                "Assistant response: {} (tokens in/out={}, durationMs={})",
                                finalPlanCompletion.content(),
                                finalPlanCompletion.tokenUsage(),
                                finalPlanCompletion.durationMs());
                    }
                    if (metricsSnapshot.callCount() > 0) {
                        logger.info(
                                "LLM usage summary: calls={}, tokens_in={}, tokens_out={}, durationMs={}",
                                metricsSnapshot.callCount(),
                                metricsSnapshot.totalInputTokens(),
                                metricsSnapshot.totalOutputTokens(),
                                metricsSnapshot.totalDurationMs());
                    }
                    if (finalActResult != null) {
                        logger.info("Act stage invoked skills: {}", finalActResult.invokedSkills());
                        if (finalActResult.hasArtifact()) {
                            logger.info("Final artefact generated at {}", finalActResult.finalArtifact());
                        }
                    }
                    if (finalEvaluation != null) {
                        logger.info("Reflect summary: {}", finalEvaluation.finalSummary());
                        if (finalEvaluation.needsRetry()) {
                            logger.warn("Reflect could not satisfy requirements within retry budget.");
                        }
                    }

                    // Add comprehensive tracing events for execution summary
                    tracer.addEvent("execution.completed", Map.of(
                            "stageVisits", String.valueOf(stageVisits.size()),
                            "llmCalls", String.valueOf(metricsSnapshot.callCount()),
                            "totalTokens", String.valueOf(metricsSnapshot.totalTokenCount()),
                            "inputTokens", String.valueOf(metricsSnapshot.totalInputTokens()),
                            "outputTokens", String.valueOf(metricsSnapshot.totalOutputTokens()),
                            "totalDurationMs", String.valueOf(metricsSnapshot.totalDurationMs())));

                    if (finalActResult != null) {
                        tracer.addEvent("execution.artifacts", Map.of(
                                "invokedSkills", String.join(",", finalActResult.invokedSkills()),
                                "hasArtifact", String.valueOf(finalActResult.hasArtifact()),
                                "artifactPath",
                                finalActResult.hasArtifact() ? finalActResult.finalArtifact().toString() : ""));
                    }

                    if (finalEvaluation != null) {
                        Span.current().setAttribute("output", finalEvaluation.finalSummary());
                        setGenAiAttributes(Span.current(), request.goal(), finalEvaluation.finalSummary());
                    } else if (finalActResult != null && finalActResult.hasArtifact()) {
                        Span.current().setAttribute("output", finalActResult.finalArtifact().toString());
                        setGenAiAttributes(Span.current(), request.goal(), finalActResult.finalArtifact().toString());
                    } else if (finalPlanCompletion != null && finalPlanCompletion.content() != null) {
                        Span.current().setAttribute("output", finalPlanCompletion.content());
                        setGenAiAttributes(Span.current(), request.goal(), finalPlanCompletion.content());
                    }

                    return new ExecutionResult(
                            List.copyOf(stageVisits),
                            finalPlan,
                            finalPlanCompletion,
                            metricsSnapshot,
                            finalActResult,
                            finalEvaluation,
                            blackboardStore.snapshot());
                });
    }

    private void writePlanGoal(AgenticScope scope, String goal) {
        if (goal == null || goal.isBlank()) {
            throw new IllegalArgumentException("goal must be provided");
        }
        scope.writeState(PlanState.GOAL_KEY, goal);
    }

    public record AgentRunRequest(String goal, boolean dryRun, List<String> forcedSkillIds) {
        public AgentRunRequest {
            if (goal == null || goal.isBlank()) {
                throw new IllegalArgumentException("goal must be provided");
            }
            forcedSkillIds = forcedSkillIds == null ? List.of() : List.copyOf(forcedSkillIds);
        }
    }

    public record ExecutionResult(
            List<StageVisit> visitedStages,
            PlanModels.PlanResult plan,
            LangChain4jLlmClient.CompletionResult planResult,
            LangChain4jLlmClient.ProviderMetrics metrics,
            ActResult actResult,
            ReflectEvaluator.EvaluationResult evaluation,
            Map<String, Object> blackboardSnapshot) {
    }

    public record StageVisit(int attempt, String stage) {
        public StageVisit {
            if (attempt < 1) {
                throw new IllegalArgumentException("attempt must be 1-indexed");
            }
            Objects.requireNonNull(stage, "stage");
        }
    }

    private static String determineProvider(ChatModel chatModel) {
        if (chatModel instanceof OpenAiChatModel) {
            return "openai";
        }
        return chatModel.getClass().getSimpleName().toLowerCase(Locale.ROOT);
    }

    private static void setGenAiAttributes(Span span, String input, String output) {
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
