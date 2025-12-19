package io.github.hide212131.langchain4j.claude.skills.app;

import io.github.hide212131.langchain4j.claude.skills.runtime.AgentFlow;
import io.github.hide212131.langchain4j.claude.skills.runtime.AgentFlow.AgentFlowResult;
import io.github.hide212131.langchain4j.claude.skills.runtime.AgentFlowFactory;
import io.github.hide212131.langchain4j.claude.skills.runtime.LlmConfiguration;
import io.github.hide212131.langchain4j.claude.skills.runtime.LlmConfigurationLoader;
import io.github.hide212131.langchain4j.claude.skills.runtime.LlmProvider;
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

/** 最小構成の CLI。SKILL.md をパースし、Plan/Act/Reflect フローを実行する。 */
// @formatter:off
@Command(name = "skills", description = "SKILL.md を読み込み Plan/Act/Reflect を実行します。", mixinStandardHelpOptions = true)
@SuppressWarnings({ "PMD.GuardLogStatement", "checkstyle:LineLength" })
public final class SkillsCliApp implements Callable<Integer> {

    private static final int EXIT_PARSE_ERROR = 2;
    private static final int EXIT_EXECUTION_ERROR = 3;
    private static final int EXIT_CONFIGURATION_ERROR = 4;
    private static final String LOG_TAG_ERROR = "error";

    @Spec
    private CommandSpec spec;

    // CHECKSTYLE:OFF: LineLength
    @Option(names = "--skill", required = true, paramLabel = "SKILL.md", description = "実行する SKILL.md のパス")
    private Path skillPath;

    @Option(names = "--goal", paramLabel = "TEXT", description = "エージェントに与えるゴール（任意）")
    private String goal;

    @Option(names = "--skill-id", paramLabel = "ID", description = "SKILL ID を明示的に指定（任意）")
    private String skillId;

    @SuppressWarnings("PMD.ImmutableField")
    @Option(names = "--visibility-level", paramLabel = "LEVEL", defaultValue = "basic", description = "可視化ログレベル (basic|off)", converter = VisibilityLevelConverter.class)
    private VisibilityLevel visibilityLevel = VisibilityLevel.BASIC;

    @Option(names = "--llm-provider", paramLabel = "PROVIDER", description = "LLM プロバイダ (mock|openai)。未指定時は環境変数/ .env を参照します。", converter = LlmProviderConverter.class)
    private LlmProvider llmProvider;
    // CHECKSTYLE:ON
    // @formatter:on

    @SuppressWarnings("PMD.UnnecessaryConstructor")
    public SkillsCliApp() {
        // for picocli
    }

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
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public Integer call() {
        String runId = UUID.randomUUID().toString();
        boolean basic = visibilityLevel == VisibilityLevel.BASIC;
        VisibilityLog log = new VisibilityLog(Logger.getLogger(SkillsCliApp.class.getName()));
        SkillDocumentParser parser = new SkillDocumentParser();

        log.info(basic, runId, "-", "parse", "parse.skill", "SKILL.md を読み込みます", "path=" + skillPath, "");

        SkillDocument document;
        try {
            document = parser.parse(skillPath, skillId);
        } catch (RuntimeException ex) {
            String fallbackSkillId = skillId == null ? "(不明)" : skillId;
            log.error(runId, fallbackSkillId, LOG_TAG_ERROR, "parse", "SKILL.md のパースに失敗しました", "", "", ex);
            spec.commandLine().getErr().println("SKILL.md のパースに失敗しました: " + ex.getMessage());
            spec.commandLine().getErr().flush();
            return EXIT_PARSE_ERROR;
        }

        LlmConfiguration configuration;
        try {
            configuration = new LlmConfigurationLoader().load(llmProvider);
        } catch (RuntimeException ex) {
            log.error(runId, document.id(), LOG_TAG_ERROR, "config.load", "LLM 設定の読み込みに失敗しました", "", "", ex);
            spec.commandLine().getErr().println("LLM 設定の読み込みに失敗しました: " + ex.getMessage());
            spec.commandLine().getErr().flush();
            return EXIT_CONFIGURATION_ERROR;
        }

        AgentFlowFactory factory = new AgentFlowFactory(configuration);
        AgentFlow flow = factory.create();
        Supplier<AgentFlowResult> action = () -> flow.run(document, goal == null ? "" : goal, log, basic, runId);

        try {
            AgentFlowResult result = executeWithRetry(action, log, basic, runId, document.id());
            spec.commandLine().getOut().println(result.formatted());
            spec.commandLine().getOut().flush();
            return CommandLine.ExitCode.OK;
        } catch (RuntimeException ex) {
            log.error(runId, document.id(), LOG_TAG_ERROR, "run.failed", "エージェント実行が失敗しました", "", "", ex);
            spec.commandLine().getErr().println("エージェント実行が失敗しました: " + ex.getMessage());
            spec.commandLine().getErr().flush();
            return EXIT_EXECUTION_ERROR;
        }
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    static <T> T executeWithRetry(Supplier<T> action, VisibilityLog log, boolean basic, String runId, String skillId) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(skillId, "skillId");
        try {
            return action.get();
        } catch (RuntimeException first) {
            log.warn(runId, skillId, LOG_TAG_ERROR, "run.retry", "エラーが発生したため再試行します", "", "", first);
            try {
                return action.get();
            } catch (RuntimeException second) {
                second.addSuppressed(first);
                log.error(runId, skillId, LOG_TAG_ERROR, "run.failed", "再試行しても失敗しました", "", "", second);
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

    private static final class LlmProviderConverter implements ITypeConverter<LlmProvider> {

        @Override
        public LlmProvider convert(String value) {
            return LlmProvider.from(value);
        }
    }
}
