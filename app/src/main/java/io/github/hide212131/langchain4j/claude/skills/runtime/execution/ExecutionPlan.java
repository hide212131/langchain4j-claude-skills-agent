package io.github.hide212131.langchain4j.claude.skills.runtime.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;

public record ExecutionPlan(List<String> commands, List<String> outputPatterns) {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<String> DEFAULT_OUTPUT_PATTERNS = List.of("**/*.pptx", "**/*.png");

    public ExecutionPlan {
        Objects.requireNonNull(commands, "commands");
        if (commands.isEmpty()) {
            throw new IllegalArgumentException("commands は空にできません");
        }
        if (outputPatterns == null) {
            outputPatterns = List.of();
        }
    }

    public static ExecutionPlan parse(String rawPlan) {
        if (rawPlan == null || rawPlan.isBlank()) {
            throw new IllegalArgumentException("実行計画が空です");
        }
        String json = extractJson(rawPlan);
        try {
            return MAPPER.readValue(json, ExecutionPlan.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("実行計画の JSON 解析に失敗しました", ex);
        }
    }

    public List<String> effectiveOutputPatterns() {
        return outputPatterns.isEmpty() ? DEFAULT_OUTPUT_PATTERNS : outputPatterns;
    }

    private static String extractJson(String rawPlan) {
        int start = rawPlan.indexOf('{');
        int end = rawPlan.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return rawPlan.substring(start, end + 1);
        }
        return rawPlan;
    }
}
