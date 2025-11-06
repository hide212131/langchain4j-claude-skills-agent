package io.github.hide212131.langchain4j.claude.skills.runtime.workflow;

import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.Objects;
import java.util.Optional;

/**
 * Typed wrapper around {@link AgenticScope} state keys to centralise key names and
 * enforce type-safe read/write operations.
 */
public final class AgentStateKey<T> {

    private final String key;
    private final Class<T> type;

    private AgentStateKey(String key, Class<T> type) {
        this.key = Objects.requireNonNull(key, "key");
        this.type = Objects.requireNonNull(type, "type");
    }

    public static <T> AgentStateKey<T> of(String key, Class<T> type) {
        return new AgentStateKey<>(key, type);
    }

    public void write(AgenticScope scope, T value) {
        Objects.requireNonNull(scope, "scope");
        scope.writeState(key, value);
    }

    public Optional<T> readOptional(AgenticScope scope) {
        Objects.requireNonNull(scope, "scope");
        if (!scope.hasState(key)) {
            return Optional.empty();
        }
        Object value = scope.readState(key);
        if (value == null) {
            return Optional.empty();
        }
        if (!type.isInstance(value)) {
            throw new IllegalStateException("State '" + key + "' expected type "
                    + type.getName() + " but found " + value.getClass().getName());
        }
        return Optional.of(type.cast(value));
    }

    public T readOrNull(AgenticScope scope) {
        return readOptional(scope).orElse(null);
    }

    public T readRequired(AgenticScope scope) {
        return readOptional(scope)
                .orElseThrow(() -> new IllegalStateException("Missing required AgenticScope state: " + key));
    }

    public boolean exists(AgenticScope scope) {
        Objects.requireNonNull(scope, "scope");
        return scope.hasState(key);
    }

    public String key() {
        return key;
    }

    public Class<T> type() {
        return type;
    }
}
