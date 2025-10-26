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
        Path skillsRoot = Path.of("skills").toAbsolutePath().normalize();
        SkillIndex index = new SkillIndex(skillsRoot, Map.of(
                "document-skills/pptx",
                new SkillIndex.SkillMetadata(
                        "document-skills/pptx",
                        "PPTX Generator",
                        "Build slide decks",
                        List.of("pptx"),
                        List.of(),
                        skillsRoot.resolve("document-skills/pptx"))));
        SkillRuntime runtime = new SkillRuntime(index, tempDir, logger);

        SkillRuntime.ExecutionResult result =
                runtime.execute("document-skills/pptx", Map.of("goal", "demo deck"));

        assertThat(result.hasArtifact()).isTrue();
        assertThat(result.artifactPath()).isNotNull();
        assertThat(result.artifactPath()).exists();
        assertThat(Files.readString(result.artifactPath())).contains("demo deck");
        assertThat(Files.readString(result.artifactPath())).contains("PPTX Generator");
        assertThat(result.outputs())
                .containsEntry(
                        "skillRoot",
                        skillsRoot.resolve("document-skills/pptx").toAbsolutePath().normalize().toString());
    }

    @Test
    void executeShouldGenerateOutputForAnySkill() throws Exception {
        // All skills produce output files in the generic MVP implementation
        Path tempDir = Files.createTempDirectory("skill-runtime-test-summary");
        Path skillsRoot = Path.of("skills").toAbsolutePath().normalize();
        SkillIndex index = new SkillIndex(skillsRoot, Map.of(
                "brand-guidelines",
                new SkillIndex.SkillMetadata(
                        "brand-guidelines",
                        "Brand Guidelines",
                        "Summarise brand rules",
                        List.of("brand"),
                        List.of(),
                        skillsRoot.resolve("brand-guidelines"))));
        SkillRuntime runtime = new SkillRuntime(index, tempDir, logger);

        SkillRuntime.ExecutionResult result =
                runtime.execute("brand-guidelines", Map.of("goal", "branding"));

        assertThat(result.hasArtifact()).isTrue();
        assertThat(result.outputs()).containsEntry("summary", "Summarise brand rules");
        assertThat(result.outputs())
                .containsEntry(
                        "skillRoot",
                        skillsRoot.resolve("brand-guidelines").toAbsolutePath().normalize().toString());
        assertThat(result.artifactPath()).exists();
        assertThat(Files.readString(result.artifactPath())).contains("Brand Guidelines");
    }

    @Test
    void executeShouldGenerateOutputRegardlessOfSkillStructure() throws Exception {
        // Test that any skill works, regardless of folder structure or naming
        Path tempDir = Files.createTempDirectory("skill-runtime-test-no-keywords");
        Path skillsRoot = Path.of("skills").toAbsolutePath().normalize();
        SkillIndex index = new SkillIndex(skillsRoot, Map.of(
                "foo/bar",
                new SkillIndex.SkillMetadata(
                        "foo/bar",
                        "Some Skill",
                        "Does something",
                        List.of(), // No keywords
                        List.of(),
                        skillsRoot.resolve("foo/bar"))));
        SkillRuntime runtime = new SkillRuntime(index, tempDir, logger);

        SkillRuntime.ExecutionResult result =
                runtime.execute("foo/bar", Map.of("goal", "test"));

        assertThat(result.hasArtifact()).isTrue();
        assertThat(result.artifactPath()).isNotNull();
        assertThat(result.artifactPath()).exists();
        assertThat(Files.readString(result.artifactPath())).contains("Some Skill");
        assertThat(result.outputs())
                .containsEntry(
                        "skillRoot",
                        skillsRoot.resolve("foo/bar").toAbsolutePath().normalize().toString());
    }
}
