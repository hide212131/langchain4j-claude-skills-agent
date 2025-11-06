package io.github.hide212131.langchain4j.claude.skills.runtime.workflow;

/**
 * Shared constants for keys stored inside {@code AgenticScope} while executing
 * the Plan → Act → Reflect workflow.
 */
public final class WorkflowStateKeys {

    private WorkflowStateKeys() {}

    public static final String PLAN_RESULT = "workflow.plan.result";
    public static final String PLAN_DRAFT = "workflow.plan.draft";
    public static final String ACT_RESULT = "workflow.act.result";
    public static final String REFLECT_RESULT = "workflow.reflect.result";
}
