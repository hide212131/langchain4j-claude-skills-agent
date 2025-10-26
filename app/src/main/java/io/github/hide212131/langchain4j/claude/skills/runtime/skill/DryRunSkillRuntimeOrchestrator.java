package io.github.hide212131.langchain4j.claude.skills.runtime.skill;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Simple orchestrator used during dry-run execution to avoid relying on an LLM.
 * It mirrors the behaviour exercised in unit tests, calling the toolbox tools
 * in a deterministic order and producing a minimal supervisor reply.
 */
public final class DryRunSkillRuntimeOrchestrator implements SkillRuntime.SkillAgentOrchestrator {

    @Override
    public String run(
            SkillRuntime.Toolbox toolbox,
            SkillIndex.SkillMetadata metadata,
            Map<String, Object> inputs,
            List<String> expectedOutputs,
            String prompt) {
        String skillId = metadata.id();
        SkillRuntime.SkillDocumentResult skillDocument = toolbox.readSkillMd(skillId);
        for (String reference : skillDocument.references()) {
            toolbox.readReference(skillId, reference);
        }
        String description = metadata.description().isBlank() ? metadata.name() : metadata.description();
        String goal = Objects.toString(inputs.getOrDefault("goal", ""), "");
        StringBuilder body = new StringBuilder();
        if (!skillDocument.content().isBlank()) {
            body.append(skillDocument.content())
                    .append(System.lineSeparator())
                    .append("---")
                    .append(System.lineSeparator());
        }
        body.append("Goal: ")
                .append(goal.isBlank() ? "(none)" : goal)
                .append(System.lineSeparator());
        body.append("Skill: ")
                .append(metadata.name())
                .append(System.lineSeparator());
        SkillRuntime.ArtifactHandle handle = toolbox.writeArtifact(skillId, null, body.toString(), false);
        Map<String, Object> expected = new LinkedHashMap<>();
        for (String expectedOutput : expectedOutputs) {
            if (expectedOutput != null && !expectedOutput.isBlank()) {
                expected.put(expectedOutput, Boolean.TRUE);
            }
        }
        toolbox.validateExpectedOutputs(
                expected,
                Map.of(
                        "artifactPath", handle.path(),
                        "summary", description,
                        "skillRoot", metadata.skillRoot().toString()));

        return "artifactPath=" + handle.path() + System.lineSeparator() + "summary=" + description;
    }
}
