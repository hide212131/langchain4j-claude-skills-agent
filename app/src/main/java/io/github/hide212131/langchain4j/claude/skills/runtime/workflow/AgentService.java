package io.github.hide212131.langchain4j.claude.skills.runtime.workflow;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.BlackboardStore;
import io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.PlanCandidateStepsState;
import io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.PlanConstraintsState;
import io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.PlanEvaluationCriteriaState;
import io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.PlanInputsState;
import io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.PlanState;
import io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.ReflectFinalSummaryState;
import io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.ReflectReviewState;
import io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.ReflectRetryAdviceState;
import io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.SharedContextSnapshotState;
import io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.SharedGuardState;
import io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.SharedMetricsState;
import io.github.hide212131.langchain4j.claude.skills.infra.logging.WorkflowLogger;
import io.github.hide212131.langchain4j.claude.skills.runtime.provider.LangChain4jLlmClient;
import io.github.hide212131.langchain4j.claude.skills.runtime.skill.SkillIndex;
import io.github.hide212131.langchain4j.claude.skills.runtime.skill.SkillRuntime;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.act.DefaultInvoker;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.act.DefaultInvoker.ActResult;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.act.InvokeSkillTool;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.plan.DefaultPlanner;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.support.WorkflowFactory;
import io.github.hide212131.langchain4j.claude.skills.runtime.guard.SkillInvocationGuard;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * High-level entry point that executes the Plan → Act → Reflect workflow.
 */
public final class AgentService {

    private final WorkflowFactory workflowFactory;
    private final LangChain4jLlmClient llmClient;
    private final WorkflowLogger logger;
    private final DefaultPlanner planner;
    private final DefaultInvoker invoker;
    private final BlackboardStore blackboardStore;

    public static AgentService withDefaults(
            WorkflowFactory workflowFactory,
            LangChain4jLlmClient llmClient,
            SkillIndex skillIndex) {
        WorkflowLogger logger = new WorkflowLogger();
        BlackboardStore blackboardStore = new BlackboardStore();
        SkillRuntime runtime = new SkillRuntime(skillIndex, Path.of("build", "out"), logger);
        InvokeSkillTool tool = new InvokeSkillTool(runtime);
        SkillInvocationGuard guard = new SkillInvocationGuard();
        DefaultInvoker invoker = new DefaultInvoker(tool, guard, blackboardStore, logger);
        return new AgentService(
                workflowFactory, llmClient, skillIndex, logger, invoker, blackboardStore);
    }

    public AgentService(
            WorkflowFactory workflowFactory,
            LangChain4jLlmClient llmClient,
            SkillIndex skillIndex,
            WorkflowLogger logger,
            DefaultInvoker invoker,
            BlackboardStore blackboardStore) {
        this.workflowFactory = Objects.requireNonNull(workflowFactory, "workflowFactory");
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.planner = new DefaultPlanner(Objects.requireNonNull(skillIndex, "skillIndex"));
        this.invoker = Objects.requireNonNull(invoker, "invoker");
        this.blackboardStore = Objects.requireNonNull(blackboardStore, "blackboardStore");
    }

