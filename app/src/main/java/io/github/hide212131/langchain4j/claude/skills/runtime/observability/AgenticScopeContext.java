package io.github.hide212131.langchain4j.claude.skills.runtime.observability;

import dev.langchain4j.agentic.scope.AgenticScope;

/**
 * Thread-local bridge that exposes the current {@link AgenticScope} so instrumentation
 * (e.g. llm.chat spans) can attach full scope snapshots to their spans.
 */
final class AgenticScopeContext {

    private static final ThreadLocal<AgenticScope> CURRENT = new ThreadLocal<>();

    private AgenticScopeContext() {}

    /**
     * Sets the current scope for the calling thread and returns the previously registered scope.
     */
    static AgenticScope set(AgenticScope scope) {
        AgenticScope previous = CURRENT.get();
        if (scope == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(scope);
        }
        return previous;
    }

    static void restore(AgenticScope previous) {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }

    static AgenticScope current() {
        return CURRENT.get();
    }
}
