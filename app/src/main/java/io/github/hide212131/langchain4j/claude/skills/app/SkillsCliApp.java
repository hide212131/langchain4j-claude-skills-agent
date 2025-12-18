package io.github.hide212131.langchain4j.claude.skills.app;

import io.github.hide212131.langchain4j.claude.skills.runtime.DummyAgentFlow;
import io.github.hide212131.langchain4j.claude.skills.runtime.SkillDocument;
import io.github.hide212131.langchain4j.claude.skills.runtime.SkillDocumentParser;
import io.github.hide212131.langchain4j.claude.skills.runtime.VisibilityLevel;
import io.github.hide212131.langchain4j.claude.skills.runtime.VisibilityLog;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.logging.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/** 最小構成の CLI。SKILL.md をパースし、ダミー Plan/Act/Reflect フローを実行する。 */
@Command(
    name = "skills",
    description = "SKILL.md を読み込みダミー Plan/Act/Reflect を実行します。",
    mixinStandardHelpOptions = true)
public final class SkillsCliApp implements Callable<Integer> {

  private static final int EXIT_PARSE_ERROR = 2;
  private static final int EXIT_EXECUTION_ERROR = 3;

  @Spec CommandSpec spec;

  @Option(
      names = "--skill",
      required = true,
      paramLabel = "SKILL.md",
      description = "実行する SKILL.md のパス")
  Path skillPath;

  @Option(names = "--goal", paramLabel = "TEXT", description = "エージェントに与えるゴール（任意）")
  String goal;

  @Option(names = "--skill-id", paramLabel = "ID", description = "SKILL ID を明示的に指定（任意）")
  String skillId;

  @Option(
      names = "--visibility-level",
      paramLabel = "LEVEL",
      defaultValue = "basic",
      description = "可視化ログレベル (basic|off)",
      converter = VisibilityLevelConverter.class)
  VisibilityLevel visibilityLevel = VisibilityLevel.BASIC;

  public static void main(String[] args) {
    int exitCode = new CommandLine(new SkillsCliApp()).execute(args);
    if (exitCode != 0) {
      System.exit(exitCode);
    }
  }

  static int run(String[] args, PrintStream out, PrintStream err) {
    CommandLine cmd = new CommandLine(new SkillsCliApp());
    cmd.setOut(new PrintWriter(out, true, StandardCharsets.UTF_8));
    cmd.setErr(new PrintWriter(err, true, StandardCharsets.UTF_8));
    return cmd.execute(args);
  }

  @Override
  public Integer call() {
    String runId = UUID.randomUUID().toString();
    boolean basic = visibilityLevel == VisibilityLevel.BASIC;
    VisibilityLog log = new VisibilityLog(Logger.getLogger(SkillsCliApp.class.getName()));
    SkillDocumentParser parser = new SkillDocumentParser();

    log.info(
        basic, runId, "-", "parse", "parse.skill", "SKILL.md を読み込みます", "path=" + skillPath, "");

    SkillDocument document;
    try {
      document = parser.parse(skillPath, skillId);
    } catch (RuntimeException ex) {
      String fallbackSkillId = skillId == null ? "(不明)" : skillId;
      log.error(runId, fallbackSkillId, "error", "parse", "SKILL.md のパースに失敗しました", "", "", ex);
      spec.commandLine().getErr().println("SKILL.md のパースに失敗しました: " + ex.getMessage());
      spec.commandLine().getErr().flush();
      return EXIT_PARSE_ERROR;
    }

    DummyAgentFlow flow = new DummyAgentFlow();
    Supplier<DummyAgentFlow.Result> action =
        () -> flow.run(document, goal == null ? "" : goal, log, basic, runId);

    try {
      DummyAgentFlow.Result result = executeWithRetry(action, log, basic, runId, document.id());
      spec.commandLine().getOut().println(result.formatted());
      spec.commandLine().getOut().flush();
      return CommandLine.ExitCode.OK;
    } catch (RuntimeException ex) {
      log.error(runId, document.id(), "error", "run.failed", "エージェント実行が失敗しました", "", "", ex);
      spec.commandLine().getErr().println("エージェント実行が失敗しました: " + ex.getMessage());
      spec.commandLine().getErr().flush();
      return EXIT_EXECUTION_ERROR;
    }
  }

  static <T> T executeWithRetry(
      Supplier<T> action, VisibilityLog log, boolean basic, String runId, String skillId) {
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

  private static final class VisibilityLevelConverter implements ITypeConverter<VisibilityLevel> {

    @Override
    public VisibilityLevel convert(String value) {
      return VisibilityLevel.parse(value);
    }
  }
}
