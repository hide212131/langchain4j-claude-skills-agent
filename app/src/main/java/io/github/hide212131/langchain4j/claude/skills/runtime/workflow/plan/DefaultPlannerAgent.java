package io.github.hide212131.langchain4j.claude.skills.runtime.workflow.plan;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.hide212131.langchain4j.claude.skills.infra.logging.WorkflowLogger;
import io.github.hide212131.langchain4j.claude.skills.runtime.skill.SkillIndex;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default implementation of PlannerAgent using the declarative Agentic Framework.
 *
 * This class provides the @Tool implementations for skill planning.
 * The agent is instantiated dynamically via AgenticServices.agentBuilder(PlannerAgent.class)
 * and does not directly call llmClient.complete().
 */
public class DefaultPlannerAgent implements PlannerAgent {

    private static final int MAX_PRESENTED_CANDIDATES = 100;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final WorkflowLogger logger;

    public DefaultPlannerAgent(WorkflowLogger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public PlanModels.PlanResult planSkills(String goal, SkillIndex skillIndex) {
        // This method serves as the @Agent entry point.
        // The actual LLM interaction happens through the @Tool methods below.
        // AgenticServices will invoke the tools as needed.
        String normalisedGoal = normaliseGoal(goal);

        if (skillIndex.skills().isEmpty()) {
            logger.warn("Planner found no skills in the index");
            return PlanModels.empty(normalisedGoal, "No skills available");
        }

        if (normalisedGoal.isBlank()) {
            logger.warn("Planner received blank goal");
            return PlanModels.empty(normalisedGoal, "No skills selected");
        }

        // The actual tool invocation happens in the Agentic Framework
        // This is a placeholder that will be overridden by the framework's agent proxy
        return PlanModels.empty(normalisedGoal, "Planner agent initialized");
    }

    @Override
    public String listAvailableSkills(SkillIndex skillIndex) {
        List<SkillIndex.SkillMetadata> allSkills = new ArrayList<>(skillIndex.skills().values());
        allSkills.sort(Comparator.comparing(SkillIndex.SkillMetadata::id));

        List<SkillIndex.SkillMetadata> presentedSkills = allSkills.stream()
                .limit(MAX_PRESENTED_CANDIDATES)
                .toList();

        return formatSkillCatalog(presentedSkills);
    }

    @Override
    public PlanModels.PlanResult executeSkillSelection(String goal, SkillIndex skillIndex, String selectedSkillIds) {
        String normalisedGoal = normaliseGoal(goal);

        if (selectedSkillIds == null || selectedSkillIds.isBlank()) {
            logger.warn("No skill IDs provided for selection");
            return PlanModels.empty(normalisedGoal, "No skills selected");
        }

        List<SkillIndex.SkillMetadata> allSkills = new ArrayList<>(skillIndex.skills().values());
        List<PlanModels.PlanStep> allCandidates = allSkills.stream()
                .map(this::toPlanStep)
                .toList();

        // Parse the LLM's selection response (JSON or text format)
        List<OrderedStep> orderedSteps = extractOrderedSteps(selectedSkillIds, allCandidates);

        if (orderedSteps.isEmpty()) {
            logger.warn("Planner returned no recognizable skills for goal '{}'", normalisedGoal);
            return PlanModels.empty(normalisedGoal, "No skills selected");
        }

        // Map selected skills to PlanStep objects
        List<PlanModels.PlanStep> selectedSteps = new ArrayList<>();
        for (OrderedStep orderedStep : orderedSteps) {
            PlanModels.PlanStep metadata = allCandidates.stream()
                    .filter(step -> step.skillId().equals(orderedStep.skillId()))
                    .findFirst()
                    .orElse(null);

            if (metadata != null) {
                PlanModels.PlanStep resolved = new PlanModels.PlanStep(
                        metadata.skillId(),
                        metadata.name(),
                        metadata.description(),
                        metadata.keywords(),
                        normaliseStepGoal(orderedStep.stepGoal()),
                        metadata.skillRoot());
                selectedSteps.add(resolved);
            }
        }

        if (selectedSteps.isEmpty()) {
            logger.warn("Planner referenced unknown skills; returning empty plan");
            return PlanModels.empty(normalisedGoal, "No skills selected");
        }

        String summary = summarize(selectedSteps);
        logger.info("Planner selected skills: {}", selectedSteps.stream()
                .map(PlanModels.PlanStep::skillId)
                .collect(Collectors.toList()));

        return new PlanModels.PlanResult(normalisedGoal, selectedSteps, summary);
    }

    private String formatSkillCatalog(List<SkillIndex.SkillMetadata> skills) {
        StringBuilder sb = new StringBuilder();
        sb.append("Available skills:\n\n");

        for (SkillIndex.SkillMetadata skill : skills) {
            sb.append("- id: ").append(skill.id()).append("\n");
            sb.append("  name: ").append(skill.name()).append("\n");
            sb.append("  description: ").append(skill.description()).append("\n");
            if (!skill.keywords().isEmpty()) {
                sb.append("  keywords: ").append(skill.keywords()).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private List<OrderedStep> extractOrderedSteps(String response, List<PlanModels.PlanStep> allCandidates) {
        if (response == null || response.isBlank()) {
            return List.of();
        }

        List<OrderedStep> parsed = parseJsonSteps(response);
        if (!parsed.isEmpty()) {
            return filterToCandidates(parsed, allCandidates);
        }

        return inferByOccurrence(response, allCandidates);
    }

    private List<OrderedStep> parseJsonSteps(String content) {
        try {
            return parseJsonNode(OBJECT_MAPPER.readTree(content));
        } catch (JsonProcessingException ex) {
            int start = content.indexOf('{');
            int end = content.lastIndexOf('}');
            if (start >= 0 && end > start) {
                String slice = content.substring(start, end + 1);
                try {
                    return parseJsonNode(OBJECT_MAPPER.readTree(slice));
                } catch (JsonProcessingException ignore) {
                    logger.debug("Failed to parse planner JSON slice: {}", ignore.getMessage());
                }
            }
            logger.debug("Failed to parse planner JSON: {}", ex.getMessage());
        }
        return List.of();
    }

    private List<OrderedStep> parseJsonNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (node.isArray()) {
            List<OrderedStep> results = new ArrayList<>();
            for (JsonNode child : node) {
                results.addAll(parseJsonNode(child));
            }
            return results;
        }
        if (node.isObject()) {
            if (node.has("skill_steps")) {
                List<OrderedStep> steps = parseJsonNode(node.get("skill_steps"));
                if (!steps.isEmpty()) {
                    return steps;
                }
            }
            if (node.has("skill_ids")) {
                List<OrderedStep> ids = parseJsonNode(node.get("skill_ids"));
                if (!ids.isEmpty()) {
                    return ids;
                }
            }
            OrderedStep step = toOrderedStep(node);
            if (step != null) {
                return List.of(step);
            }
            List<OrderedStep> nested = new ArrayList<>();
            node.fieldNames().forEachRemaining(fieldName ->
                nested.addAll(parseJsonNode(node.get(fieldName))));
            return nested;
        }
        if (node.isTextual()) {
            return List.of(new OrderedStep(node.asText(), ""));
        }
        return List.of();
    }

    private OrderedStep toOrderedStep(JsonNode node) {
        String id = textValue(node, "skill_id", "skillId", "id");
        if (id == null || id.isBlank()) {
            return null;
        }
        String goal = textValue(node, "goal", "step_goal", "stepGoal");
        return new OrderedStep(id, goal == null ? "" : goal);
    }

    private String textValue(JsonNode node, String... candidates) {
        for (String field : candidates) {
            JsonNode child = node.get(field);
            if (child != null && child.isTextual()) {
                return child.asText();
            }
        }
        return null;
    }

    private List<OrderedStep> filterToCandidates(List<OrderedStep> orderedSteps, List<PlanModels.PlanStep> allCandidates) {
        Set<String> validIds = allCandidates.stream()
                .map(PlanModels.PlanStep::skillId)
                .collect(Collectors.toSet());

        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<OrderedStep> filtered = new ArrayList<>();

        for (OrderedStep step : orderedSteps) {
            if (step == null || step.skillId() == null) {
                continue;
            }
            String trimmed = step.skillId().trim();
            if (!trimmed.isEmpty() && validIds.contains(trimmed) && seen.add(trimmed)) {
                filtered.add(new OrderedStep(trimmed, normaliseStepGoal(step.stepGoal())));
            }
        }

        return filtered;
    }

    private List<OrderedStep> inferByOccurrence(String response, List<PlanModels.PlanStep> candidates) {
        String lower = response.toLowerCase(Locale.ROOT);
        record Match(String id, int index) {}

        List<Match> matches = new ArrayList<>();
        for (PlanModels.PlanStep step : candidates) {
            String id = step.skillId();
            int idx = lower.indexOf(id.toLowerCase(Locale.ROOT));
            if (idx >= 0) {
                matches.add(new Match(id, idx));
            }
        }

        matches.sort((a, b) -> Integer.compare(a.index(), b.index()));
        return matches.stream()
                .map(Match::id)
                .distinct()
                .map(id -> new OrderedStep(id, ""))
                .toList();
    }

    private PlanModels.PlanStep toPlanStep(SkillIndex.SkillMetadata metadata) {
        return new PlanModels.PlanStep(
                metadata.id(),
                metadata.name(),
                metadata.description(),
                metadata.keywords(),
                "",
                metadata.skillRoot());
    }

    private String summarize(List<PlanModels.PlanStep> steps) {
        return steps.stream()
                .map(step -> {
                    StringBuilder builder = new StringBuilder(String.format(
                            "%s: %s — %s",
                            step.skillId(),
                            step.name(),
                            step.description()));
                    if (!step.stepGoal().isBlank()) {
                        builder.append(" Goal: ").append(step.stepGoal());
                    }
                    if (!step.keywords().isEmpty()) {
                        builder.append(" (keywords: ")
                                .append(String.join(", ", step.keywords()))
                                .append(')');
                    }
                    return builder.toString();
                })
                .collect(Collectors.joining("\n"));
    }

    private String normaliseStepGoal(String goal) {
        if (goal == null) {
            return "";
        }
        String trimmed = goal.trim();
        return trimmed;
    }

    private String normaliseGoal(String goal) {
        return goal == null ? "" : goal.trim();
    }

    private record OrderedStep(String skillId, String stepGoal) {}
}
