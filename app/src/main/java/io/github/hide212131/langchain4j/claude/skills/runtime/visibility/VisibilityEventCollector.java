package io.github.hide212131.langchain4j.claude.skills.runtime.visibility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** メモリ内にイベントを蓄積するシンプルなパブリッシャ。テスト向け。 */
public final class VisibilityEventCollector implements VisibilityEventPublisher {

    private final List<VisibilityEvent> buffer;

    public VisibilityEventCollector() {
        this.buffer = new ArrayList<>();
    }

    @Override
    public void publish(VisibilityEvent event) {
        buffer.add(Objects.requireNonNull(event, "event"));
    }

    public List<VisibilityEvent> events() {
        return Collections.unmodifiableList(buffer);
    }

    public void clear() {
        buffer.clear();
    }
}
