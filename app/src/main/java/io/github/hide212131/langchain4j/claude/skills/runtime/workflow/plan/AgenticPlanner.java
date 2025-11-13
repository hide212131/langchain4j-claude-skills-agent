package io.github.hide212131.langchain4j.claude.skills.runtime.workflow.plan;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.hide212131.langchain4j.claude.skills.infra.logging.WorkflowLogger;
import io.github.hide212131.langchain4j.claude.skills.runtime.provider.LangChain4jLlmClient;
import io.github.hide212131.langchain4j.claude.skills.runtime.skill.SkillIndex;
import io.github.hide212131.langchain4j.claude.skills.runtime.skill.SkillIndex.SkillMetadata;
import io.opentelemetry.api.trace.Span;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Comparator;

/**
 * Planner that delegates skill selection and ordering to the LLM agent.
 */
public final class AgenticPlanner {

    private static final int MAX_PRESENTED_CANDIDATES = 100;

    private final SkillIndex skillIndex;
    private final LangChain4jLlmClient llmClient;
    private final WorkflowLogger logger;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgenticPlanner(SkillIndex skillIndex, LangChain4jLlmClient llmClient, WorkflowLogger logger) {
        this.skillIndex = Objects.requireNonNull(skillIndex, "skillIndex");
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public PlanModels.PlanResult plan(String goal) {
        String normalisedGoal = normaliseGoal(goal);
        List<SkillMetadata> allSkills = new ArrayList<>(skillIndex.skills().values());
        allSkills.sort(Comparator.comparing(SkillMetadata::id));

        if (allSkills.isEmpty()) {
            logger.warn("Agentic planner found no skills in the index");
            return PlanModels.empty(normalisedGoal, "No skills available");
        }

        List<PlanModels.PlanStep> candidates = allSkills.stream()
                .map(this::toPlanStep)
                .toList();

        List<PlanModels.PlanStep> presentedCandidates = candidates.stream()
                .limit(MAX_PRESENTED_CANDIDATES)
                .toList();

        if (normalisedGoal.isBlank()) {
            logger.warn("Agentic planner received blank goal; returning empty plan");
            return PlanModels.empty(normalisedGoal, "No skills selected");
        }

        String prompt = buildPrompt(normalisedGoal, presentedCandidates);
        LangChain4jLlmClient.CompletionResult completion;
        try {
            completion = llmClient.complete(prompt);
        } catch (RuntimeException ex) {
            logger.warn("Agentic planner call failed: {}", ex.getMessage());
            return PlanModels.empty(normalisedGoal, "No skills selected");
        }

        recordPlanTelemetry(prompt, completion);

        List<OrderedStep> orderedSteps =
                extractOrderedSteps(completion.content(), presentedCandidates, candidates);
        if (orderedSteps.isEmpty()) {
            logger.warn("Agentic planner returned no recognizable skills for goal '{}'", normalisedGoal);
            return PlanModels.empty(normalisedGoal, "No skills selected");
        }

        Map<String, PlanModels.PlanStep> stepById = candidates.stream()
                .collect(Collectors.toMap(PlanModels.PlanStep::skillId, step -> step, (a, b) -> a));
        List<PlanModels.PlanStep> selectedSteps = new ArrayList<>();
        List<String> orderedSkillIds = new ArrayList<>();

        for (OrderedStep orderedStep : orderedSteps) {
            PlanModels.PlanStep metadata = stepById.get(orderedStep.skillId());
            if (metadata == null) {
                continue;
            }
            PlanModels.PlanStep resolved = new PlanModels.PlanStep(
                    metadata.skillId(),
                    metadata.name(),
                    metadata.description(),
                    metadata.keywords(),
                    normaliseStepGoal(orderedStep.stepGoal()),
                    metadata.skillRoot());
            selectedSteps.add(resolved);
            orderedSkillIds.add(resolved.skillId());
        }

        if (selectedSteps.isEmpty()) {
            logger.warn("Agentic planner referenced unknown skills; returning empty plan");
            return PlanModels.empty(normalisedGoal, "No skills selected");
        }

        String summary = summarize(selectedSteps);
        logger.info("Agentic planner selected skills {}", orderedSkillIds);
        return new PlanModels.PlanResult(normalisedGoal, selectedSteps, summary);
    }

    public PlanModels.PlanResult planWithFixedOrder(String goal, List<String> requestedSkillIds) {
        String normalisedGoal = normaliseGoal(goal);
        if (requestedSkillIds == null || requestedSkillIds.isEmpty()) {
            logger.warn("Forced skill list was empty; falling back to empty plan");
            return PlanModels.empty(normalisedGoal, "Forced skill list was empty");
        }

        LinkedHashSet<String> orderedUniqueIds = new LinkedHashSet<>();
        for (String value : requestedSkillIds) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                orderedUniqueIds.add(trimmed);
            }
        }

        if (orderedUniqueIds.isEmpty()) {
            logger.warn("Forced skill list normalised to nothing; returning empty plan");
            return PlanModels.empty(normalisedGoal, "Forced skill list contained only blanks");
        }

        List<String> normalisedIds = new ArrayList<>(orderedUniqueIds);
        List<String> missing = new ArrayList<>();
        List<PlanModels.PlanStep> steps = new ArrayList<>();

        for (String id : normalisedIds) {
            SkillMetadata metadata = skillIndex.skills().get(id);
            if (metadata == null) {
                missing.add(id);
                continue;
            }
            steps.add(toPlanStep(metadata));
        }

