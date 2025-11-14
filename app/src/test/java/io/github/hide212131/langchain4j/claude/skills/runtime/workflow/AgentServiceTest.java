package io.github.hide212131.langchain4j.claude.skills.runtime.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.github.hide212131.langchain4j.claude.skills.infra.logging.WorkflowLogger;
import io.github.hide212131.langchain4j.claude.skills.infra.observability.WorkflowTracer;
import io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.ActState;

import io.github.hide212131.langchain4j.claude.skills.runtime.guard.SkillInvocationGuard;
import io.github.hide212131.langchain4j.claude.skills.runtime.provider.LangChain4jLlmClient;
import io.github.hide212131.langchain4j.claude.skills.runtime.skill.SkillIndex;
import io.github.hide212131.langchain4j.claude.skills.runtime.skill.SkillIndex.SkillMetadata;
import io.github.hide212131.langchain4j.claude.skills.runtime.skill.DryRunSkillRuntimeOrchestrator;
import io.github.hide212131.langchain4j.claude.skills.runtime.skill.SkillRuntime;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.act.DefaultInvoker;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.act.InvokeSkillTool;

import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.plan.AgenticPlanner;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.plan.PlanModels;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.support.WorkflowFactory;
import io.opentelemetry.api.OpenTelemetry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentServiceTest {

    @Test
    void runGoalInvokesPlanActInOrder() throws Exception {
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
        DryRunSkillRuntimeOrchestrator orchestrator = new DryRunSkillRuntimeOrchestrator();
        SkillRuntime runtime = new SkillRuntime(index, tempDir, logger, orchestrator);
        InvokeSkillTool tool = new InvokeSkillTool(runtime);
        DefaultInvoker invoker =
                new DefaultInvoker(tool, new SkillInvocationGuard(), logger);
        LangChain4jLlmClient llmClient = LangChain4jLlmClient.fake();
        AgenticPlanner planner = new AgenticPlanner(index, llmClient, logger);
        WorkflowTracer tracer = new WorkflowTracer(
                OpenTelemetry.noop().getTracer("test"), false);
        AgentService service = new AgentService(
                new WorkflowFactory(),
                llmClient,
                index,
                logger,
                invoker,
                planner,
                tracer);
        AgentService.ExecutionResult result =
                service.run(new AgentService.AgentRunRequest("demo", true, List.of()));

        assertThat(result.visitedStages())
                .extracting(AgentService.StageVisit::attempt, AgentService.StageVisit::stage)
                .containsExactly(tuple(1, "plan"), tuple(1, "act"));
        assertThat(result.plan().orderedSkillIds())
                .containsExactly("brand-guidelines", "document-skills/pptx");
        assertThat(result.plan().systemPromptSummary()).contains("brand-guidelines");
        assertThat(result.plan().steps().get(0).skillRoot())
                .isEqualTo(skillsRoot.resolve("brand-guidelines").toAbsolutePath().normalize());
        assertThat(result.planResult().content()).isEqualTo("dry-run-plan");
        assertThat(result.metrics().callCount()).isEqualTo(2);
        assertThat(result.actResult()).isNotNull();
        assertThat(result.actResult().invokedSkills())
                .containsExactly("brand-guidelines", "document-skills/pptx");
        assertThat(result.blackboardSnapshot())
                .containsKeys(
                        ActState.outputKey("brand-guidelines"), ActState.outputKey("document-skills/pptx"));
        assertThat(result.actResult().hasArtifact()).isTrue();
        assertThat(result.actResult().finalArtifact()).isEqualTo(tempDir.resolve("document-skills/pptx/deck.pptx"));
        assertThat(Files.exists(result.actResult().finalArtifact())).isTrue();
    }


}
