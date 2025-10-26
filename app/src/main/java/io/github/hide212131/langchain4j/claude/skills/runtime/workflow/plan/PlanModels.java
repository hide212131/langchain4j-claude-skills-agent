package io.github.hide212131.langchain4j.claude.skills.runtime.workflow.plan;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class PlanModels {

    private PlanModels() {}

    public static PlanResult empty(String goal, String summary) {
        return new PlanResult(goal, List.of(), summary == null ? "" : summary);
    }

    public record PlanResult(String goal, List<PlanStep> steps, String systemPromptSummary) {
        public PlanResult {
            Objects.requireNonNull(goal, "goal");
            Objects.requireNonNull(steps, "steps");
            Objects.requireNonNull(systemPromptSummary, "systemPromptSummary");
        }

        public List<String> orderedSkillIds() {
            return steps.stream().map(PlanStep::skillId).collect(Collectors.toList());
        }
    }

    public record PlanStep(
            String skillId, String name, String description, List<String> keywords, Path skillRoot) {
        public PlanStep {
            Objects.requireNonNull(skillId, "skillId");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(keywords, "keywords");
            Objects.requireNonNull(skillRoot, "skillRoot");
        }
    }
}
