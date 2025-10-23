package io.github.hide212131.langchain4j.claude.skills.index;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkillIndexLoaderTest {

    @TempDir
    Path tempDir;

    private final SkillIndexLoader loader = new SkillIndexLoader();

    @Test
    void extractsStructuredMetadataFromSkillFile() throws IOException {
        Path skillDir = Files.createDirectories(tempDir.resolve("brand-guidelines"));
        Path resourcesDir = Files.createDirectories(skillDir.resolve("resources"));
        Path templatesDir = Files.createDirectories(resourcesDir.resolve("templates"));
        Files.writeString(resourcesDir.resolve("palette.yaml"), "primary: \"#FF0000\"");
        Files.writeString(templatesDir.resolve("slide.md"), "# Slide");
        Path scriptsDir = Files.createDirectories(skillDir.resolve("scripts"));
        Files.writeString(scriptsDir.resolve("normalize.py"), "#!/usr/bin/env python3\nprint('ok')");

        String skillMd = """
            ---
            name: "Brand Guidelines"
            description: "Summarize and cache brand guidelines."
            version: "1.0"
            inputs:
              - id: "source_doc"
                type: "file"
                required: true
                description: "Brand guideline markdown."
            outputs:
              - id: "brand_profile"
                type: "json"
                description: "Normalized brand profile."
            keywords:
              - "brand"
              - "guidelines"
            stages:
              - id: "ingest"
                purpose: "Ingest brand guideline documents and extract palette."
                resources:
                  - "resources/palette.yaml"
                  - "resources/templates/slide.md"
                scripts:
                  - "scripts/normalize.py"
            ---
            # Body
            """;
        Files.writeString(skillDir.resolve("SKILL.md"), skillMd);

        CapturingLogger logger = new CapturingLogger();
        List<SkillIndexEntry> entries = loader.load(tempDir, logger);

        assertThat(entries).hasSize(1);
        SkillIndexEntry entry = entries.getFirst();

        assertThat(entry.skillId()).isEqualTo("brand-guidelines");
        assertThat(entry.name()).isEqualTo("Brand Guidelines");
        assertThat(entry.description()).isEqualTo("Summarize and cache brand guidelines.");
        assertThat(entry.version()).contains("1.0");
        assertThat(entry.inputs())
                .containsExactly(new SkillIO("source_doc", "file", true, "Brand guideline markdown."));
        assertThat(entry.outputs())
                .containsExactly(new SkillIO("brand_profile", "json", false, "Normalized brand profile."));
        assertThat(entry.keywords()).containsExactly("brand", "guidelines");
        assertThat(entry.stages())
                .containsExactly(
                        new SkillStage(
                                "ingest",
                                "Ingest brand guideline documents and extract palette.",
                                List.of("resources/palette.yaml", "resources/templates/slide.md"),
                                List.of("scripts/normalize.py")));
        assertThat(entry.resourceFiles())
                .containsExactly("resources/palette.yaml", "resources/templates/slide.md");
        assertThat(entry.scriptFiles()).containsExactly("scripts/normalize.py");
        assertThat(entry.l1Summary())
                .isEqualTo("Brand Guidelines — Summarize and cache brand guidelines. Trigger when keywords: brand, guidelines.");
        assertThat(logger.warnings).isEmpty();
    }

    @Test
    void recordsWarningForUnsupportedFrontMatterFields() throws IOException {
        Path skillDir = Files.createDirectories(tempDir.resolve("document-skills/pptx"));
        Files.createDirectories(skillDir.resolve("resources"));
        Files.writeString(skillDir.resolve("SKILL.md"), """
            ---
            name: "Deck Builder"
            description: "Generate PPTX slides from agenda."
            inputs: []
            outputs: []
            keywords: ["presentation"]
            stages: []
            deprecated_field: "legacy"
            ---
            """);

        CapturingLogger logger = new CapturingLogger();
        loader.load(tempDir, logger);

        assertThat(logger.warnings)
                .singleElement()
                .satisfies(message -> {
                    assertThat(message).contains("document-skills/pptx");
                    assertThat(message).contains("deprecated_field");
                });
    }

    @Test
    void collectsRelativeResourceAndScriptPaths() throws IOException {
        Path skillDir = Files.createDirectories(tempDir.resolve("analytics/reporting"));
        Files.writeString(skillDir.resolve("SKILL.md"), """
            ---
            name: "Analytics Reporter"
            description: "Produce analytics report."
            inputs: []
            outputs: []
            keywords: []
            stages: []
            ---
            """);
        Files.createDirectories(skillDir.resolve("resources/sql"));
        Files.createDirectories(skillDir.resolve("scripts/helpers"));
        Files.writeString(skillDir.resolve("resources/sql/query.sql"), "select 1;");
        Files.writeString(skillDir.resolve("scripts/helpers/run.sh"), "echo ok");

        CapturingLogger logger = new CapturingLogger();
        SkillIndexEntry entry = loader.load(tempDir, logger).getFirst();

        assertThat(entry.resourceFiles()).containsExactly("resources/sql/query.sql");
        assertThat(entry.scriptFiles()).containsExactly("scripts/helpers/run.sh");
    }

    private static final class CapturingLogger implements SkillIndexLogger {

        private final List<String> warnings = new java.util.ArrayList<>();

        @Override
        public void warn(String skillId, String message) {
            warnings.add(skillId + ": " + message);
        }
    }
}
