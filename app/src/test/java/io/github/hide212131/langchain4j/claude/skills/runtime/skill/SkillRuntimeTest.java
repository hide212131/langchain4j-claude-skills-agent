package io.github.hide212131.langchain4j.claude.skills.runtime.skill;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.hide212131.langchain4j.claude.skills.infra.logging.WorkflowLogger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

class SkillRuntimeTest {

        private final WorkflowLogger logger = new WorkflowLogger();
        private final DryRunSkillRuntimeOrchestrator orchestrator = new DryRunSkillRuntimeOrchestrator();

        private SkillRuntime newRuntime(SkillIndex index, Path tempDir) {
                return new SkillRuntime(index, tempDir, logger, orchestrator);
        }

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
        SkillRuntime runtime = newRuntime(index, tempDir);

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
        assertThat(result.validation()).isNotNull();
        assertThat(result.validation().expectedOutputsSatisfied()).isTrue();
        assertThat(result.validation().missingOutputs()).isEmpty();
        assertThat(result.disclosureLog()).isNotEmpty();
        assertThat(disclosureLevels(result)).contains(SkillRuntime.DisclosureLevel.L1);
        List<String> toolNames = toolNames(result);
        assertThat(toolNames)
                .withFailMessage("tool names: %s", toolNames)
                .contains("writeArtifact", "validateExpectedOutputs");
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
        SkillRuntime runtime = newRuntime(index, tempDir);

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
        assertThat(result.validation()).isNotNull();
        assertThat(result.validation().expectedOutputsSatisfied()).isTrue();
        assertThat(result.validation().missingOutputs()).isEmpty();
        assertThat(disclosureLevels(result)).contains(SkillRuntime.DisclosureLevel.L1);
        List<String> brandToolNames = toolNames(result);
        assertThat(brandToolNames)
                .withFailMessage("tool names: %s", brandToolNames)
                .contains("writeArtifact", "validateExpectedOutputs");
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
        SkillRuntime runtime = newRuntime(index, tempDir);

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
        assertThat(result.validation()).isNotNull();
        assertThat(result.validation().expectedOutputsSatisfied()).isTrue();
        assertThat(result.validation().missingOutputs()).isEmpty();
        assertThat(disclosureLevels(result)).contains(SkillRuntime.DisclosureLevel.L1);
        List<String> fooToolNames = toolNames(result);
        assertThat(fooToolNames)
                .withFailMessage("tool names: %s", fooToolNames)
                .contains("writeArtifact", "validateExpectedOutputs");
    }

    @Test
    void executeShouldSucceedForSkillWithOnlySkillMd() throws Exception {
        Path tempDir = Files.createTempDirectory("skill-runtime-skillmd-only");
        Path skillsRoot = Path.of("src/test/resources/test-skills").toAbsolutePath().normalize();
        SkillIndex index = new SkillIndex(skillsRoot, Map.of(
                "brand-guidelines",
                new SkillIndex.SkillMetadata(
                        "brand-guidelines",
                        "Brand Guidelines",
                        "Summarise brand rules",
                        List.of("brand"),
                        List.of(),
                        skillsRoot.resolve("brand-guidelines"))));
        SkillRuntime runtime = newRuntime(index, tempDir);
        SkillIndex.SkillMetadata brandGuidelines = index.find("brand-guidelines").orElseThrow();
        Path skillMd = brandGuidelines.skillRoot().resolve("SKILL.md");
        assertThat(Files.exists(skillMd))
                .withFailMessage("Expected SKILL.md at %s", skillMd)
                .isTrue();

        SkillRuntime.ExecutionResult result =
                runtime.execute("brand-guidelines", Map.of("goal", "Ensure Anthropic styling"));

        assertThat(result.validation()).isNotNull();
        assertThat(result.validation().expectedOutputsSatisfied()).isTrue();
        assertThat(result.validation().missingOutputs()).isEmpty();
        assertThat(result.hasArtifact()).isTrue();
        assertThat(result.disclosureLog()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(disclosureLevels(result))
                .containsExactly(SkillRuntime.DisclosureLevel.L1, SkillRuntime.DisclosureLevel.L2);
        List<String> skillMdOnlyTools = toolNames(result);
        assertThat(skillMdOnlyTools)
                .withFailMessage("tool names: %s", skillMdOnlyTools)
                .contains("readSkillMd", "writeArtifact", "validateExpectedOutputs");
    }

    @Test
    void executeShouldLogL3ForReferencedDocuments() throws Exception {
        Path tempDir = Files.createTempDirectory("skill-runtime-reference");
        Path skillsRoot = Path.of("src/test/resources/test-skills").toAbsolutePath().normalize();
        SkillIndex.SkillMetadata withReferenceMetadata = new SkillIndex.SkillMetadata(
                "with-reference",
                "Reference Skill",
                "Loads external note",
                List.of("reference"),
                List.of(),
                skillsRoot.resolve("with-reference"));
        SkillIndex index = new SkillIndex(skillsRoot, Map.of("with-reference", withReferenceMetadata));
        Path withReferenceSkillMd = withReferenceMetadata.skillRoot().resolve("SKILL.md");
        assertThat(Files.exists(withReferenceSkillMd))
                .withFailMessage("Expected SKILL.md at %s", withReferenceSkillMd)
                .isTrue();
        SkillRuntime runtime = newRuntime(index, tempDir);

        SkillRuntime.ExecutionResult result =
                runtime.execute("with-reference", Map.of("goal", "review outline"));

        assertThat(disclosureLevels(result))
                .contains(SkillRuntime.DisclosureLevel.L1, SkillRuntime.DisclosureLevel.L2, SkillRuntime.DisclosureLevel.L3);
        assertThat(result.outputs())
                .containsKey("referencedFiles")
                .extractingByKey("referencedFiles", InstanceOfAssertFactories.list(String.class))
                .anyMatch(path -> path.endsWith("notes/outline.md"));
        List<String> referenceToolNames = toolNames(result);
        assertThat(referenceToolNames)
                .contains("readRef")
                .contains("readSkillMd", "writeArtifact", "validateExpectedOutputs");
        assertThat(result.toolInvocations())
                .filteredOn(invocation -> invocation.name().equals("readRef"))
                .isNotEmpty();
    }

    @Test
    void readReferenceShouldResolveArtifactsFromOutputDirectory() throws Exception {
        Path tempDir = Files.createTempDirectory("skill-runtime-output-reference");
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
        SkillRuntime.SkillAgentOrchestrator customOrchestrator = new SkillRuntime.SkillAgentOrchestrator() {
            @Override
            public String run(
                    SkillRuntime.Toolbox toolbox,
                    SkillIndex.SkillMetadata metadata,
                    Map<String, Object> inputs,
                    List<String> expectedOutputs,
                    String prompt) {
                String skillId = metadata.id();
                SkillRuntime.ArtifactHandle handle =
                        toolbox.writeArtifact(skillId, "procedures/generated.md", "Generated notes", false);
                SkillRuntime.ReferenceDocuments docs =
                        toolbox.readReference(skillId, "procedures/generated.md");
                assertThat(docs.documents()).hasSize(1);
                assertThat(docs.documents().get(0).content()).contains("Generated notes");
                return "artifactPath=" + handle.path() + System.lineSeparator() + "summary=Generated notes";
            }
        };
        SkillRuntime runtime = new SkillRuntime(index, tempDir, logger, customOrchestrator);

        SkillRuntime.ExecutionResult result = runtime.execute("document-skills/pptx", Map.of());

        assertThat(result.toolInvocations()).anyMatch(invocation -> invocation.name().equals("readRef"));
        assertThat(result.outputs())
                .containsKey("referencedFiles")
                .extractingByKey("referencedFiles", InstanceOfAssertFactories.list(String.class))
                .anyMatch(path -> path.endsWith("procedures/generated.md"));
        Path generated = tempDir
                .resolve("document-skills/pptx")
                .resolve("procedures/generated.md");
        assertThat(Files.readString(generated)).contains("Generated notes");
    }

    @Test
    void secondExecutionShouldEmbedPreviousOutputDetailsIntoPrompt() throws Exception {
        Path tempDir = Files.createTempDirectory("skill-runtime-previous-output");
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
        List<String> prompts = new ArrayList<>();
        SkillRuntime.SkillAgentOrchestrator capturingOrchestrator = new SkillRuntime.SkillAgentOrchestrator() {
            private int invocationCount;

            @Override
            public String run(
                    SkillRuntime.Toolbox toolbox,
                    SkillIndex.SkillMetadata metadata,
                    Map<String, Object> inputs,
                    List<String> expectedOutputs,
                    String prompt) {
                prompts.add(prompt);
                if (invocationCount++ == 0) {
                    SkillRuntime.ArtifactHandle handle =
                            toolbox.writeArtifact(metadata.id(), "first-output.txt", "First artefact", false);
                    return "artifactPath=" + handle.path() + System.lineSeparator() + "summary=First summary";
                }
                return "artifactPath=/tmp/second-output" + System.lineSeparator() + "summary=Second summary";
            }
        };
        SkillRuntime runtime = new SkillRuntime(index, tempDir, logger, capturingOrchestrator);

        SkillRuntime.ExecutionResult firstResult =
                runtime.execute("brand-guidelines", Map.of("goal", "first goal"));
        runtime.execute("brand-guidelines", Map.of("goal", "second goal"));

        assertThat(prompts).hasSize(2);
        assertThat(prompts.get(0)).doesNotContain("Previous Skill Output:");
        assertThat(prompts.get(1))
                .contains("Previous Skill Output:")
                .contains("Summary: First summary")
                .contains("Artifact Path: " + firstResult.outputs().get("artifactPath"));
    }

        private EnumSet<SkillRuntime.DisclosureLevel> disclosureLevels(SkillRuntime.ExecutionResult result) {
                return result.disclosureLog().stream()
                                .map(SkillRuntime.DisclosureEvent::level)
                                .collect(
                                                () -> EnumSet.noneOf(SkillRuntime.DisclosureLevel.class),
                                                EnumSet::add,
                                                EnumSet::addAll);
        }

        private List<String> toolNames(SkillRuntime.ExecutionResult result) {
                return result.toolInvocations().stream()
                                .map(SkillRuntime.ToolInvocation::name)
                                .toList();
        }
}
