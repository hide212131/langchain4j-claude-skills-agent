package io.github.hide212131.langchain4j.claude.skills.runtime.skill;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.hide212131.langchain4j.claude.skills.infra.logging.WorkflowLogger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SkillRuntimeTest {

    private final WorkflowLogger logger = new WorkflowLogger();

    @Test
    void executeShouldGenerateDeckForPptxSkill() throws Exception {
        Path tempDir = Files.createTempDirectory("skill-runtime-test");
        SkillIndex index = new SkillIndex(Map.of(
                "document-skills/pptx",
                new SkillIndex.SkillMetadata(
                        "document-skills/pptx",
                        "PPTX Generator",
                        "Build slide decks",
                        List.of("pptx"),
                        List.of())));
        SkillRuntime runtime = new SkillRuntime(index, tempDir, logger);

        SkillRuntime.ExecutionResult result =
                runtime.execute("document-skills/pptx", Map.of("goal", "demo deck"));

        assertThat(result.hasArtifact()).isTrue();
        assertThat(result.artifactPath()).isNotNull();
        assertThat(result.artifactPath()).exists();
        assertThat(Files.readString(result.artifactPath())).contains("demo deck");
    }

    @Test
    void executeShouldReturnSummaryForNonArtifactSkill() throws Exception {
        Path tempDir = Files.createTempDirectory("skill-runtime-test-summary");
        SkillIndex index = new SkillIndex(Map.of(
                "brand-guidelines",
                new SkillIndex.SkillMetadata(
                        "brand-guidelines",
                        "Brand Guidelines",
                        "Summarise brand rules",
                        List.of("brand"),
                        List.of())));
        SkillRuntime runtime = new SkillRuntime(index, tempDir, logger);

        SkillRuntime.ExecutionResult result =
                runtime.execute("brand-guidelines", Map.of("goal", "branding"));

        assertThat(result.hasArtifact()).isFalse();
        assertThat(result.outputs()).containsEntry("summary", "Summarise brand rules");
    }
}
