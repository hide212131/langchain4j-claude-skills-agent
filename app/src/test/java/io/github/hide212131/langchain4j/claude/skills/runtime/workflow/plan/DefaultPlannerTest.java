package io.github.hide212131.langchain4j.claude.skills.runtime.workflow.plan;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.hide212131.langchain4j.claude.skills.runtime.skill.SkillIndex;
import io.github.hide212131.langchain4j.claude.skills.runtime.skill.SkillIndex.SkillMetadata;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DefaultPlannerTest {

    @Test
    void shouldPrioritizeBrandThenPptx() {
        Path skillsRoot = Path.of("skills").toAbsolutePath().normalize();
        SkillIndex index = new SkillIndex(skillsRoot, Map.of(
                "brand-guidelines",
                        new SkillMetadata(
                                "brand-guidelines",
                                "Brand",
                                "",
                                List.of("brand"),
                                List.of(),
                                skillsRoot.resolve("brand-guidelines")),
                "document-skills/pptx",
                        new SkillMetadata(
                                "document-skills/pptx",
                                "PPTX",
                                "",
                                List.of("pptx"),
                                List.of(),
                                skillsRoot.resolve("document-skills/pptx"))));

        DefaultPlanner.PlanResult plan = new DefaultPlanner(index).plan("demo");

        assertThat(plan.orderedSkillIds())
                .containsExactly("brand-guidelines", "document-skills/pptx");
        assertThat(plan.systemPromptSummary()).contains("Brand").contains("PPTX");
        assertThat(plan.steps().get(0).skillRoot())
                .isEqualTo(skillsRoot.resolve("brand-guidelines").toAbsolutePath().normalize());
    }
}
