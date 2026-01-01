package io.github.hide212131.langchain4j.claude.skills.runtime.planning;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 実行タスクの実行場所。
 */
public enum ExecutionTaskLocation {
    LOCAL("ローカル"), REMOTE("リモート");

    private final String displayLabel;
    private static final Map<String, ExecutionTaskLocation> LOOKUP = buildLookup();

    ExecutionTaskLocation(String label) {
        this.displayLabel = label;
    }

    public String label() {
        return displayLabel;
    }

    @JsonCreator
    public static ExecutionTaskLocation from(String value) {
        if (value == null || value.isBlank()) {
            return LOCAL;
        }
        ExecutionTaskLocation mapped = LOOKUP.get(normalizeKey(value));
        if (mapped != null) {
            return mapped;
        }
        throw new IllegalArgumentException("不明な実行場所です: " + value);
    }

    private static String normalizeKey(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static Map<String, ExecutionTaskLocation> buildLookup() {
        Map<String, ExecutionTaskLocation> map = new HashMap<>();
        for (ExecutionTaskLocation location : values()) {
            map.put(normalizeKey(location.name()), location);
            map.put(normalizeKey(location.displayLabel), location);
        }
        map.put("local", LOCAL);
        map.put("remote", REMOTE);
        return Map.copyOf(map);
    }

}
