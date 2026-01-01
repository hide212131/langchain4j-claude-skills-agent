package io.github.hide212131.langchain4j.claude.skills.runtime.planning;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 実行計画のステータス。
 */
public enum ExecutionTaskStatus {
    PENDING("未実施"), IN_PROGRESS("実行中"), FAILED("異常終了"), COMPLETED("完了");

    private final String displayLabel;
    private static final Map<String, ExecutionTaskStatus> LOOKUP = buildLookup();

    ExecutionTaskStatus(String label) {
        this.displayLabel = label;
    }

    public String label() {
        return displayLabel;
    }

    @JsonCreator
    public static ExecutionTaskStatus from(String value) {
        if (value == null || value.isBlank()) {
            return PENDING;
        }
        ExecutionTaskStatus mapped = LOOKUP.get(normalizeKey(value));
        if (mapped != null) {
            return mapped;
        }
        throw new IllegalArgumentException("不明なステータスです: " + value);
    }

    private static String normalizeKey(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static Map<String, ExecutionTaskStatus> buildLookup() {
        Map<String, ExecutionTaskStatus> map = new HashMap<>();
        for (ExecutionTaskStatus status : values()) {
            map.put(normalizeKey(status.name()), status);
            map.put(normalizeKey(status.displayLabel), status);
        }
        map.put("pending", PENDING);
        map.put("todo", PENDING);
        map.put("not_started", PENDING);
        map.put("in_progress", IN_PROGRESS);
        map.put("running", IN_PROGRESS);
        map.put("executing", IN_PROGRESS);
        map.put("failed", FAILED);
        map.put("error", FAILED);
        map.put("aborted", FAILED);
        map.put("completed", COMPLETED);
        map.put("done", COMPLETED);
        map.put("success", COMPLETED);
        return Map.copyOf(map);
    }

}
