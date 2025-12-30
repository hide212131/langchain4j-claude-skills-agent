package io.github.hide212131.langchain4j.claude.skills.bundle;

/**
 * スキルパスから Docker イメージタグを規約ベースで生成する
 *
 * <p>
 * 規約:
 * <ul>
 * <li>スキルパスの "/" を "-" に置き換える</li>
 * <li>バージョンタグは ":latest" 固定（初期実装）</li>
 * <li>プレフィックス（"skill-" など）は付与しない</li>
 * </ul>
 *
 * <p>
 * 例:
 * <ul>
 * <li>"anthropics/skills/document-skills/pptx" →
 * "anthropics-skills-document-skills-pptx:latest"</li>
 * <li>"anthropics/skills/theme-factory" →
 * "anthropics-skills-theme-factory:latest"</li>
 * <li>"gotalab/skills/custom-skill" → "gotalab-skills-custom-skill:latest"</li>
 * </ul>
 */
public class SkillImageTagGenerator {

    private static final String VERSION_TAG = "latest";

    /**
     * スキルパスから Docker イメージタグを生成
     *
     * @param skillPath スキルパス（例: "anthropics/skills/document-skills/pptx"）
     * @return イメージタグ（例: "anthropics-skills-document-skills-pptx:latest"）
     * @throws IllegalArgumentException skillPath が null または空の場合
     */
    public static String generateImageTag(String skillPath) {
        if (skillPath == null || skillPath.isBlank()) {
            throw new IllegalArgumentException("skillPath は null または空にできません");
        }

        String normalized = skillPath.replace("/", "-");
        return normalized + ":" + VERSION_TAG;
    }

    /**
     * イメージタグからスキルパスを逆算する（デバッグ/検証用）
     *
     * @param imageTag イメージタグ（例: "anthropics-skills-document-skills-pptx:latest"）
     * @return スキルパス（例: "anthropics/skills/document-skills/pptx"）
     * @throws IllegalArgumentException imageTag が null または無効な形式の場合
     */
    public static String extractSkillPath(String imageTag) {
        if (imageTag == null || imageTag.isBlank()) {
            throw new IllegalArgumentException("imageTag は null または空にできません");
        }

        // ":latest" を削除
        String withoutVersion = imageTag;
        if (imageTag.contains(":")) {
            withoutVersion = imageTag.substring(0, imageTag.lastIndexOf(":"));
        }

        // "-" を "/" に戻す
        return withoutVersion.replace("-", "/");
    }
}
