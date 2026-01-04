package io.github.hide212131.langchain4j.claude.skills.runtime.visibility;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

public final class SkillEventAssertions {

    private SkillEventAssertions() {
    }

    /** 可視化イベントの検証を簡潔にするためのテスト補助。 */
    public static SkillEvent findByStep(List<SkillEvent> events, SkillEventType type, String step) {
        assertThat(events).isNotNull();
        return events.stream().filter(event -> event.type() == type)
                .filter(event -> step.equals(event.metadata().step())).findFirst()
                .orElseThrow(() -> new AssertionError("イベントが見つかりません: type=" + type + " step=" + step));
    }
}
