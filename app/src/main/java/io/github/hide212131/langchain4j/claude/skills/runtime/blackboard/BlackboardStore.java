package io.github.hide212131.langchain4j.claude.skills.runtime.blackboard;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal blackboard implementation backed by an in-memory map. It will be expanded with
 * AgenticScope synchronisation logic in later tasks.
 */
public final class BlackboardStore {

    private final Map<String, Object> artifacts = new ConcurrentHashMap<>();

    public void put(String key, Object value) {
        artifacts.put(key, value);
    }

    public Optional<Object> get(String key) {
        return Optional.ofNullable(artifacts.get(key));
    }

    public boolean contains(String key) {
        return artifacts.containsKey(key);
    }

    public void clear() {
        artifacts.clear();
    }

    public Map<String, Object> snapshot() {
        return Map.copyOf(artifacts);
    }
}
