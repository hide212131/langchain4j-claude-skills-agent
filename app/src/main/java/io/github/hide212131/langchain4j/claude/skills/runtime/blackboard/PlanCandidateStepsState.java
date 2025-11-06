package io.github.hide212131.langchain4j.claude.skills.runtime.blackboard;

import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.AgentStateKey;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.plan.PlanModels;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record PlanCandidateStepsState(List<PlanCandidateStep> steps) {

    public static final String KEY = "plan.candidateSteps";
    public static final AgentStateKey<PlanCandidateStepsState> STATE =
            AgentStateKey.of(KEY, PlanCandidateStepsState.class);

    public PlanCandidateStepsState {
        Objects.requireNonNull(steps, "steps");
        steps = List.copyOf(steps);
    }

    public static PlanCandidateStepsState fromPlan(PlanModels.PlanResult plan) {
        Objects.requireNonNull(plan, "plan");
        List<PlanCandidateStep> mapped = plan.steps().stream()
                .map(step -> new PlanCandidateStep(
                        step.skillId(),
                        step.name(),
                        step.description(),
                        step.keywords(),
                        step.skillRoot()))
                .toList();
        return new PlanCandidateStepsState(mapped);
    }

    public record PlanCandidateStep(
            String skillId,
            String name,
            String description,
            List<String> keywords,
            Path skillRoot) {

        public PlanCandidateStep {
            if (skillId == null || skillId.isBlank()) {
                throw new IllegalArgumentException("skillId must be provided");
            }
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("name must be provided");
            }
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(keywords, "keywords");
            keywords = List.copyOf(keywords);
            Objects.requireNonNull(skillRoot, "skillRoot");
        }
    }
}
