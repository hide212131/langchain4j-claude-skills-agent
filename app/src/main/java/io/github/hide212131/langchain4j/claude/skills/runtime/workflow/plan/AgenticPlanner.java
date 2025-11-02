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

        List<String> orderedSkillIds = extractSkillOrder(completion.content(), presentedCandidates, candidates);
        if (orderedSkillIds.isEmpty()) {
            logger.warn("Agentic planner returned no recognizable skills for goal '{}'", normalisedGoal);
            return PlanModels.empty(normalisedGoal, "No skills selected");
        }

        Map<String, PlanModels.PlanStep> stepById = candidates.stream()
                .collect(Collectors.toMap(PlanModels.PlanStep::skillId, step -> step, (a, b) -> a));
        List<PlanModels.PlanStep> selectedSteps = orderedSkillIds.stream()
                .map(stepById::get)
                .filter(Objects::nonNull)
                .toList();

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
                + "Reply strictly as JSON with a root object containing `skill_ids`, an array of "
                + "skill identifiers in execution order. Only use identifiers from the list below." + "\n\n"
                + "Goal: " + goal + "\n\n"
                + "Available skills (top candidates):\n"
                + candidateBlock + "\n\n"
                + "JSON schema: {\"skill_ids\": [\"id-1\", \"id-2\"]}";
    }

    private List<String> extractSkillOrder(
            String response, List<PlanModels.PlanStep> presentedCandidates, List<PlanModels.PlanStep> allCandidates) {
        if (response == null || response.isBlank()) {
            return List.of();
        }
        List<String> parsed = parseJsonSkillIds(response);
        if (!parsed.isEmpty()) {
            return filterToCandidates(parsed, presentedCandidates, allCandidates);
        }
        return inferByOccurrence(response, presentedCandidates);
    }

    private List<String> parseJsonSkillIds(String content) {
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

    private List<String> parseJsonNode(JsonNode node) {
        if (node == null) {
            return List.of();
        }
        List<String> results = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode child : node) {
                results.addAll(parseJsonNode(child));
            }
            return dedupe(results);
        }
        if (node.isObject()) {
            if (node.has("skill_ids")) {
                results.addAll(parseJsonNode(node.get("skill_ids")));
            }
            if (node.has("skills")) {
                results.addAll(parseJsonNode(node.get("skills")));
            }
            if (node.has("plan")) {
                results.addAll(parseJsonNode(node.get("plan")));
            }
            if (node.has("steps")) {
                results.addAll(parseJsonNode(node.get("steps")));
            }
            if (results.isEmpty()) {
                results.addAll(node.findValuesAsText("skill_id"));
                results.addAll(node.findValuesAsText("skillId"));
                if (results.isEmpty() && node.has("id") && node.get("id").isTextual()) {
                    results.add(node.get("id").asText());
                }
            }
            return dedupe(results);
        }
        if (node.isTextual()) {
            return List.of(node.asText());
        }
        return List.of();
    }

    private List<String> dedupe(List<String> values) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                seen.add(value.trim());
            }
        }
        return new ArrayList<>(seen);
    }

    private List<String> filterToCandidates(
            List<String> orderedIds,
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
        List<String> filtered = new ArrayList<>();
        for (String id : orderedIds) {
            if (id == null) {
                continue;
            }
            String trimmed = id.trim();
            if (validIds.contains(trimmed) && !filtered.contains(trimmed)) {
                filtered.add(trimmed);
            }
        }
        return filtered;
    }

    private List<String> inferByOccurrence(String response, List<PlanModels.PlanStep> candidates) {
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
        return matches.stream().map(Match::id).distinct().toList();
    }

    private String summarize(List<PlanModels.PlanStep> steps) {
        return steps.stream()
                .map(step -> String.format(
                        "%s: %s — %s (keywords: %s)",
                        step.skillId(),
                        step.name(),
                        step.description(),
                        String.join(", ", step.keywords())))
                .collect(Collectors.joining("\n"));
    }

    private String normaliseGoal(String goal) {
        return goal == null ? "" : goal.trim();
    }

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
