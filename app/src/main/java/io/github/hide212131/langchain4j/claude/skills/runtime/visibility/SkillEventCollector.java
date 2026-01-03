package io.github.hide212131.langchain4j.claude.skills.runtime.visibility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** メモリ内にイベントを蓄積するシンプルなパブリッシャ。テスト向け。 */
public final class SkillEventCollector implements SkillEventPublisher {

    private final List<SkillEvent> buffer;

    public SkillEventCollector() {
        this.buffer = new ArrayList<>();
    }

    @Override
    public void publish(SkillEvent event) {
        buffer.add(Objects.requireNonNull(event, "event"));
    }

    public List<SkillEvent> events() {
        return Collections.unmodifiableList(buffer);
    }

    public void clear() {
        buffer.clear();
    }
}
