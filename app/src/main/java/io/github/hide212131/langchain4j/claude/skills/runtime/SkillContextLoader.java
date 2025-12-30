package io.github.hide212131.langchain4j.claude.skills.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class SkillContextLoader {

    private SkillContextLoader() {
        throw new AssertionError("インスタンス化できません");
    }

    static String load(Path skillMdPath, String skillBody) {
        Objects.requireNonNull(skillMdPath, "skillMdPath");
        Objects.requireNonNull(skillBody, "skillBody");
        Path skillDir = skillMdPath.getParent();
        if (skillDir == null) {
            return "";
        }
        List<String> references = new ArrayList<>();
        if (skillBody.contains("html2pptx.md")) {
            references.add("html2pptx.md");
        }
        if (skillBody.contains("ooxml.md")) {
            references.add("ooxml.md");
        }
        if (references.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String reference : references) {
            Path path = skillDir.resolve(reference);
            if (!Files.exists(path)) {
                continue;
            }
            builder.append("## 参照資料: ").append(reference).append(System.lineSeparator()).append(readAll(path))
                    .append(System.lineSeparator());
        }
        return builder.toString();
    }

    private static String readAll(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("参照資料の読み込みに失敗しました: " + path, ex);
        }
    }
}
