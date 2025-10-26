package io.github.hide212131.langchain4j.claude.skills.runtime.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.hide212131.langchain4j.claude.skills.infra.logging.WorkflowLogger;
import io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.ActState;
import io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.BlackboardStore;
import io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.ReflectFinalSummaryState;
import io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.ReflectReviewState;
import io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.ReflectRetryAdviceState;
import io.github.hide212131.langchain4j.claude.skills.runtime.guard.SkillInvocationGuard;
import io.github.hide212131.langchain4j.claude.skills.runtime.provider.LangChain4jLlmClient;
import io.github.hide212131.langchain4j.claude.skills.runtime.skill.SkillIndex;
import io.github.hide212131.langchain4j.claude.skills.runtime.skill.SkillIndex.SkillMetadata;
import io.github.hide212131.langchain4j.claude.skills.runtime.skill.SkillRuntime;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.act.DefaultInvoker;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.act.InvokeSkillTool;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.reflect.DefaultEvaluator;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.reflect.ReflectEvaluator;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.plan.DefaultPlanner;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.support.WorkflowFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentServiceTest {

    @Test
    void runGoalInvokesPlanActReflectInOrder() throws Exception {
        Path tempDir = Files.createTempDirectory("agent-service-test");
        Path skillsRoot = Path.of("skills").toAbsolutePath().normalize();
        SkillIndex index = new SkillIndex(skillsRoot, java.util.Map.of(
                "brand-guidelines",
                new SkillMetadata(
                        "brand-guidelines",
                        "Brand",
                        "",
                        java.util.List.of("brand"),
                        java.util.List.of(),
                        skillsRoot.resolve("brand-guidelines")),
                "document-skills/pptx",
                new SkillMetadata(
                        "document-skills/pptx",
                        "PPTX",
                        "",
                        java.util.List.of("pptx"),
                        java.util.List.of(),
                        skillsRoot.resolve("document-skills/pptx"))));
        WorkflowLogger logger = new WorkflowLogger();
        BlackboardStore blackboardStore = new BlackboardStore();
        SkillRuntime runtime = new SkillRuntime(index, tempDir, logger);
        InvokeSkillTool tool = new InvokeSkillTool(runtime);
        DefaultInvoker invoker =
                new DefaultInvoker(tool, new SkillInvocationGuard(), blackboardStore, logger);
        AgentService service = new AgentService(
                new WorkflowFactory(),
                LangChain4jLlmClient.fake(),
                index,
                logger,
                invoker,
                blackboardStore,
                new DefaultEvaluator(blackboardStore, logger));
        AgentService.ExecutionResult result =
                service.run(new AgentService.AgentRunRequest("demo", true));

        assertThat(result.visitedStages()).containsExactly("plan", "act", "reflect");
        assertThat(result.plan().orderedSkillIds())
                .containsExactly("brand-guidelines", "document-skills/pptx");
        assertThat(result.plan().systemPromptSummary()).contains("brand-guidelines");
        assertThat(result.plan().steps().get(0).skillRoot())
                .isEqualTo(skillsRoot.resolve("brand-guidelines").toAbsolutePath().normalize());
        assertThat(result.planResult().content()).isEqualTo("dry-run-plan");
        assertThat(result.metrics().callCount()).isEqualTo(1);
        assertThat(result.actResult()).isNotNull();
        assertThat(result.actResult().invokedSkills())
                .containsExactly("brand-guidelines", "document-skills/pptx");
        assertThat(result.blackboardSnapshot())
                .containsKeys(
                        ActState.outputKey("brand-guidelines"), ActState.outputKey("document-skills/pptx"));
        assertThat(result.evaluation()).isNotNull();
        assertThat(result.evaluation().success()).isTrue();
        assertThat(result.evaluation().needsRetry()).isFalse();
        assertThat(result.actResult().hasArtifact()).isTrue();
        assertThat(result.actResult().finalArtifact()).isEqualTo(tempDir.resolve("deck.pptx"));
        assertThat(Files.exists(result.actResult().finalArtifact())).isTrue();
    }

    @Test
    void retriesPlanWhenEvaluatorRequestsRetry() throws Exception {
        Path tempDir = Files.createTempDirectory("agent-service-test-retry");
        Path skillsRoot = Path.of("skills").toAbsolutePath().normalize();
        SkillIndex index = new SkillIndex(skillsRoot, java.util.Map.of(
                "brand-guidelines",
                new SkillMetadata(
                        "brand-guidelines",
                        "Brand",
                        "",
                        java.util.List.of("brand"),
                        java.util.List.of(),
                        skillsRoot.resolve("brand-guidelines")),
                "document-skills/pptx",
                new SkillMetadata(
                        "document-skills/pptx",
                        "PPTX",
                        "",
                        java.util.List.of("pptx"),
                        java.util.List.of(),
                        skillsRoot.resolve("document-skills/pptx"))));
        WorkflowLogger logger = new WorkflowLogger();
        BlackboardStore blackboardStore = new BlackboardStore();
        SkillRuntime runtime = new SkillRuntime(index, tempDir, logger);
        InvokeSkillTool tool = new InvokeSkillTool(runtime);
        DefaultInvoker invoker =
                new DefaultInvoker(tool, new SkillInvocationGuard(), blackboardStore, logger);
        RetryingEvaluator evaluator = new RetryingEvaluator();
        AgentService service = new AgentService(
                new WorkflowFactory(),
                LangChain4jLlmClient.fake(),
                index,
                logger,
                invoker,
                blackboardStore,
                evaluator);

        AgentService.ExecutionResult result =
                service.run(new AgentService.AgentRunRequest("demo", true));

        assertThat(result.visitedStages())
                .containsExactly("plan", "act", "reflect", "plan", "act", "reflect");
        assertThat(result.metrics().callCount()).isEqualTo(2);
        assertThat(result.evaluation().success()).isTrue();
        assertThat(result.evaluation().needsRetry()).isFalse();
        assertThat(evaluator.invocationCount()).isEqualTo(2);
        assertThat(result.blackboardSnapshot())
                .containsKeys(
                        ActState.outputKey("brand-guidelines"), ActState.outputKey("document-skills/pptx"));
    }

    private static final class RetryingEvaluator implements ReflectEvaluator {

        private int invocationCount;

        @Override
        public EvaluationResult evaluate(
                dev.langchain4j.agentic.scope.AgenticScope scope,
                DefaultPlanner.PlanResult plan,
                DefaultInvoker.ActResult actResult,
                int attempt,
                int maxAttempts) {
            invocationCount++;
            if (invocationCount == 1) {
                scope.writeState(ReflectReviewState.KEY, List.of("Deck missing, retry required"));
                scope.writeState(ReflectRetryAdviceState.KEY, "retry-plan");
                scope.writeState(ReflectFinalSummaryState.KEY, "Attempt 1 failed; retrying");
                return new EvaluationResult(
                        false,
                        true,
                        actResult != null ? actResult.finalArtifact() : null,
                        0,
                        List.of("Deck missing, retry required"),
                        "retry-plan",
                        "Attempt 1 failed; retrying");
            }
            scope.writeState(ReflectReviewState.KEY, List.of("Deck validated"));
            scope.writeState(ReflectRetryAdviceState.KEY, "none");
            scope.writeState(ReflectFinalSummaryState.KEY, "Attempt 2 succeeded");
            return new EvaluationResult(
                    true,
                    false,
                    actResult != null ? actResult.finalArtifact() : null,
                    3,
                    List.of("Deck validated"),
                    "none",
                    "Attempt 2 succeeded");
        }

        int invocationCount() {
            return invocationCount;
        }
    }
}
