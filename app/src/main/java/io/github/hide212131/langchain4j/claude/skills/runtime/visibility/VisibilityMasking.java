package io.github.hide212131.langchain4j.claude.skills.runtime.visibility;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 秘匿情報を初期ルールでマスクするユーティリティ。 */
public final class VisibilityMasking {

    private static final int PREVIEW_LIMIT = 240;
    private static final String MASK_TOKEN = "****";
    private static final Set<String> DEFAULT_SENSITIVE_KEYS = Set.of("api_key", "token", "secret", "authorization",
            "password");

    private final Set<String> sensitiveKeys;

    public VisibilityMasking(Set<String> sensitiveKeys) {
        this.sensitiveKeys = Objects.requireNonNull(sensitiveKeys, "sensitiveKeys");
    }

    public static VisibilityMasking defaultRules() {
        return new VisibilityMasking(DEFAULT_SENSITIVE_KEYS);
    }

    public Map<String, Object> maskFrontMatter(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> masked = new HashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            masked.put(entry.getKey(), maskValue(entry.getKey(), entry.getValue()));
        }
        return masked;
    }

    public String maskPreview(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= PREVIEW_LIMIT) {
            return trimmed;
        }
        return trimmed.substring(0, PREVIEW_LIMIT) + "...";
    }

    private Object maskValue(String key, Object value) {
        if (isSensitiveKey(key)) {
            return MASK_TOKEN;
        }
        if (value instanceof Map<?, ?> nested) {
            Map<String, Object> nestedMap = new HashMap<>();
            for (Map.Entry<?, ?> entry : nested.entrySet()) {
                Object nestedKey = entry.getKey();
                if (nestedKey instanceof String nestedKeyString) {
                    nestedMap.put(nestedKeyString, maskValue(nestedKeyString, entry.getValue()));
                }
            }
            return nestedMap;
        }
        return value;
    }

    private boolean isSensitiveKey(String key) {
        String lowered = key.toLowerCase(Locale.ROOT);
        return sensitiveKeys.contains(lowered);
    }
}
