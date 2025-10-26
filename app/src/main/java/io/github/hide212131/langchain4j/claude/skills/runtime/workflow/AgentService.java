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
import io.github.hide212131.langchain4j.claude.skills.infra.logging.WorkflowLogger;
import io.github.hide212131.langchain4j.claude.skills.runtime.provider.LangChain4jLlmClient;
import io.github.hide212131.langchain4j.claude.skills.runtime.skill.SkillIndex;
import io.github.hide212131.langchain4j.claude.skills.runtime.skill.SkillRuntime;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.act.DefaultInvoker;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.act.DefaultInvoker.ActResult;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.act.InvokeSkillTool;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.plan.DefaultPlanner;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.reflect.DefaultEvaluator;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.reflect.ReflectEvaluator;
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
    private final ReflectEvaluator evaluator;

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
        ReflectEvaluator evaluator = new DefaultEvaluator(blackboardStore, logger);
        return new AgentService(
                workflowFactory,
                llmClient,
                skillIndex,
                logger,
                invoker,
                blackboardStore,
                evaluator);
    }

    public AgentService(
            WorkflowFactory workflowFactory,
            LangChain4jLlmClient llmClient,
            SkillIndex skillIndex,
            WorkflowLogger logger,
            DefaultInvoker invoker,
            BlackboardStore blackboardStore,
            ReflectEvaluator evaluator) {
        this.workflowFactory = Objects.requireNonNull(workflowFactory, "workflowFactory");
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.planner = new DefaultPlanner(Objects.requireNonNull(skillIndex, "skillIndex"));
        this.invoker = Objects.requireNonNull(invoker, "invoker");
        this.blackboardStore = Objects.requireNonNull(blackboardStore, "blackboardStore");
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
    }

    public ExecutionResult run(AgentRunRequest request) {
        Objects.requireNonNull(request, "request");
        blackboardStore.clear();
        List<String> visitedStages = new ArrayList<>();
        LangChain4jLlmClient.CompletionResult finalPlanCompletion = null;
        DefaultPlanner.PlanResult finalPlan = null;
        ActResult finalActResult = null;
        ReflectEvaluator.EvaluationResult finalEvaluation = null;
        int maxAttempts = 2;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int attemptIndex = attempt;
            int attemptNumber = attemptIndex + 1;
            AtomicReference<LangChain4jLlmClient.CompletionResult> planResultRef = new AtomicReference<>();
            AtomicReference<DefaultPlanner.PlanResult> planRef = new AtomicReference<>();
            AtomicReference<ActResult> actRef = new AtomicReference<>();
            AtomicReference<ReflectEvaluator.EvaluationResult> evaluationRef = new AtomicReference<>();

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
                                            "mode", request.dryRun() ? "dry-run" : "live",
                                            "attempt", attemptNumber));
                            scope.writeState(
                                    PlanConstraintsState.KEY,
                                    Map.of(
                                            "tokenBudget", 0,
                                            "maxToolCalls", 3,
                                            "maxAttempts", maxAttempts,
                                            "attempt", attemptNumber));
                            DefaultPlanner.PlanResult plan = planner.plan(request.goal());
                            planRef.set(plan);
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
                            LangChain4jLlmClient.CompletionResult completion =
                                    llmClient.complete(request.goal());
                            planResultRef.set(completion);
                            scope.writeState(
                                    PlanEvaluationCriteriaState.KEY,
                                    Map.of(
                                            "systemPromptL1", plan.systemPromptSummary(),
                                            "assistantDraft",
                                            completion != null ? completion.content() : "",
                                            "attempt", attemptNumber));
                            logger.info(
                                    "Plan attempt {} candidate steps: {}",
                                    attemptNumber,
                                    plan.orderedSkillIds());
                        }),
                        AgenticServices.agentAction(scope -> {
                            visitedStages.add("act");
                            DefaultPlanner.PlanResult plan = planRef.get();
                            if (plan == null) {
                                throw new IllegalStateException("Plan stage must complete before Act");
                            }
                            ActResult result = invoker.invoke(scope, plan);
                            actRef.set(result);
                        }),
                        AgenticServices.agentAction(scope -> {
                            visitedStages.add("reflect");
                            DefaultPlanner.PlanResult plan = planRef.get();
                            ReflectEvaluator.EvaluationResult evaluation = evaluator.evaluate(
                                    scope, plan, actRef.get(), attemptIndex, maxAttempts);
                            evaluationRef.set(evaluation);
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
        return new ExecutionResult(
                List.copyOf(visitedStages),
                finalPlan,
                finalPlanCompletion,
                metricsSnapshot,
                finalActResult,
                finalEvaluation,
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
            ReflectEvaluator.EvaluationResult evaluation,
            Map<String, Object> blackboardSnapshot) {}
}
