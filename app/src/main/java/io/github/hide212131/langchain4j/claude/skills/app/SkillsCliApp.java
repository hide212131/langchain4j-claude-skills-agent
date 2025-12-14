package io.github.hide212131.langchain4j.claude.skills.app;

import io.github.hide212131.langchain4j.claude.skills.runtime.DummyAgentFlow;
import io.github.hide212131.langchain4j.claude.skills.runtime.SkillDocument;
import io.github.hide212131.langchain4j.claude.skills.runtime.SkillDocumentParser;
import io.github.hide212131.langchain4j.claude.skills.runtime.VisibilityLevel;
import io.github.hide212131.langchain4j.claude.skills.runtime.VisibilityLog;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * 最小構成の CLI。SKILL.md をパースし、ダミー Plan/Act/Reflect フローを実行する。
 */
public final class SkillsCliApp {

    private static final int EXIT_USAGE = 1;
    private static final int EXIT_PARSE_ERROR = 2;
    private static final int EXIT_EXECUTION_ERROR = 3;

    public static void main(String[] args) {
        int exitCode = run(args, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        Arguments arguments = Arguments.parse(args);
        if (arguments == null) {
            printUsage(out);
            return EXIT_USAGE;
        }

        String runId = UUID.randomUUID().toString();
        boolean basic = arguments.visibilityLevel() == VisibilityLevel.BASIC;
        VisibilityLog log = new VisibilityLog(Logger.getLogger(SkillsCliApp.class.getName()));
        SkillDocumentParser parser = new SkillDocumentParser();

        log.info(basic, runId, "-", "parse", "parse.skill", "SKILL.md を読み込みます", "path=" + arguments.skillPath(), "");

        SkillDocument document;
        try {
            document = parser.parse(arguments.skillPath(), arguments.skillId().orElse(null));
        } catch (RuntimeException ex) {
            String fallbackSkillId = arguments.skillId().orElse("(不明)");
            log.error(runId, fallbackSkillId, "error", "parse", "SKILL.md のパースに失敗しました", "", "", ex);
            err.println("SKILL.md のパースに失敗しました: " + ex.getMessage());
            return EXIT_PARSE_ERROR;
        }

        DummyAgentFlow flow = new DummyAgentFlow();
        Supplier<DummyAgentFlow.Result> action =
                () -> flow.run(document, arguments.goal().orElse(""), log, basic, runId);

        try {
            DummyAgentFlow.Result result = executeWithRetry(action, log, basic, runId, document.id());
            out.println(result.formatted());
            return 0;
        } catch (RuntimeException ex) {
            log.error(runId, document.id(), "error", "run.failed", "エージェント実行が失敗しました", "", "", ex);
            err.println("エージェント実行が失敗しました: " + ex.getMessage());
            return EXIT_EXECUTION_ERROR;
        }
    }

    static <T> T executeWithRetry(Supplier<T> action, VisibilityLog log, boolean basic, String runId, String skillId) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(skillId, "skillId");
        try {
            return action.get();
        } catch (RuntimeException first) {
            log.warn(runId, skillId, "error", "run.retry", "エラーが発生したため再試行します", "", "", first);
            try {
                return action.get();
            } catch (RuntimeException second) {
                log.error(runId, skillId, "error", "run.failed", "再試行しても失敗しました", "", "", second);
                throw second;
            }
        }
    }

    private static void printUsage(PrintStream out) {
        out.println("使い方: skills --skill <path/to/SKILL.md> [--goal <text>] [--skill-id <id>] [--visibility-level basic|off]");
        out.println("SKILL.md を読み込んでダミー Plan/Act/Reflect を実行します。");
    }

    private record Arguments(Path skillPath, Optional<String> goal, Optional<String> skillId, VisibilityLevel visibilityLevel) {

        static Arguments parse(String[] args) {
            if (args == null) {
                return null;
            }
            Path skill = null;
            String goal = null;
            String skillId = null;
            VisibilityLevel visibilityLevel = VisibilityLevel.BASIC;
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
                    case "--visibility-level" -> {
                        if (i + 1 >= args.length) {
                            return null;
                        }
                        String raw = args[++i];
                        try {
                            visibilityLevel = VisibilityLevel.parse(raw);
                        } catch (IllegalArgumentException ex) {
                            return null;
                        }
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
            return new Arguments(skill, Optional.ofNullable(goal), Optional.ofNullable(skillId), visibilityLevel);
        }
    }
}
