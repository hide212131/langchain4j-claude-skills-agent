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

import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.support.WorkflowFactory;
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
    private final WorkflowTracer tracer;
    private final ExecutionTelemetryReporter telemetryReporter;

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
        ObservabilityConfig observability = ObservabilityConfig.fromEnvironment();
        WorkflowTracer tracer = new WorkflowTracer(observability.tracer(), observability.isEnabled());
        ChatModel runtimeChatModel = llmClient.chatModel();
        ChatModel highPerformanceChatModel = llmClient.highPerformanceChatModel();
        if (!dryRun && observability.isEnabled()) {
            ChatModel instrumentedDefault = new InstrumentedChatModel(
                    runtimeChatModel,
                    observability.tracer(),
                    determineProvider(runtimeChatModel),
                    llmClient.defaultModelName());
            ChatModel instrumentedHighPerformance;
            if (highPerformanceChatModel == runtimeChatModel) {
                instrumentedHighPerformance = instrumentedDefault;
            } else {
                instrumentedHighPerformance = new InstrumentedChatModel(
                        highPerformanceChatModel,
                        observability.tracer(),
                        determineProvider(highPerformanceChatModel),
                        llmClient.highPerformanceModelName());
            }
            runtimeChatModel = instrumentedDefault;
            highPerformanceChatModel = instrumentedHighPerformance;
        }
    Path outputDirectory = resolveOutputDirectory(skillIndex);
    SkillRuntime runtime = dryRun
        ? new SkillRuntime(
            skillIndex,
            outputDirectory,
            logger,
            new DryRunSkillRuntimeOrchestrator(),
            tracer)
        : new SkillRuntime(
            skillIndex,
            outputDirectory,
            logger,
            runtimeChatModel,
            highPerformanceChatModel,
            tracer);
        InvokeSkillTool tool = new InvokeSkillTool(runtime);
        SkillInvocationGuard guard = new SkillInvocationGuard();
        DefaultInvoker invoker = new DefaultInvoker(tool, guard, logger);
        return new AgentService(
                workflowFactory,
                llmClient,
                skillIndex,
                logger,
                invoker,
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
            AgenticPlanner planner,
            WorkflowTracer tracer) {
        this.workflowFactory = Objects.requireNonNull(workflowFactory, "workflowFactory");
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
        this.logger = Objects.requireNonNull(logger, "logger");
        Objects.requireNonNull(skillIndex, "skillIndex");
        this.invoker = Objects.requireNonNull(invoker, "invoker");
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
                    List<StageVisit> stageVisits = new ArrayList<>();

                    AgentWorkflowObserver observer = new AgentWorkflowObserver(tracer, request, 1);

                    DynamicPlanOperator dynamicPlanAgent = new DynamicPlanOperator(
                            planner,
                            llmClient,
                            logger,
                            request,
                            1,
                            1,
                            observer);
                    ForcedPlanOperator forcedPlanAgent = new ForcedPlanOperator(
                            planner,
                            logger,
                            request,
                            1,
                            1,
                            observer);
                    ActOperator actAgent = new ActOperator(invoker, observer);

                    UntypedAgent planBranch = AgenticServices.conditionalBuilder()
                            .name("plan-branch")
                            .subAgents(agenticScope -> !request.forcedSkillIds().isEmpty(), forcedPlanAgent)
                            .subAgents(agenticScope -> request.forcedSkillIds().isEmpty(), dynamicPlanAgent)
                            .build();

                    UntypedAgent executionWorkflow = workflowFactory.createWorkflow(builder -> {
                        builder.name("skills-plan-act");
                        builder.output(attemptScope -> assembleExecutionResult(request, stageVisits, attemptScope, observer));
                        builder.subAgents(planBranch, actAgent);
                    });

                    return (ExecutionResult) executionWorkflow.invoke(Map.of("goal", request.goal()));
                });
    }

    private static <T> T readStateOrNull(AgenticScope scope, AgentStateKey<T> key) {
        if (scope == null) {
            return null;
        }
        return key.readOrNull(scope);
    }

    private ExecutionResult assembleExecutionResult(
            AgentRunRequest request,
            List<StageVisit> stageVisits,
            AgenticScope scope,
            AgentWorkflowObserver observer) {

        PlanModels.PlanResult finalPlan = readStateOrNull(scope, WorkflowStateKeys.PLAN_RESULT);
        LangChain4jLlmClient.CompletionResult finalPlanCompletion = readStateOrNull(scope, WorkflowStateKeys.PLAN_DRAFT);
        ActResult finalActResult = readStateOrNull(scope, WorkflowStateKeys.ACT_RESULT);

        LangChain4jLlmClient.ProviderMetrics metricsSnapshot = llmClient.metrics();

        stageVisits.addAll(observer.visits());
        List<StageVisit> visitsSnapshot = List.copyOf(stageVisits);

        telemetryReporter.report(
                request,
                finalPlan,
                finalPlanCompletion,
                finalActResult,
                metricsSnapshot,
                visitsSnapshot.size());

        // Build blackboard snapshot from ActResult outputs for backward compatibility
        Map<String, Object> blackboardSnapshot = new java.util.LinkedHashMap<>();
        if (finalActResult != null && finalActResult.outputs() != null) {
            finalActResult.outputs().forEach((skillId, output) -> {
                blackboardSnapshot.put(
                    io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.ActState.outputKey(skillId),
                    output);
            });
        }

        return new ExecutionResult(
                visitsSnapshot,
                finalPlan,
                finalPlanCompletion,
                metricsSnapshot,
                finalActResult,
                blackboardSnapshot);
    }



    public static final class DynamicPlanOperator {
        private final AgenticPlanner planner;
        private final LangChain4jLlmClient llmClient;
        private final WorkflowLogger logger;
        private final AgentRunRequest request;
        private final int attemptNumber;
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
            this.observer = observer;
        }

        @Agent(name = "plan")
        public void run(AgenticScope scope) {
            observer.beforeAgentInvocation(new AgentRequest(scope, "plan", Map.of("attempt", attemptNumber)));
            try {
                if (!request.forcedSkillIds().isEmpty()) {
                    throw new IllegalStateException("DynamicPlanOperator should not handle forced skill ids.");
                }
                PlanModels.PlanResult plan = planner.plan(request.goal());
                LangChain4jLlmClient.CompletionResult completion = llmClient.complete(request.goal());
                String assistantDraft = completion != null && completion.content() != null
                    ? completion.content()
                    : "";
                logger.info(
                        "Plan attempt {} candidate steps: {}",
                        attemptNumber,
                        plan.orderedSkillIds());
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
                PlanModels.PlanResult plan = planner.planWithFixedOrder(request.goal(), forcedSkillIds);
                String assistantDraft = "Planner bypassed via forced skill sequence.";
                logger.info(
                        "Plan attempt {} using forced skill sequence {}",
                        attemptNumber,
                        plan.orderedSkillIds());
                WorkflowStateKeys.PLAN_RESULT.write(scope, plan);
                WorkflowStateKeys.PLAN_DRAFT.write(scope, null);
                PlanStageOutput output = new PlanStageOutput(plan, null, assistantDraft);
                WorkflowStateKeys.PLAN_STAGE_OUTPUT.write(scope, output);
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
                // Use the refactored DefaultInvoker which now has cleaner skill execution logic
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



    public record PlanStageOutput(
            PlanModels.PlanResult plan,
            LangChain4jLlmClient.CompletionResult completion,
            String assistantDraft) {}
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
