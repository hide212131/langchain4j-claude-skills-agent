package io.github.hide212131.langchain4j.claude.skills.runtime.observability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Serialises {@link AgenticScope} state into a LangFuse-friendly JSON payload.
 * Values are converted into primitives/collections to avoid reflection issues and
 * truncated to keep OTLP payloads bounded.
 */
public final class AgenticScopeSnapshots {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_STRING_LENGTH = 2048;
    private static final int MAX_JSON_LENGTH = 16384;
    private static final int MAX_DEPTH = 4;
    private static final int MAX_COLLECTION_ITEMS = 32;

    private AgenticScopeSnapshots() {}

    public static Optional<String> snapshot(AgenticScope scope) {
        if (scope == null) {
            return Optional.empty();
        }
        Map<String, Object> state = scope.state();
        if (state == null || state.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> serialisable = new LinkedHashMap<>();
        state.forEach((key, value) -> serialisable.put(key, sanitiseValue(value, 0)));
        String json = toJson(serialisable);
        if (json == null || json.isBlank() || "{}".equals(json)) {
            return Optional.empty();
        }
        if (json.length() > MAX_JSON_LENGTH) {
            json = json.substring(0, MAX_JSON_LENGTH) + "...(truncated)";
        }
        return Optional.of(json);
    }

    private static Object sanitiseValue(Object value, int depth) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof CharSequence sequence) {
            return limit(sequence.toString());
        }
        if (depth >= MAX_DEPTH) {
            return limit(String.valueOf(value));
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> nested = new LinkedHashMap<>();
            int count = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (count++ >= MAX_COLLECTION_ITEMS) {
                    nested.put("__truncated__", "...(truncated)");
                    break;
                }
                nested.put(String.valueOf(entry.getKey()), sanitiseValue(entry.getValue(), depth + 1));
            }
            return nested;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> items = new ArrayList<>();
            int count = 0;
            for (Object element : iterable) {
                if (count++ >= MAX_COLLECTION_ITEMS) {
                    items.add("...(truncated)");
                    break;
                }
                items.add(sanitiseValue(element, depth + 1));
            }
            return items;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> items = new ArrayList<>(Math.min(length, MAX_COLLECTION_ITEMS));
            for (int i = 0; i < length && i < MAX_COLLECTION_ITEMS; i++) {
                items.add(sanitiseValue(Array.get(value, i), depth + 1));
            }
            if (length > MAX_COLLECTION_ITEMS) {
                items.add("...(truncated)");
            }
            return items;
        }
        return limit(String.valueOf(value));
    }

    private static String limit(String value) {
        if (value == null) {
            return null;
        }
        if (value.length() <= MAX_STRING_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_STRING_LENGTH) + "...(truncated)";
    }

    private static String toJson(Map<String, Object> serialisable) {
        try {
            return OBJECT_MAPPER.writeValueAsString(serialisable);
        } catch (JsonProcessingException e) {
            return serialisable.toString();
        }
    }
}
