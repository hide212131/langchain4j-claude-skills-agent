package io.github.hide212131.langchain4j.claude.skills.runtime.workflow;

import static io.github.hide212131.langchain4j.claude.skills.runtime.observability.WorkflowTelemetry.setGenAiAttributes;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.agent.AgentRequest;
import dev.langchain4j.agentic.agent.AgentResponse;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.github.hide212131.langchain4j.claude.skills.infra.logging.WorkflowLogger;
import io.github.hide212131.langchain4j.claude.skills.infra.observability.ObservabilityConfig;
import io.github.hide212131.langchain4j.claude.skills.infra.observability.WorkflowTracer;
import io.github.hide212131.langchain4j.claude.skills.runtime.observability.AgentWorkflowObserver;
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
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.WorkflowStateKeys;
import io.opentelemetry.api.trace.Span;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;

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
                    PlanModels.PlanResult finalPlan = null;
                    LangChain4jLlmClient.CompletionResult finalPlanCompletion = null;
                    ActResult finalActResult = null;
                    ReflectEvaluator.EvaluationResult finalEvaluation = null;
                    int maxAttempts = 2;

                    for (int attempt = 0; attempt < maxAttempts; attempt++) {
                        int attemptNumber = attempt + 1;
                        AgentWorkflowObserver observer = new AgentWorkflowObserver(
                                tracer,
                                request,
                                attemptNumber);
                        PlanOperator planAgent = new PlanOperator(
                                planner,
                                llmClient,
                                logger,
                                request,
                                attemptNumber,
                                maxAttempts,
                                observer);
                        ActOperator actAgent = new ActOperator(invoker, observer);
                        ReflectOperator reflectAgent = new ReflectOperator(
                                evaluator,
                                attemptNumber,
                                maxAttempts,
                                observer);

                        UntypedAgent workflow = workflowFactory.createWorkflow(builder -> {
                            builder.name("skills-plan-act-reflect");
                            builder.output(scope -> assembleAttemptSnapshot(scope, observer));
                            builder.subAgents(planAgent, actAgent, reflectAgent);
                        });

                        AttemptSnapshot snapshot =
                                (AttemptSnapshot) workflow.invoke(Map.of("goal", request.goal()));

                        finalPlan = snapshot.plan();
                        finalPlanCompletion = snapshot.planCompletion();
                        finalActResult = snapshot.actResult();
                        finalEvaluation = snapshot.evaluation();
                        stageVisits.addAll(snapshot.stageVisits());

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

    private AttemptSnapshot assembleAttemptSnapshot(AgenticScope scope, AgentWorkflowObserver observer) {
        PlanModels.PlanResult plan = readStateOrNull(scope, WorkflowStateKeys.PLAN_RESULT, PlanModels.PlanResult.class);
        LangChain4jLlmClient.CompletionResult planDraft =
                readStateOrNull(scope, WorkflowStateKeys.PLAN_DRAFT, LangChain4jLlmClient.CompletionResult.class);
        ActResult actResult = readStateOrNull(scope, WorkflowStateKeys.ACT_RESULT, ActResult.class);
        ReflectEvaluator.EvaluationResult evaluation =
                readStateOrNull(scope, WorkflowStateKeys.REFLECT_RESULT, ReflectEvaluator.EvaluationResult.class);
        return new AttemptSnapshot(plan, planDraft, actResult, evaluation, observer.visits());
    }

    private static <T> T readStateOrNull(AgenticScope scope, String key, Class<T> type) {
        if (scope == null || !scope.hasState(key)) {
            return null;
        }
        Object value = scope.readState(key);
        if (value == null) {
            return null;
        }
        if (!type.isInstance(value)) {
            throw new IllegalStateException("Unexpected type for key " + key + ": " + value.getClass());
        }
        return type.cast(value);
    }

    public static final class PlanOperator {
        private final AgenticPlanner planner;
        private final LangChain4jLlmClient llmClient;
        private final WorkflowLogger logger;
        private final AgentRunRequest request;
        private final int attemptNumber;
        private final int maxAttempts;
        private final AgentWorkflowObserver observer;

        PlanOperator(
                AgenticPlanner planner,
                LangChain4jLlmClient llmClient,
                WorkflowLogger logger,
                AgentRunRequest request,
                int attemptNumber,
                int maxAttempts,
                AgentWorkflowObserver observer) {
            this.planner = planner;
            this.llmClient = llmClient;
            this.logger = logger;
            this.request = request;
            this.attemptNumber = attemptNumber;
            this.maxAttempts = maxAttempts;
            this.observer = observer;
        }

        @Agent(name = "plan")
        public void run(AgenticScope scope) {
            observer.beforeAgentInvocation(new AgentRequest(scope, "plan", Map.of("attempt", attemptNumber)));
            try {
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
                    assistantDraft = completion != null ? completion.content() : "";
                    logger.info(
                            "Plan attempt {} candidate steps: {}",
                            attemptNumber,
                            plan.orderedSkillIds());
                }
                scope.writeState(
                        PlanEvaluationCriteriaState.KEY,
                        Map.of(
                                "systemPromptL1", plan.systemPromptSummary(),
                                "assistantDraft", assistantDraft,
                                "attempt", attemptNumber));
                scope.writeState(WorkflowStateKeys.PLAN_RESULT, plan);
                scope.writeState(WorkflowStateKeys.PLAN_DRAFT, completion);
                PlanOperatorOutput output = new PlanOperatorOutput(plan, completion, assistantDraft);
                scope.writeState(WorkflowStateKeys.PLAN_STAGE_OUTPUT, output);
                observer.afterAgentInvocation(new AgentResponse(scope, "plan", Map.of("attempt", attemptNumber), output));
            } catch (RuntimeException ex) {
                observer.onStageError(ex);
                throw ex;
            }
        }
    }

    public static final class ActOperator {
        private final DefaultInvoker invoker;
        private final AgentWorkflowObserver observer;

        ActOperator(DefaultInvoker invoker, AgentWorkflowObserver observer) {
            this.invoker = invoker;
            this.observer = observer;
        }

        @Agent(name = "act")
        public void run(AgenticScope scope) {
            observer.beforeAgentInvocation(new AgentRequest(scope, "act", Map.of()));
            try {
                PlanModels.PlanResult plan = readStateOrNull(scope, WorkflowStateKeys.PLAN_RESULT, PlanModels.PlanResult.class);
                if (plan == null) {
                    throw new IllegalStateException("Plan stage must complete before Act");
                }
                ActResult result = invoker.invoke(scope, plan);
                scope.writeState(WorkflowStateKeys.ACT_RESULT, result);
                scope.writeState(WorkflowStateKeys.ACT_STAGE_OUTPUT, result);
                observer.afterAgentInvocation(new AgentResponse(scope, "act", Map.of(), result));
            } catch (RuntimeException ex) {
                observer.onStageError(ex);
                throw ex;
            }
        }
    }

    public static final class ReflectOperator {
        private final ReflectEvaluator evaluator;
        private final int attemptNumber;
        private final int maxAttempts;
        private final AgentWorkflowObserver observer;

        ReflectOperator(
                ReflectEvaluator evaluator,
                int attemptNumber,
                int maxAttempts,
                AgentWorkflowObserver observer) {
            this.evaluator = evaluator;
            this.attemptNumber = attemptNumber;
            this.maxAttempts = maxAttempts;
            this.observer = observer;
        }

        @Agent(name = "reflect")
        public void run(AgenticScope scope) {
            observer.beforeAgentInvocation(new AgentRequest(scope, "reflect", Map.of("attempt", attemptNumber)));
            try {
                PlanModels.PlanResult plan = readStateOrNull(scope, WorkflowStateKeys.PLAN_RESULT, PlanModels.PlanResult.class);
                if (plan == null) {
                    throw new IllegalStateException("Plan stage must complete before Reflect");
                }
                ActResult actResult = readStateOrNull(scope, WorkflowStateKeys.ACT_RESULT, ActResult.class);
                ReflectEvaluator.EvaluationResult evaluation =
                        evaluator.evaluate(scope, plan, actResult, attemptNumber, maxAttempts);
                scope.writeState(WorkflowStateKeys.REFLECT_RESULT, evaluation);
                scope.writeState(WorkflowStateKeys.REFLECT_STAGE_OUTPUT, evaluation);
                observer.afterAgentInvocation(new AgentResponse(scope, "reflect", Map.of("attempt", attemptNumber), evaluation));
            } catch (RuntimeException ex) {
                observer.onStageError(ex);
                throw ex;
            }
        }
    }

    public record PlanOperatorOutput(
            PlanModels.PlanResult plan,
            LangChain4jLlmClient.CompletionResult completion,
            String assistantDraft) {}

    private record AttemptSnapshot(
            PlanModels.PlanResult plan,
            LangChain4jLlmClient.CompletionResult planCompletion,
            ActResult actResult,
            ReflectEvaluator.EvaluationResult evaluation,
            List<StageVisit> stageVisits) {

        private AttemptSnapshot {
            stageVisits = stageVisits == null ? List.of() : List.copyOf(stageVisits);
        }
    }

    private static void writePlanGoal(AgenticScope scope, String goal) {
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

}
