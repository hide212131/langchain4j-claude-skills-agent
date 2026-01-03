package io.github.hide212131.langchain4j.claude.skills.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.ParsePayload;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.PromptPayload;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.SkillEvent;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.SkillEventCollector;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.SkillEventType;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.SkillMasking;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@SuppressWarnings("PMD.JUnitTestContainsTooManyAsserts")
class SkillIntegrationTest {

    @SuppressWarnings("PMD.UnnecessaryConstructor")
    SkillIntegrationTest() {
        // default
    }

    @Test
    @DisplayName("SKILL.md パースから Plan/Act/Reflect までのスキルイベントを取得する")
    void collectSkillEventsFromSkill() {
        try (SkillEventCollector collector = new SkillEventCollector()) {
            SkillDocumentParser parser = new SkillDocumentParser(collector, SkillMasking.defaultRules());
            Path skillMd = Path.of("src/test/resources/skills/e2e/SKILL.md").toAbsolutePath().normalize();

            SkillDocument document = parser.parse(skillMd, "e2e-skill", "run-e2e");
            SkillLog log = new SkillLog(newSilentLogger());
            DummyAgentFlow flow = new DummyAgentFlow();

            flow.run(document, "integration goal", log, true, "run-e2e", skillMd.toString(), null, collector);

            List<SkillEvent> events = collector.events();
            assertThat(events).extracting(SkillEvent::type).contains(SkillEventType.PARSE, SkillEventType.PROMPT,
                    SkillEventType.AGENT_STATE);
            assertThat(events).allMatch(event -> "run-e2e".equals(event.metadata().runId()));

            SkillEvent bodyEvent = events.stream().filter(event -> "parse.body".equals(event.metadata().step()))
                    .findFirst().orElseThrow();
            assertThat(((ParsePayload) bodyEvent.payload()).bodyPreview()).contains("Plan/Act/Reflect");

            SkillEvent reflectEvent = events.stream().filter(event -> "reflect.eval".equals(event.metadata().step()))
                    .findFirst().orElseThrow();
            assertThat(((PromptPayload) reflectEvent.payload()).response()).contains("Reflect");
            assertThat(reflectEvent.metadata().skillId()).isEqualTo("e2e-skill");
        }
    }

    @Test
    @DisplayName("スキルイベント収集のオーバーヘッドが実用的な範囲に収まる")
    void measureCollectorOverhead() {
        try (SkillEventCollector collector = new SkillEventCollector()) {
            SkillDocumentParser parser = new SkillDocumentParser(collector, SkillMasking.defaultRules());
            Path skillMd = Path.of("src/test/resources/skills/e2e/SKILL.md").toAbsolutePath().normalize();
            SkillDocument document = parser.parse(skillMd, "e2e-skill", "run-perf");
            DummyAgentFlow flow = new DummyAgentFlow();
            SkillLog log = new SkillLog(newSilentLogger());

            long startNanos = System.nanoTime();
            for (int i = 0; i < 20; i++) {
                collector.clear();
                flow.run(document, "perf goal", log, true, "run-perf-" + i, skillMd.toString(), null, collector);
            }
            long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

            assertThat(durationMillis).isLessThan(1500L);
        }
    }

    private Logger newSilentLogger() {
        Logger logger = Logger.getLogger("skill-test-" + System.nanoTime());
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.OFF);
        return logger;
    }
}
