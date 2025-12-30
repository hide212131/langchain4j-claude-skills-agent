package io.github.hide212131.langchain4j.claude.skills.runtime.execution;

import java.nio.file.Path;
import java.util.Objects;

final class SkillPathResolver {

    private static final String SKILLS_DIR = "skills";

    private SkillPathResolver() {
        throw new AssertionError("インスタンス化できません");
    }

    static String resolveSkillPath(Path skillMdPath) {
        Objects.requireNonNull(skillMdPath, "skillMdPath");
        Path skillDir = skillMdPath.getParent();
        if (skillDir == null) {
            throw new IllegalArgumentException("SKILL.md の親ディレクトリを取得できません: " + skillMdPath);
        }
        int nameCount = skillDir.getNameCount();
        int skillsIndex = -1;
        for (int i = 0; i < nameCount; i++) {
            if (SKILLS_DIR.equals(skillDir.getName(i).toString())) {
                skillsIndex = i;
                break;
            }
        }
        if (skillsIndex < 0 || skillsIndex + 1 >= nameCount) {
            throw new IllegalArgumentException("スキルパスを解決できません: " + skillMdPath);
        }
        Path relative = skillDir.subpath(skillsIndex + 1, nameCount);
        return relative.toString().replace('\\', '/');
    }
}
