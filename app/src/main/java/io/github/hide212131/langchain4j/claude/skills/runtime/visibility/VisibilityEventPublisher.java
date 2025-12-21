package io.github.hide212131.langchain4j.claude.skills.runtime.visibility;

/** 可視化イベント送信先のインターフェース。 */
@FunctionalInterface
public interface VisibilityEventPublisher extends AutoCloseable {

    void publish(VisibilityEvent event);

    @Override
    default void close() {
        // default no-op
    }

    static VisibilityEventPublisher noop() {
        return event -> {
            // no-op
        };
    }
}
