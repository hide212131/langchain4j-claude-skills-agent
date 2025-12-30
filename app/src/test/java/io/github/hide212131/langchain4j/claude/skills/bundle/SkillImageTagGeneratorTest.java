package io.github.hide212131.langchain4j.claude.skills.bundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * SkillImageTagGenerator のテスト
 */
class SkillImageTagGeneratorTest {

    @Test
    void generateImageTag_基本的なスキルパス() {
        String skillPath = "anthropics/skills/document-skills/pptx";
        String imageTag = SkillImageTagGenerator.generateImageTag(skillPath);
        assertEquals("anthropics-skills-document-skills-pptx:latest", imageTag);
    }

    @Test
    void generateImageTag_短いスキルパス() {
        String skillPath = "anthropics/skills/theme-factory";
        String imageTag = SkillImageTagGenerator.generateImageTag(skillPath);
        assertEquals("anthropics-skills-theme-factory:latest", imageTag);
    }

    @Test
    void generateImageTag_カスタムスキル() {
        String skillPath = "gotalab/skills/custom-skill";
        String imageTag = SkillImageTagGenerator.generateImageTag(skillPath);
        assertEquals("gotalab-skills-custom-skill:latest", imageTag);
    }

    @Test
    void generateImageTag_単一階層() {
        String skillPath = "simple-skill";
        String imageTag = SkillImageTagGenerator.generateImageTag(skillPath);
        assertEquals("simple-skill:latest", imageTag);
    }

    @Test
    void generateImageTag_null入力() {
        assertThrows(IllegalArgumentException.class, () -> {
            SkillImageTagGenerator.generateImageTag(null);
        });
    }

    @Test
    void generateImageTag_空文字列() {
        assertThrows(IllegalArgumentException.class, () -> {
            SkillImageTagGenerator.generateImageTag("");
        });
    }

    @Test
    void generateImageTag_空白のみ() {
        assertThrows(IllegalArgumentException.class, () -> {
            SkillImageTagGenerator.generateImageTag("   ");
        });
    }

    @Test
    void extractSkillPath_基本的なイメージタグ() {
        String imageTag = "anthropics-skills-document-skills-pptx:latest";
        String skillPath = SkillImageTagGenerator.extractSkillPath(imageTag);
        assertEquals("anthropics/skills/document/skills/pptx", skillPath);
    }

    @Test
    void extractSkillPath_バージョンなし() {
        String imageTag = "anthropics-skills-theme-factory";
        String skillPath = SkillImageTagGenerator.extractSkillPath(imageTag);
        assertEquals("anthropics/skills/theme/factory", skillPath);
    }

    @Test
    void extractSkillPath_null入力() {
        assertThrows(IllegalArgumentException.class, () -> {
            SkillImageTagGenerator.extractSkillPath(null);
        });
    }

    @Test
    void extractSkillPath_空文字列() {
        assertThrows(IllegalArgumentException.class, () -> {
            SkillImageTagGenerator.extractSkillPath("");
        });
    }

    @Test
    void ラウンドトリップ変換_基本パターン() {
        String originalPath = "anthropics/skills/document-skills/pptx";
        String imageTag = SkillImageTagGenerator.generateImageTag(originalPath);
        String extractedPath = SkillImageTagGenerator.extractSkillPath(imageTag);

        // 注意: "-" が含まれるスキルパスの場合、ラウンドトリップでは元に戻らない
        // これは規約の制限事項
        assertEquals("anthropics/skills/document/skills/pptx", extractedPath);
    }
}
