package io.github.hide212131.langchain4j.claude.skills.runtime.langfuse;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** JSON を走査して特定キーの値を収集する。 */
public final class LangfuseJsonScan {

    private LangfuseJsonScan() {
    }

    public static List<FoundValue> findValues(JsonNode root, String key) {
        List<FoundValue> results = new ArrayList<>();
        walk(root, "", key, results);
        return results;
    }

    public static List<FoundValue> findValuesAnyOf(JsonNode root, List<String> keys) {
        List<FoundValue> results = new ArrayList<>();
        walkAny(root, "", keys, results);
        return results;
    }

    @SuppressWarnings("checkstyle:CognitiveComplexity")
    private static void walk(JsonNode node, String path, String key, List<FoundValue> out) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String childPath = path.isEmpty() ? entry.getKey() : path + "." + entry.getKey();
                if (entry.getKey().equals(key)) {
                    out.add(new FoundValue(childPath, entry.getValue()));
                }
                walk(entry.getValue(), childPath, key, out);
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                walk(node.get(i), path + "[" + i + "]", key, out);
            }
        }
    }

    @SuppressWarnings("checkstyle:CognitiveComplexity")
    private static void walkAny(JsonNode node, String path, List<String> keys, List<FoundValue> out) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String childPath = path.isEmpty() ? entry.getKey() : path + "." + entry.getKey();
                if (keys.contains(entry.getKey())) {
                    out.add(new FoundValue(childPath, entry.getValue()));
                }
                walkAny(entry.getValue(), childPath, keys, out);
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                walkAny(node.get(i), path + "[" + i + "]", keys, out);
            }
        }
    }

    public record FoundValue(String path, JsonNode value) {
    }
}
