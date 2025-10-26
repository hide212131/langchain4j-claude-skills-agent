package io.github.hide212131.langchain4j.claude.skills.runtime.workflow.plan;

import io.github.hide212131.langchain4j.claude.skills.runtime.skill.SkillIndex;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class DefaultPlanner {

    private final SkillIndex skillIndex;

    public DefaultPlanner(SkillIndex skillIndex) {
        this.skillIndex = Objects.requireNonNull(skillIndex, "skillIndex");
    }

    public PlanResult plan(String goal) {
        List<PlanStep> steps = skillIndex.skills().values().stream()
                .sorted(Comparator.comparingInt(this::priorityFor))
                .map(metadata -> new PlanStep(
                        metadata.id(),
                        metadata.name(),
                        metadata.description(),
                        metadata.keywords(),
                        metadata.skillRoot()))
                .collect(Collectors.toList());

        String systemPromptSummary = steps.stream()
                .map(step -> String.format(
                        "%s: %s — %s (keywords: %s)",
                        step.skillId(),
                        step.name(),
                        step.description(),
                        String.join(", ", step.keywords())))
                .collect(Collectors.joining("\n"));

        return new PlanResult(goal, steps, systemPromptSummary);
    }

    private int priorityFor(SkillIndex.SkillMetadata metadata) {
        List<String> keywords = metadata.keywords();
        if (keywords.contains("brand")) {
            return 0;
        }
        if (keywords.contains("pptx")) {
            return 1;
        }
        return 10;
    }

    public record PlanResult(String goal, List<PlanStep> steps, String systemPromptSummary) {
        public List<String> orderedSkillIds() {
            return steps.stream().map(PlanStep::skillId).collect(Collectors.toList());
        }
    }

    public record PlanStep(
            String skillId, String name, String description, List<String> keywords, Path skillRoot) {}
}