        if (steps.isEmpty()) {
            logger.warn("Forced skill list {} produced no recognised skills", normalisedIds);
            String summary = missing.isEmpty()
                    ? "Forced skill list produced no recognised skills"
                    : "Forced skill list missing skills: " + String.join(", ", missing);
            return PlanModels.empty(normalisedGoal, summary);
        }

        if (!missing.isEmpty()) {
            logger.warn("Forced skill list contained unknown skills {}", missing);
        }

        logger.info("Bypassing planner with forced skill order {}", steps.stream()
                .map(PlanModels.PlanStep::skillId)
                .collect(Collectors.toList()));

        String summary = summarize(steps);
        if (!missing.isEmpty()) {
            summary = summary + "\nMissing skill ids: " + String.join(", ", missing);
        }
        return new PlanModels.PlanResult(normalisedGoal, steps, summary);
    }

    private PlanModels.PlanStep toPlanStep(SkillMetadata metadata) {
        return new PlanModels.PlanStep(
                metadata.id(),
                metadata.name(),
                metadata.description(),
                metadata.keywords(),
                "",
                metadata.skillRoot());
    }

    private String buildPrompt(String goal, List<PlanModels.PlanStep> candidates) {
        String candidateBlock = candidates.stream()
                .map(step -> String.format(
                        "- id: %s%n  name: %s%n  description: %s%n  keywords: %s",
                        step.skillId(),
                        step.name(),
                        step.description(),
                        step.keywords().isEmpty() ? "[]" : step.keywords()))
                .collect(Collectors.joining("\n\n"));

        return "You are an expert workflow planner. Given a user goal in Japanese "
                + "or English, choose the minimal ordered list of skills required to satisfy it. "
                + "Reply strictly as JSON with a root object containing `skill_steps`, an array where each "
                + "element has `skill_id` and `goal`. The goal must describe the artifact produced by the "
                + "step and, when useful, how the skill will be used to create it. Do not ask the user for "
                + "confirmation or next steps; simply proceed to the next milestone. "
                + "Use only skill identifiers "
                + "from the list below. Also include `skill_ids`, mirroring the identifiers in order for "
                + "backward compatibility." + "\n\n"
                + "Goal: " + goal + "\n\n"
                + "Available skills (top candidates):\n"
                + candidateBlock + "\n\n"
                + "JSON schema: {\"skill_steps\": [{\"skill_id\": \"id-1\", \"goal\": \"artifact description\"}], "
                + "\"skill_ids\": [\"id-1\", \"id-2\"]}";
    }

    private List<OrderedStep> extractOrderedSteps(
            String response, List<PlanModels.PlanStep> presentedCandidates, List<PlanModels.PlanStep> allCandidates) {
        if (response == null || response.isBlank()) {
            return List.of();
        }
        List<OrderedStep> parsed = parseJsonSteps(response);
        if (!parsed.isEmpty()) {
            return filterToCandidates(parsed, presentedCandidates, allCandidates);
        }
        return inferByOccurrence(response, presentedCandidates);
    }

    private List<OrderedStep> parseJsonSteps(String content) {
        try {
            return parseJsonNode(objectMapper.readTree(content));
        } catch (JsonProcessingException ex) {
            int start = content.indexOf('{');
            int end = content.lastIndexOf('}');
            if (start >= 0 && end > start) {
                String slice = content.substring(start, end + 1);
                try {
                    return parseJsonNode(objectMapper.readTree(slice));
                } catch (JsonProcessingException ignore) {
                    logger.debug("Failed to parse agentic planner JSON slice: {}", ignore.getMessage());
                }
            }
            logger.debug("Failed to parse agentic planner JSON: {}", ex.getMessage());
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
            node.fields().forEachRemaining(entry -> nested.addAll(parseJsonNode(entry.getValue())));
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

    private List<OrderedStep> filterToCandidates(
            List<OrderedStep> orderedSteps,
            List<PlanModels.PlanStep> presentedCandidates,
            List<PlanModels.PlanStep> allCandidates) {
        Set<String> validIds = presentedCandidates.stream()
                .map(PlanModels.PlanStep::skillId)
                .collect(Collectors.toSet());
        if (validIds.isEmpty()) {
            validIds = allCandidates.stream()
                    .map(PlanModels.PlanStep::skillId)
                    .collect(Collectors.toSet());
        }
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

    private void recordPlanTelemetry(String prompt, LangChain4jLlmClient.CompletionResult completion) {
        Span span = Span.current();
        if (!span.isRecording()) {
            return;
        }
        if (prompt != null && !prompt.isBlank()) {
            span.setAttribute("plan.llm.prompt", prompt);
        }
        if (completion != null && completion.content() != null) {
            span.setAttribute("plan.llm.response", completion.content());
            span.setAttribute("plan.llm.durationMs", completion.durationMs());
            if (completion.tokenUsage() != null) {
                if (completion.tokenUsage().inputTokenCount() != null) {
                    span.setAttribute("plan.llm.inputTokens", completion.tokenUsage().inputTokenCount());
                }
                if (completion.tokenUsage().outputTokenCount() != null) {
                    span.setAttribute("plan.llm.outputTokens", completion.tokenUsage().outputTokenCount());
                }
                if (completion.tokenUsage().totalTokenCount() != null) {
                    span.setAttribute("plan.llm.totalTokens", completion.tokenUsage().totalTokenCount());
                }
            }
        }
    }
}
