package io.github.hide212131.langchain4j.claude.skills.runtime.workflow;

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
import io.github.hide212131.langchain4j.claude.skills.runtime.observability.ExecutionTelemetryReporter;
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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
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
    private final ExecutionTelemetryReporter telemetryReporter;
    private static final AgentStateKey<PlanState> PLAN_STATE_KEY =
        AgentStateKey.of("plan.goal", PlanState.class);

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
    Path outputDirectory = resolveOutputDirectory(skillIndex);
    SkillRuntime runtime = dryRun
        ? new SkillRuntime(
            skillIndex,
            outputDirectory,
            logger,
            new DryRunSkillRuntimeOrchestrator())
        : new SkillRuntime(
            skillIndex,
            outputDirectory,
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

    private static Path resolveOutputDirectory(SkillIndex skillIndex) {
        Objects.requireNonNull(skillIndex, "skillIndex");
        Path skillsRoot = skillIndex.skillsRoot();
        Path candidate = skillsRoot.resolveSibling("build").resolve("out");
        return candidate.toAbsolutePath().normalize();
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
        this.telemetryReporter = new ExecutionTelemetryReporter(this.tracer, this.logger);
    }

    public ExecutionResult run(AgentRunRequest request) {
        Objects.requireNonNull(request, "request");

    return tracer.trace("agent.execution", Map.ofEntries(
        Map.entry("workflow.execution.goal", request.goal()),
        Map.entry("workflow.execution.dry_run", request.dryRun()),
        Map.entry("workflow.execution.forced_skills", request.forcedSkillIds().stream()
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.joining(",")))),
                () -> {
                    blackboardStore.clear();
                    List<StageVisit> stageVisits = new ArrayList<>();
                    int maxAttempts = 2;

                    AtomicReference<AttemptSnapshot> lastSnapshot = new AtomicReference<>();
                    AttemptAgent attemptAgent = new AttemptAgent(
                            request,
                            maxAttempts,
                            stageVisits,
                            lastSnapshot);

                    UntypedAgent workflow = AgenticServices.loopBuilder()
                            .name("skills-plan-act-reflect")
                            .subAgents(attemptAgent)
                            .maxIterations(maxAttempts)
                            .exitCondition((scope, iteration) -> shouldExit(lastSnapshot.get()))
                            .output(scope -> lastSnapshot.get())
                            .build();

                    UntypedAgent executionWorkflow = AgenticServices.sequenceBuilder()
                            .name("skills-plan-act-reflect-root")
                            .subAgents(workflow)
                            .output(scope -> assembleExecutionResult(request, stageVisits, lastSnapshot))
                            .build();

                    return (ExecutionResult) executionWorkflow.invoke(Map.of("goal", request.goal()));
                });
    }

    private AttemptSnapshot assembleAttemptSnapshot(AgenticScope scope, AgentWorkflowObserver observer) {
        PlanModels.PlanResult plan = readStateOrNull(scope, WorkflowStateKeys.PLAN_RESULT);
        LangChain4jLlmClient.CompletionResult planDraft = readStateOrNull(scope, WorkflowStateKeys.PLAN_DRAFT);
        ActResult actResult = readStateOrNull(scope, WorkflowStateKeys.ACT_RESULT);
        ReflectEvaluator.EvaluationResult evaluation = readStateOrNull(scope, WorkflowStateKeys.REFLECT_RESULT);
        return new AttemptSnapshot(plan, planDraft, actResult, evaluation, observer.visits());
    }

    private static <T> T readStateOrNull(AgenticScope scope, AgentStateKey<T> key) {
        if (scope == null) {
            return null;
        }
        return key.readOrNull(scope);
    }

    private static boolean shouldExit(AttemptSnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }
        ReflectEvaluator.EvaluationResult evaluation = snapshot.evaluation();
        return evaluation == null || !evaluation.needsRetry();
    }

    private ExecutionResult assembleExecutionResult(
            AgentRunRequest request,
            List<StageVisit> stageVisits,
            AtomicReference<AttemptSnapshot> lastSnapshot) {

        AttemptSnapshot snapshot = lastSnapshot.get();
        PlanModels.PlanResult finalPlan = snapshot != null ? snapshot.plan() : null;
        LangChain4jLlmClient.CompletionResult finalPlanCompletion =
                snapshot != null ? snapshot.planCompletion() : null;
        ActResult finalActResult = snapshot != null ? snapshot.actResult() : null;
        ReflectEvaluator.EvaluationResult finalEvaluation = snapshot != null ? snapshot.evaluation() : null;

        LangChain4jLlmClient.ProviderMetrics metricsSnapshot = llmClient.metrics();
        if (finalEvaluation != null && finalEvaluation.needsRetry()) {
            logger.warn("Reflect could not satisfy requirements within retry budget.");
        }

    List<StageVisit> visitsSnapshot = List.copyOf(stageVisits);

        telemetryReporter.report(
                request,
                finalPlan,
                finalPlanCompletion,
                finalActResult,
                finalEvaluation,
        metricsSnapshot,
        visitsSnapshot.size());

        return new ExecutionResult(
                visitsSnapshot,
                finalPlan,
                finalPlanCompletion,
                metricsSnapshot,
                finalActResult,
                finalEvaluation,
                blackboardStore.snapshot());
    }

    public final class AttemptAgent {
        private final AgentRunRequest request;
        private final int maxAttempts;
        private final List<StageVisit> stageVisits;
        private final AtomicReference<AttemptSnapshot> lastSnapshot;
        private final AtomicInteger attemptCounter = new AtomicInteger();

        AttemptAgent(
                AgentRunRequest request,
                int maxAttempts,
                List<StageVisit> stageVisits,
                AtomicReference<AttemptSnapshot> lastSnapshot) {
            this.request = request;
            this.maxAttempts = maxAttempts;
            this.stageVisits = stageVisits;
            this.lastSnapshot = lastSnapshot;
        }

        @Agent(name = "attempt")
        public AttemptSnapshot invoke(AgenticScope scope) {
            int attemptNumber = attemptCounter.incrementAndGet();
            AgentWorkflowObserver observer = new AgentWorkflowObserver(tracer, request, attemptNumber);

            DynamicPlanOperator dynamicPlanAgent = new DynamicPlanOperator(
                    planner,
                    llmClient,
                    logger,
                    request,
                    attemptNumber,
                    maxAttempts,
                    observer);
            ForcedPlanOperator forcedPlanAgent = new ForcedPlanOperator(
                    planner,
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

            UntypedAgent planBranch = AgenticServices.conditionalBuilder()
                    .name("plan-branch")
                    .subAgents(agenticScope -> !request.forcedSkillIds().isEmpty(), forcedPlanAgent)
                    .subAgents(agenticScope -> request.forcedSkillIds().isEmpty(), dynamicPlanAgent)
                    .build();

            UntypedAgent attemptWorkflow = workflowFactory.createWorkflow(builder -> {
                builder.name("skills-plan-act-reflect-attempt");
                builder.output(attemptScope -> assembleAttemptSnapshot(attemptScope, observer));
                builder.subAgents(planBranch, actAgent, reflectAgent);
            });

            AttemptSnapshot snapshot = (AttemptSnapshot) attemptWorkflow.invoke(Map.of("goal", request.goal()));
            lastSnapshot.set(snapshot);
            stageVisits.addAll(snapshot.stageVisits());

            ReflectEvaluator.EvaluationResult evaluation = snapshot.evaluation();
            if (evaluation != null && evaluation.needsRetry() && attemptNumber < maxAttempts) {
                logger.info(
                        "Reflect requested retry after attempt {} — clearing blackboard and retrying.",
                        attemptNumber);
                blackboardStore.clear();
            }

            return snapshot;
        }
    }

    public static final class DynamicPlanOperator {
        private final AgenticPlanner planner;
        private final LangChain4jLlmClient llmClient;
        private final WorkflowLogger logger;
        private final AgentRunRequest request;
        private final int attemptNumber;
        private final int maxAttempts;
        private final AgentWorkflowObserver observer;

        DynamicPlanOperator(
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
        PlanInputsState.STATE.write(scope, new PlanInputsState(request.goal(), request.dryRun(), attemptNumber));
        PlanConstraintsState.STATE.write(
            scope,
            new PlanConstraintsState(0, 3, maxAttempts, attemptNumber));
                if (!request.forcedSkillIds().isEmpty()) {
                    throw new IllegalStateException("DynamicPlanOperator should not handle forced skill ids.");
                }
                PlanModels.PlanResult plan = planner.plan(request.goal());
        PlanCandidateStepsState.STATE.write(scope, PlanCandidateStepsState.fromPlan(plan));
        LangChain4jLlmClient.CompletionResult completion = llmClient.complete(request.goal());
        String assistantDraft = completion != null && completion.content() != null
            ? completion.content()
            : "";
                logger.info(
                        "Plan attempt {} candidate steps: {}",
                        attemptNumber,
                        plan.orderedSkillIds());
        PlanEvaluationCriteriaState.STATE.write(
            scope,
            new PlanEvaluationCriteriaState(plan.systemPromptSummary(), assistantDraft, attemptNumber));
        WorkflowStateKeys.PLAN_RESULT.write(scope, plan);
        WorkflowStateKeys.PLAN_DRAFT.write(scope, completion);
                PlanStageOutput output = new PlanStageOutput(plan, completion, assistantDraft);
        WorkflowStateKeys.PLAN_STAGE_OUTPUT.write(scope, output);
                observer.afterAgentInvocation(new AgentResponse(scope, "plan", Map.of("attempt", attemptNumber), output));
            } catch (RuntimeException ex) {
                observer.onStageError(ex);
                throw ex;
            }
        }
    }

    public static final class ForcedPlanOperator {
        private final AgenticPlanner planner;
        private final WorkflowLogger logger;
        private final AgentRunRequest request;
        private final int attemptNumber;
        private final int maxAttempts;
        private final AgentWorkflowObserver observer;

        ForcedPlanOperator(
                AgenticPlanner planner,
                WorkflowLogger logger,
                AgentRunRequest request,
                int attemptNumber,
                int maxAttempts,
                AgentWorkflowObserver observer) {
            this.planner = planner;
            this.logger = logger;
            this.request = request;
            this.attemptNumber = attemptNumber;
            this.maxAttempts = maxAttempts;
            this.observer = observer;
        }

        @Agent(name = "plan")
        public void run(AgenticScope scope) {
            observer.beforeAgentInvocation(new AgentRequest(scope, "plan", Map.of("attempt", attemptNumber, "mode", "forced")));
            try {
                List<String> forcedSkillIds = request.forcedSkillIds();
                if (forcedSkillIds.isEmpty()) {
                    throw new IllegalStateException("ForcedPlanOperator requires forced skill ids.");
                }
                writePlanGoal(scope, request.goal());
        PlanInputsState.STATE.write(scope, new PlanInputsState(request.goal(), request.dryRun(), attemptNumber));
        PlanConstraintsState.STATE.write(
            scope,
            new PlanConstraintsState(0, 3, maxAttempts, attemptNumber));
                PlanModels.PlanResult plan = planner.planWithFixedOrder(request.goal(), forcedSkillIds);
        PlanCandidateStepsState.STATE.write(scope, PlanCandidateStepsState.fromPlan(plan));
                String assistantDraft = "Planner bypassed via forced skill sequence.";
        PlanEvaluationCriteriaState.STATE.write(
            scope,
            new PlanEvaluationCriteriaState(plan.systemPromptSummary(), assistantDraft, attemptNumber));
        WorkflowStateKeys.PLAN_RESULT.write(scope, plan);
        WorkflowStateKeys.PLAN_DRAFT.write(scope, null);
                PlanStageOutput output = new PlanStageOutput(plan, null, assistantDraft);
        WorkflowStateKeys.PLAN_STAGE_OUTPUT.write(scope, output);
                logger.info(
                        "Plan attempt {} using forced skill sequence {}",
                        attemptNumber,
                        plan.orderedSkillIds());
                observer.afterAgentInvocation(new AgentResponse(scope, "plan", Map.of("attempt", attemptNumber, "mode", "forced"), output));
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
                PlanModels.PlanResult plan = readStateOrNull(scope, WorkflowStateKeys.PLAN_RESULT);
                if (plan == null) {
                    throw new IllegalStateException("Plan stage must complete before Act");
                }
                ActResult result = invoker.invoke(scope, plan);
                WorkflowStateKeys.ACT_RESULT.write(scope, result);
                WorkflowStateKeys.ACT_STAGE_OUTPUT.write(scope, result);
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
                PlanModels.PlanResult plan = readStateOrNull(scope, WorkflowStateKeys.PLAN_RESULT);
                if (plan == null) {
                    throw new IllegalStateException("Plan stage must complete before Reflect");
                }
                ActResult actResult = readStateOrNull(scope, WorkflowStateKeys.ACT_RESULT);
                ReflectEvaluator.EvaluationResult evaluation =
                        evaluator.evaluate(scope, plan, actResult, attemptNumber, maxAttempts);
                WorkflowStateKeys.REFLECT_RESULT.write(scope, evaluation);
                WorkflowStateKeys.REFLECT_STAGE_OUTPUT.write(scope, evaluation);
                observer.afterAgentInvocation(new AgentResponse(scope, "reflect", Map.of("attempt", attemptNumber), evaluation));
            } catch (RuntimeException ex) {
                observer.onStageError(ex);
                throw ex;
            }
        }
    }

    public record PlanStageOutput(
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
        PLAN_STATE_KEY.write(scope, new PlanState(goal));
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
