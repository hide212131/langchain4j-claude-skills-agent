package io.github.hide212131.langchain4j.claude.skills.runtime.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.hide212131.langchain4j.claude.skills.runtime.provider.LangChain4jLlmClient;
import io.github.hide212131.langchain4j.claude.skills.runtime.skill.SkillIndex;
import io.github.hide212131.langchain4j.claude.skills.runtime.skill.SkillIndex.SkillMetadata;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.support.WorkflowFactory;
import org.junit.jupiter.api.Test;

class AgentServiceTest {

    @Test
    void runGoalInvokesPlanActReflectInOrder() {
        SkillIndex index = new SkillIndex(java.util.Map.of(
                "brand-guidelines",
                new SkillMetadata("brand-guidelines", "Brand", "", java.util.List.of("brand"), java.util.List.of()),
                "document-skills/pptx",
                new SkillMetadata("document-skills/pptx", "PPTX", "", java.util.List.of("pptx"), java.util.List.of())));
        AgentService service =
                AgentService.withDefaults(new WorkflowFactory(), LangChain4jLlmClient.fake(), index);
        AgentService.ExecutionResult result =
                service.run(new AgentService.AgentRunRequest("demo", true));

        assertThat(result.visitedStages()).containsExactly("plan", "act", "reflect");
        assertThat(result.plan().orderedSkillIds())
                .containsExactly("brand-guidelines", "document-skills/pptx");
        assertThat(result.plan().systemPromptSummary()).contains("brand-guidelines");
        assertThat(result.planResult().content()).isEqualTo("dry-run-plan");
        assertThat(result.metrics().callCount()).isEqualTo(1);
    }
}
