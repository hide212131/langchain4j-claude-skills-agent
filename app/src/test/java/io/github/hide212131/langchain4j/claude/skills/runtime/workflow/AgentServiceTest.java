package io.github.hide212131.langchain4j.claude.skills.runtime.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.hide212131.langchain4j.claude.skills.infra.logging.WorkflowLogger;
import io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.ActState;
import io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.BlackboardStore;
import io.github.hide212131.langchain4j.claude.skills.runtime.guard.SkillInvocationGuard;
import io.github.hide212131.langchain4j.claude.skills.runtime.provider.LangChain4jLlmClient;
import io.github.hide212131.langchain4j.claude.skills.runtime.skill.SkillIndex;
import io.github.hide212131.langchain4j.claude.skills.runtime.skill.SkillIndex.SkillMetadata;
import io.github.hide212131.langchain4j.claude.skills.runtime.skill.SkillRuntime;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.act.DefaultInvoker;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.act.InvokeSkillTool;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.support.WorkflowFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AgentServiceTest {

    @Test
    void runGoalInvokesPlanActReflectInOrder() throws Exception {
        Path tempDir = Files.createTempDirectory("agent-service-test");
        SkillIndex index = new SkillIndex(java.util.Map.of(
                "brand-guidelines",
                new SkillMetadata("brand-guidelines", "Brand", "", java.util.List.of("brand"), java.util.List.of()),
                "document-skills/pptx",
                new SkillMetadata("document-skills/pptx", "PPTX", "", java.util.List.of("pptx"), java.util.List.of())));
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
                blackboardStore);
        AgentService.ExecutionResult result =
                service.run(new AgentService.AgentRunRequest("demo", true));

        assertThat(result.visitedStages()).containsExactly("plan", "act", "reflect");
        assertThat(result.plan().orderedSkillIds())
                .containsExactly("brand-guidelines", "document-skills/pptx");
        assertThat(result.plan().systemPromptSummary()).contains("brand-guidelines");
        assertThat(result.planResult().content()).isEqualTo("dry-run-plan");
        assertThat(result.metrics().callCount()).isEqualTo(1);
        assertThat(result.actResult()).isNotNull();
        assertThat(result.actResult().invokedSkills())
                .containsExactly("brand-guidelines", "document-skills/pptx");
        assertThat(result.blackboardSnapshot())
                .containsKeys(
                        ActState.outputKey("brand-guidelines"), ActState.outputKey("document-skills/pptx"));
        assertThat(result.actResult().hasArtifact()).isTrue();
        assertThat(result.actResult().finalArtifact()).isEqualTo(tempDir.resolve("deck.pptx"));
        assertThat(Files.exists(result.actResult().finalArtifact())).isTrue();
    }
}
