package io.github.hide212131.langchain4j.claude.skills.runtime.visibility;

/** 可視化イベント送信先のインターフェース。 */
@FunctionalInterface
public interface SkillEventPublisher extends AutoCloseable {

    void publish(SkillEvent event);

    @Override
    default void close() {
        // default no-op
    }

    static SkillEventPublisher noop() {
        return event -> {
            // no-op
        };
    }
}