    public ExecutionResult run(AgentRunRequest request) {
        Objects.requireNonNull(request, "request");
        blackboardStore.clear();
        List<String> visitedStages = new ArrayList<>();
        AtomicReference<LangChain4jLlmClient.CompletionResult> planResult = new AtomicReference<>();
        AtomicReference<DefaultPlanner.PlanResult> planSequence = new AtomicReference<>();
        AtomicReference<ActResult> actResult = new AtomicReference<>();

        UntypedAgent workflow = workflowFactory.createWorkflow(builder -> {
            builder.name("skills-plan-act-reflect");
            builder.output(scope -> scope.state());
            builder.subAgents(
                    AgenticServices.agentAction(scope -> {
                        visitedStages.add("plan");
                        writePlanGoal(scope, request.goal());
                        scope.writeState(
                                PlanInputsState.KEY,
                                Map.of(
                                        "goal", request.goal(),
                                        "mode", request.dryRun() ? "dry-run" : "live"));
                        scope.writeState(
                                PlanConstraintsState.KEY, Map.of("tokenBudget", 0, "maxToolCalls", 3));
                        DefaultPlanner.PlanResult plan = planner.plan(request.goal());
                        planSequence.set(plan);
                        scope.writeState(
                                PlanCandidateStepsState.KEY,
                                plan.steps().stream()
                                        .map(step -> Map.of(
                                                "skillId", step.skillId(),
                                                "name", step.name(),
                                                "description", step.description(),
                                                "keywords", step.keywords()))
                                        .toList());
                        LangChain4jLlmClient.CompletionResult completion = llmClient.complete(request.goal());
                        planResult.set(completion);
                        scope.writeState(
                                PlanEvaluationCriteriaState.KEY,
                                Map.of(
                                        "systemPromptL1", plan.systemPromptSummary(),
                                        "assistantDraft",
                                        completion != null ? completion.content() : ""));
                        logger.info("Plan candidate steps: {}", plan.orderedSkillIds());
                        logger.info("Plan L1 summary:\n{}", plan.systemPromptSummary());
                    }),
                    AgenticServices.agentAction(scope -> {
                        visitedStages.add("act");
                        DefaultPlanner.PlanResult plan = planSequence.get();
                        if (plan == null) {
                            throw new IllegalStateException("Plan stage must complete before Act");
                        }
                        ActResult result = invoker.invoke(scope, plan);
                        actResult.set(result);
                    }),
                    AgenticServices.agentAction(scope -> {
                        visitedStages.add("reflect");
                        scope.writeState(ReflectReviewState.KEY, List.of());
                        scope.writeState(ReflectRetryAdviceState.KEY, "none");
                        scope.writeState(ReflectFinalSummaryState.KEY, "dry-run-summary");
                        scope.writeState(SharedContextSnapshotState.KEY, List.of());
                        scope.writeState(SharedGuardState.KEY, Map.of());
                        scope.writeState(SharedMetricsState.KEY, Map.of());
                    }));
        });

        workflow.invoke(Map.of("goal", request.goal()));

        LangChain4jLlmClient.CompletionResult completion = planResult.get();
        DefaultPlanner.PlanResult plan = planSequence.get();
        ActResult invoked = actResult.get();
        if (completion != null) {
            logger.info(
                    "Assistant response: {} (tokens in/out={}, durationMs={})",
                    completion.content(),
                    completion.tokenUsage(),
                    completion.durationMs());
        }
        LangChain4jLlmClient.ProviderMetrics metricsSnapshot = llmClient.metrics();
        if (metricsSnapshot.callCount() > 0) {
            logger.info(
                    "LLM usage summary: calls={}, tokens_in={}, tokens_out={}, durationMs={}",
                    metricsSnapshot.callCount(),
                    metricsSnapshot.totalInputTokens(),
                    metricsSnapshot.totalOutputTokens(),
                    metricsSnapshot.totalDurationMs());
        }
        if (invoked != null) {
            logger.info("Act stage invoked skills: {}", invoked.invokedSkills());
            if (invoked.hasArtifact()) {
                logger.info("Final artefact generated at {}", invoked.finalArtifact());
            }
        }
        return new ExecutionResult(
                List.copyOf(visitedStages),
                plan,
                completion,
                metricsSnapshot,
                invoked,
                blackboardStore.snapshot());
    }

    private void writePlanGoal(AgenticScope scope, String goal) {
        if (goal == null || goal.isBlank()) {
            throw new IllegalArgumentException("goal must be provided");
        }
        scope.writeState(PlanState.GOAL_KEY, goal);
    }

    public record AgentRunRequest(String goal, boolean dryRun) {}

    public record ExecutionResult(
            List<String> visitedStages,
            DefaultPlanner.PlanResult plan,
            LangChain4jLlmClient.CompletionResult planResult,
            LangChain4jLlmClient.ProviderMetrics metrics,
            ActResult actResult,
            Map<String, Object> blackboardSnapshot) {}
}
