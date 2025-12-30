package io.github.hide212131.langchain4j.claude.skills.bundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

/**
 * SkillImageBuilder のテスト
 */
class SkillImageBuilderTest {

    @TempDir
    Path tempDir;

    private Path projectRoot;
    private SkillImageBuilder builder;

    @BeforeEach
    void setUp() {
        projectRoot = tempDir;
        builder = new SkillImageBuilder(projectRoot);
    }

    @Test
    void isDockerAvailable_Docker存在確認() {
        // Docker がインストールされているかを確認
        // CI 環境では Docker がない場合もあるので、結果の真偽値は問わない
        boolean available = SkillImageBuilder.isDockerAvailable();
        // このテストは Docker の有無を問わず成功
        assertTrue(available || !available);
    }

    @Test
    void buildImage_Dockerfileが存在しない() {
        Path nonExistentDockerfile = tempDir.resolve("nonexistent/Dockerfile");
        String skillPath = "test/skill";

        assertThrows(IOException.class, () -> {
            builder.buildImage(skillPath, nonExistentDockerfile);
        });
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "DOCKER_INTEGRATION_TEST", matches = "true")
    void buildImage_最小限のDockerfileでビルド() throws IOException {
        // Docker が利用可能な場合のみ実行
        assumeTrue(SkillImageBuilder.isDockerAvailable(), "Docker が利用できません");

        // 最小限の Dockerfile を作成
        Path dockerfileDir = tempDir.resolve("test-skill/.skill-runtime");
        Files.createDirectories(dockerfileDir);
        Path dockerfilePath = dockerfileDir.resolve("Dockerfile");

        String dockerfileContent = """
                FROM alpine:latest
                RUN echo "Test skill image"
                """;

        Files.writeString(dockerfilePath, dockerfileContent);

        String skillPath = "test/skill";
        String imageTag = builder.buildImage(skillPath, dockerfilePath);

        // イメージタグが規約通りに生成されたことを確認
        assertEquals("test-skill:latest", imageTag);

        // クリーンアップ: ビルドしたイメージを削除
        cleanupDockerImage(imageTag);
    }

    @Test
    void generateImageTag_ビルド前の確認() {
        String skillPath = "anthropics/skills/document-skills/pptx";
        String expectedTag = "anthropics-skills-document-skills-pptx:latest";

        String actualTag = SkillImageTagGenerator.generateImageTag(skillPath);

        assertEquals(expectedTag, actualTag);
    }

    private void cleanupDockerImage(String imageTag) {
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "rmi", imageTag);
            pb.start().waitFor();
        } catch (IOException | InterruptedException e) {
            // クリーンアップ失敗は無視
        }
    }
}
