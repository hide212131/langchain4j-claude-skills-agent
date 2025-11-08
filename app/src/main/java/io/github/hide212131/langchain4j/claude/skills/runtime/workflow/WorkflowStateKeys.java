package io.github.hide212131.langchain4j.claude.skills.runtime.workflow;

import io.github.hide212131.langchain4j.claude.skills.runtime.provider.LangChain4jLlmClient;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.act.DefaultInvoker;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.plan.PlanModels;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.reflect.ReflectEvaluator;

/**
 * Typed keys for {@code AgenticScope} state while executing the Plan → Act → Reflect workflow.
 */
public final class WorkflowStateKeys {

    private WorkflowStateKeys() {}

    public static final AgentStateKey<PlanModels.PlanResult> PLAN_RESULT =
        AgentStateKey.of("workflow.plan.result", PlanModels.PlanResult.class);
    public static final AgentStateKey<LangChain4jLlmClient.CompletionResult> PLAN_DRAFT =
        AgentStateKey.of("workflow.plan.draft", LangChain4jLlmClient.CompletionResult.class);
    public static final AgentStateKey<DefaultInvoker.ActResult> ACT_RESULT =
        AgentStateKey.of("workflow.act.result", DefaultInvoker.ActResult.class);
    public static final AgentStateKey<ReflectEvaluator.EvaluationResult> REFLECT_RESULT =
        AgentStateKey.of("workflow.reflect.result", ReflectEvaluator.EvaluationResult.class);
    public static final AgentStateKey<AgentService.PlanStageOutput> PLAN_STAGE_OUTPUT =
        AgentStateKey.of("workflow.plan.output", AgentService.PlanStageOutput.class);
    public static final AgentStateKey<DefaultInvoker.ActResult> ACT_STAGE_OUTPUT =
        AgentStateKey.of("workflow.act.output", DefaultInvoker.ActResult.class);
    public static final AgentStateKey<ReflectEvaluator.EvaluationResult> REFLECT_STAGE_OUTPUT =
        AgentStateKey.of("workflow.reflect.output", ReflectEvaluator.EvaluationResult.class);
}
