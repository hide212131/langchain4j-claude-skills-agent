package io.github.hide212131.langchain4j.claude.skills.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.ParsePayload;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.VisibilityEvent;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.VisibilityEventCollector;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.VisibilityEventType;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.VisibilityMasking;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@SuppressWarnings("PMD.JUnitTestContainsTooManyAsserts")
class SkillDocumentParserTest {

    private final SkillDocumentParser parser = new SkillDocumentParser();

    @SuppressWarnings("PMD.UnnecessaryConstructor")
    SkillDocumentParserTest() {
        // default
    }

    @Test
    @DisplayName("frontmatter と本文をパースして POJO に変換する")
    void parseFrontmatterAndBody() {
        Path skillMd = Path.of("src/test/resources/skills/sample/SKILL.md").toAbsolutePath().normalize();

        SkillDocument document = parser.parse(skillMd, "sample-skill");

        assertThat(document.id()).isEqualTo("sample-skill");
        assertThat(document.name()).isEqualTo("sample-skill");
        assertThat(document.description()).contains("Sample skill");
        assertThat(document.body()).contains("# Sample Skill Body");
    }

    @Test
    @DisplayName("パース時に可視化イベントを出力し、秘匿情報はマスクされる")
    void parseEmitsVisibilityEventsWithMasking() {
        Path temp = Path.of("build/tmp/visibility-skill.md");
        temp.toFile().getParentFile().mkdirs();
        temp.toFile().deleteOnExit();
        temp.toFile().setWritable(true);
        try {
            java.nio.file.Files.writeString(temp, """
                    ---
                    name: visibility
                    description: demo
                    inputs:
                      api_key: secret-123
                    ---
                    Body with token
                    """);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        try (VisibilityEventCollector collector = new VisibilityEventCollector()) {
            SkillDocumentParser instrumented = new SkillDocumentParser(collector, VisibilityMasking.defaultRules());

            SkillDocument document = instrumented.parse(temp, "visibility", "run-visibility");

            assertThat(document.id()).isEqualTo("visibility");
            List<VisibilityEvent> events = collector.events();
            assertThat(events).hasSizeGreaterThanOrEqualTo(2);
            Map<VisibilityEventType, List<VisibilityEvent>> grouped = events.stream()
                    .collect(Collectors.groupingBy(VisibilityEvent::type));
            assertThat(grouped.get(VisibilityEventType.PARSE)).isNotNull();

            VisibilityEvent frontMatterEvent = grouped.get(VisibilityEventType.PARSE).stream()
                    .filter(event -> "parse.frontmatter".equals(event.metadata().step())).findFirst().orElseThrow();
            ParsePayload frontMatterPayload = (ParsePayload) frontMatterEvent.payload();
            Map<String, Object> maskedInputs = (Map<String, Object>) frontMatterPayload.frontMatter().get("inputs");
            assertThat(maskedInputs.get("api_key")).isEqualTo("****");
            assertThat(frontMatterEvent.metadata().runId()).isEqualTo("run-visibility");
            assertThat(frontMatterEvent.metadata().skillId()).isEqualTo("visibility");

            VisibilityEvent bodyEvent = grouped.get(VisibilityEventType.PARSE).stream()
                    .filter(event -> "parse.body".equals(event.metadata().step())).findFirst().orElseThrow();
            ParsePayload bodyPayload = (ParsePayload) bodyEvent.payload();
            assertThat(bodyPayload.bodyPreview()).contains("Body with token");
        }
    }

    @Test
    @DisplayName("必須項目が欠けている場合は日本語エラーメッセージで失敗する")
    void missingRequiredFieldsFails() {
        Path temp = Path.of("build/tmp/invalid-skill.md");
        temp.toFile().getParentFile().mkdirs();
        temp.toFile().deleteOnExit();
        temp.toFile().setWritable(true);
        try {
            java.nio.file.Files.writeString(temp, """
                    ---
                    name: invalid
                    ---
                    body only
                    """);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        assertThatThrownBy(() -> parser.parse(temp, "invalid")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必須項目");
    }
}
