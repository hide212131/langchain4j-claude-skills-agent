package io.github.hide212131.langchain4j.claude.skills.runtime.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@SuppressWarnings("PMD.JUnitTestContainsTooManyAsserts")
class LocalResourceToolTest {

    private static final String SKILL_MD = "SKILL.md";
    private static final String DUMMY = "dummy";

    @TempDir
    private Path tempDir;

    @SuppressWarnings("PMD.UnnecessaryConstructor")
    LocalResourceToolTest() {
        // default
    }

    @Test
    @DisplayName("ローカルファイルを読み取り、長文は省略フラグが立つ")
    void readFileTruncatesLongContent() throws IOException {
        Path skillMd = tempDir.resolve(SKILL_MD);
        Files.writeString(skillMd, DUMMY);
        Path docsDir = Files.createDirectories(tempDir.resolve("docs"));
        Path target = docsDir.resolve("note.md");
        Files.writeString(target, "a".repeat(4100));

        LocalResourceTool tool = new LocalResourceTool(skillMd);
        LocalResourceTool.LocalResourceContent content = tool.readFile("docs/note.md");

        assertThat(content.truncated()).isTrue();
        assertThat(content.content()).startsWith("a");
    }

    @Test
    @DisplayName("glob 指定でスキル配下のファイル一覧を取得する")
    void listFilesMatchesGlob() throws IOException {
        Path skillMd = tempDir.resolve(SKILL_MD);
        Files.writeString(skillMd, DUMMY);
        Path docsDir = Files.createDirectories(tempDir.resolve("docs"));
        Files.writeString(docsDir.resolve("note.md"), "note");
        Files.writeString(docsDir.resolve("data.txt"), "data");

        LocalResourceTool tool = new LocalResourceTool(skillMd);
        LocalResourceTool.LocalResourceList list = tool.listFiles("docs/*.md");

        assertThat(list.paths()).containsExactly("docs/note.md");
    }

    @Test
    @DisplayName("不正なパス指定は例外にする")
    void rejectInvalidPaths() throws IOException {
        Path skillMd = tempDir.resolve(SKILL_MD);
        Files.writeString(skillMd, DUMMY);
        LocalResourceTool tool = new LocalResourceTool(skillMd);

        assertThatThrownBy(() -> tool.readFile("/etc/passwd")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tool.readFile("../secrets.txt")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("空の pattern は例外にする")
    void rejectBlankPattern() throws IOException {
        Path skillMd = tempDir.resolve(SKILL_MD);
        Files.writeString(skillMd, DUMMY);
        LocalResourceTool tool = new LocalResourceTool(skillMd);

        assertThatThrownBy(() -> tool.listFiles(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
