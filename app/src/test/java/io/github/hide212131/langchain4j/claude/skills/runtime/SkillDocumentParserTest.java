package io.github.hide212131.langchain4j.claude.skills.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SkillDocumentParserTest {

  private final SkillDocumentParser parser = new SkillDocumentParser();

  @Test
  @DisplayName("frontmatter と本文をパースして POJO に変換する")
  void parseFrontmatterAndBody() {
    Path skillMd =
        Path.of("src/test/resources/skills/sample/SKILL.md").toAbsolutePath().normalize();

    SkillDocument document = parser.parse(skillMd, "sample-skill");

    assertThat(document.id()).isEqualTo("sample-skill");
    assertThat(document.name()).isEqualTo("sample-skill");
    assertThat(document.description()).contains("Sample skill");
    assertThat(document.body()).contains("# Sample Skill Body");
  }

  @Test
  @DisplayName("必須項目が欠けている場合は日本語エラーメッセージで失敗する")
  void missingRequiredFieldsFails() {
    Path temp = Path.of("build/tmp/invalid-skill.md");
    temp.toFile().getParentFile().mkdirs();
    temp.toFile().deleteOnExit();
    temp.toFile().setWritable(true);
    try {
      java.nio.file.Files.writeString(
          temp,
          """
                    ---
                    name: invalid
                    ---
                    body only
                    """);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }

    assertThatThrownBy(() -> parser.parse(temp, "invalid"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("必須項目");
  }
}
