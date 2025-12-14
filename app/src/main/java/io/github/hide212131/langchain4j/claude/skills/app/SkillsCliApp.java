package io.github.hide212131.langchain4j.claude.skills.app;

import io.github.hide212131.langchain4j.claude.skills.runtime.DummyAgentFlow;
import io.github.hide212131.langchain4j.claude.skills.runtime.SkillDocument;
import io.github.hide212131.langchain4j.claude.skills.runtime.SkillDocumentParser;
import java.nio.file.Path;
import java.util.Optional;

/**
 * 最小構成の CLI。SKILL.md をパースし、ダミー Plan/Act/Reflect フローを実行する。
 */
public final class SkillsCliApp {

    public static void main(String[] args) {
        Arguments arguments = Arguments.parse(args);
        if (arguments == null) {
            printUsage();
            System.exit(1);
            return;
        }
        SkillDocumentParser parser = new SkillDocumentParser();
        SkillDocument document = parser.parse(arguments.skillPath(), arguments.skillId().orElse(null));
        DummyAgentFlow flow = new DummyAgentFlow();
        DummyAgentFlow.Result result = flow.run(document, arguments.goal().orElse(""));
        System.out.println(result.formatted());
    }

    private static void printUsage() {
        System.out.println("Usage: skills --skill <path/to/SKILL.md> [--goal <text>] [--skill-id <id>]");
    }

    private record Arguments(Path skillPath, Optional<String> goal, Optional<String> skillId) {

        static Arguments parse(String[] args) {
            if (args == null) {
                return null;
            }
            Path skill = null;
            String goal = null;
            String skillId = null;
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--skill" -> {
                        if (i + 1 >= args.length) {
                            return null;
                        }
                        skill = Path.of(args[++i]);
                    }
                    case "--goal" -> {
                        if (i + 1 >= args.length) {
                            return null;
                        }
                        goal = args[++i];
                    }
                    case "--skill-id" -> {
                        if (i + 1 >= args.length) {
                            return null;
                        }
                        skillId = args[++i];
                    }
                    default -> {
                        // 不明な引数
                        return null;
                    }
                }
            }
            if (skill == null) {
                return null;
            }
            return new Arguments(skill, Optional.ofNullable(goal), Optional.ofNullable(skillId));
        }
    }
}
