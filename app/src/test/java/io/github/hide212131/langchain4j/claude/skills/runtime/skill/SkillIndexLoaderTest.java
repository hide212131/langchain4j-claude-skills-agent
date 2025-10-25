package io.github.hide212131.langchain4j.claude.skills.runtime.skill;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SkillIndexLoaderTest {

    private final SkillIndexLoader loader = new SkillIndexLoader();

    @Test
    void loadShouldParseSkillMetadata() {
        Path skillsDir = Path.of("src", "test", "resources", "test-skills");

        SkillIndexLoader.LoadResult result = loader.load(skillsDir);
        assertThat(result.warnings()).isEmpty();
        SkillIndex index = result.index();

        assertThat(index.skills()).hasSize(2);
        SkillIndex.SkillMetadata brand = index.find("brand-guidelines").orElseThrow();
        assertThat(brand.name()).isEqualTo("Brand Guidelines");
        assertThat(brand.keywords()).contains("brand", "guidelines");

        SkillIndex.SkillMetadata pptx = index.find("document-skills/pptx").orElseThrow();
        assertThat(pptx.description()).contains("slide decks");
    }

    @Test
    void loadShouldCaptureUnknownFieldsAsWarnings() throws Exception {
        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("skill-loader-test");
        java.nio.file.Path skillDir = tempDir.resolve("custom");
        java.nio.file.Files.createDirectories(skillDir);
        java.nio.file.Path skillFile = skillDir.resolve("SKILL.md");
        java.nio.file.Files.writeString(
                skillFile,
                """
                ---
                id: custom-skill
                name: Custom Skill
                description: demo
                keywords:
                  - demo
                unsupported_field: true
                ---
                """);
        skillFile.toFile().deleteOnExit();
        skillDir.toFile().deleteOnExit();
        tempDir.toFile().deleteOnExit();

        SkillIndexLoader.LoadResult result = loader.load(tempDir);

        assertThat(result.warnings())
                .singleElement()
                .satisfies(warning -> assertThat(warning).contains("unsupported_field"));
        SkillIndex.SkillMetadata metadata = result.index().find("custom-skill").orElseThrow();
        assertThat(metadata.warnings()).contains("unsupported_field");
    }

    @Test
    void loadShouldWorkWithMinimalRequiredFields() throws Exception {
        // Test that only name and description are required, per official spec
        // https://support.claude.com/en/articles/12512198-how-to-create-custom-skills
        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("skill-loader-minimal");
        java.nio.file.Path skillDir = tempDir.resolve("minimal");
        java.nio.file.Files.createDirectories(skillDir);
        java.nio.file.Path skillFile = skillDir.resolve("SKILL.md");
        java.nio.file.Files.writeString(
                skillFile,
                """
                ---
                name: Minimal Skill
                description: Only required fields
                ---
                """);
        skillFile.toFile().deleteOnExit();
        skillDir.toFile().deleteOnExit();
        tempDir.toFile().deleteOnExit();

        SkillIndexLoader.LoadResult result = loader.load(tempDir);

        assertThat(result.warnings()).isEmpty();
        SkillIndex.SkillMetadata metadata = result.index().find("minimal").orElseThrow();
        assertThat(metadata.name()).isEqualTo("Minimal Skill");
        assertThat(metadata.description()).isEqualTo("Only required fields");
        assertThat(metadata.keywords()).isEmpty(); // keywords is optional
    }
}
