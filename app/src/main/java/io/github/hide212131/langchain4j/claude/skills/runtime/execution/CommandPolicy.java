package io.github.hide212131.langchain4j.claude.skills.runtime.execution;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

final class CommandPolicy {

    private static final List<Pattern> FORBIDDEN_PATTERNS = List.of(Pattern.compile("\\bpip(?:3)?\\s+install\\b"),
            Pattern.compile("\\bpython\\s+-m\\s+pip\\s+install\\b"), Pattern.compile("\\bnpm\\s+install\\b"),
            Pattern.compile("\\byarn\\s+add\\b"));

    private CommandPolicy() {
        throw new AssertionError("インスタンス化できません");
    }

    static void validate(String command) {
        Objects.requireNonNull(command, "command");
        for (Pattern pattern : FORBIDDEN_PATTERNS) {
            if (pattern.matcher(command).find()) {
                throw new IllegalArgumentException("実行時インストールは禁止されています: " + command);
            }
        }
    }
}
