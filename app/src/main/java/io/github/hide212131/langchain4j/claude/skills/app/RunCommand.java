package io.github.hide212131.langchain4j.claude.skills.app;

import io.github.hide212131.langchain4j.claude.skills.runtime.DefaultSkillExecutionAgent;
import io.github.hide212131.langchain4j.claude.skills.runtime.LlmProvider;
import io.github.hide212131.langchain4j.claude.skills.runtime.SkillExecutionAgent;
import io.github.hide212131.langchain4j.claude.skills.runtime.SkillExecutionConfigurationException;
import io.github.hide212131.langchain4j.claude.skills.runtime.SkillExecutionParseException;
import io.github.hide212131.langchain4j.claude.skills.runtime.SkillExecutionRequest;
import io.github.hide212131.langchain4j.claude.skills.runtime.SkillExecutionResult;
import io.github.hide212131.langchain4j.claude.skills.runtime.VisibilityLevel;
import io.github.hide212131.langchain4j.claude.skills.runtime.VisibilityLog;
import io.github.hide212131.langchain4j.claude.skills.runtime.execution.ExecutionBackend;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.ErrorPayload;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.ExporterType;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.MetricsPayload;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.ObservabilityConfiguration;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.ObservabilityConfigurationLoader;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.VisibilityEvent;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.VisibilityEventMetadata;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.VisibilityEventPublisher;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.VisibilityEventType;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.VisibilityPublisherFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * スキル実行コマンド (run).
 */
@Command(name = "run", description = "SKILL.md を読み込み Plan/Act/Reflect を実行します。", mixinStandardHelpOptions = true)
@SuppressWarnings({ "PMD.GuardLogStatement", "PMD.ExcessiveImports", "checkstyle:LineLength" })
public final class RunCommand implements Callable<Integer> {

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

    @Option(names = "--input-file", paramLabel = "PATH", description = "入力ファイルのパス（任意）")
    private Path inputFilePath;

    @Option(names = "--output-dir", paramLabel = "DIR", description = "出力ファイルの保存先ディレクトリ（任意）")
    private Path outputDirectoryPath;

    @Option(names = "--skill-id", paramLabel = "ID", description = "SKILL ID を明示的に指定（任意）")
    private String skillId;

    @SuppressWarnings("PMD.ImmutableField")
    @Option(names = "--visibility-level", paramLabel = "LEVEL", defaultValue = "basic", description = "可視化ログレベル (basic|off)", converter = VisibilityLevelConverter.class)
    private VisibilityLevel visibilityLevel = VisibilityLevel.BASIC;

    @Option(names = "--llm-provider", paramLabel = "PROVIDER", description = "LLM プロバイダ (mock|openai)。未指定時は環境変数/ .env を参照します。", converter = LlmProviderConverter.class)
    private LlmProvider llmProvider;

    @Option(names = "--exporter", paramLabel = "TYPE", description = "可視化エクスポーター (none|otlp)", converter = ExporterTypeConverter.class)
    private ExporterType exporter;

    @Option(names = "--otlp-endpoint", paramLabel = "URL", description = "OTLP エンドポイント（未指定時は OTEL_EXPORTER_OTLP_ENDPOINT）")
    private String otlpEndpoint;

    @Option(names = "--otlp-headers", paramLabel = "KEY=VAL,...", description = "OTLP 追加ヘッダ（未指定時は OTEL_EXPORTER_OTLP_HEADERS）")
    private String otlpHeaders;

    @Option(names = "--execution-backend", paramLabel = "BACKEND", description = "実行バックエンド (docker|acads)。未指定時は SKILL_EXECUTION_BACKEND を参照します。", converter = ExecutionBackendConverter.class)
    private ExecutionBackend executionBackend;

    @Option(names = "--artifacts-dir", paramLabel = "DIR", description = "成果物の保存先ディレクトリ（未指定時は SKILL_ARTIFACTS_DIR を参照します。）")
    private Path artifactsDir;

    @Option(names = "--log-file", paramLabel = "FILE", description = "ログの保存先ファイル（未指定時は SKILL_LOG_FILE を参照します。）")
    private Path logFile;
    // CHECKSTYLE:ON

    @SuppressWarnings("PMD.UnnecessaryConstructor")
    public RunCommand() {
        // for picocli
    }

    @Override
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public Integer call() {
        String runId = UUID.randomUUID().toString();
        boolean basic = visibilityLevel == VisibilityLevel.BASIC;
        Logger logger = Logger.getLogger(RunCommand.class.getName());
        FileHandler fileHandler = null;
        try {
            try {
                fileHandler = configureLogFile(logger);
            } catch (IllegalStateException ex) {
                spec.commandLine().getErr().println(ex.getMessage());
                spec.commandLine().getErr().flush();
                return EXIT_CONFIGURATION_ERROR;
            }
            VisibilityLog log = new VisibilityLog(logger);
            ObservabilityConfiguration observability = new ObservabilityConfigurationLoader().load(exporter,
                    otlpEndpoint, otlpHeaders);
            VisibilityEventPublisher publisher = VisibilityPublisherFactory.create(observability);
            try (VisibilityEventPublisher closeablePublisher = publisher) {
                log.info(basic, runId, "-", "parse", "parse.skill", "SKILL.md を読み込みます", "path=" + skillPath, "");

                SkillExecutionAgent agent = new DefaultSkillExecutionAgent();
                SkillExecutionRequest request = new SkillExecutionRequest(skillPath, goal, inputFilePath,
                        outputDirectoryPath, skillId, runId, resolveExecutionBackend(), llmProvider,
                        resolveArtifactsDir(), visibilityLevel, closeablePublisher, log);
                Supplier<SkillExecutionResult> action = () -> agent.execute(request);
                String fallbackSkillId = skillId == null ? "(不明)" : skillId;
                try {
                    SkillExecutionResult result = executeWithRetry(action, log, basic, runId, fallbackSkillId,
                            closeablePublisher);
                    spec.commandLine().getOut().println(result.flowResult().formatted());
                    spec.commandLine().getOut().flush();
                    return CommandLine.ExitCode.OK;
                } catch (SkillExecutionParseException ex) {
                    log.error(runId, fallbackSkillId, LOG_TAG_ERROR, "parse", "SKILL.md のパースに失敗しました", "", "", ex);
                    spec.commandLine().getErr().println(ex.getMessage());
                    spec.commandLine().getErr().flush();
                    return EXIT_PARSE_ERROR;
                } catch (SkillExecutionConfigurationException ex) {
                    log.error(runId, fallbackSkillId, LOG_TAG_ERROR, "config.load", "LLM 設定の読み込みに失敗しました", "", "", ex);
                    spec.commandLine().getErr().println(ex.getMessage());
                    spec.commandLine().getErr().flush();
                    return EXIT_CONFIGURATION_ERROR;
                } catch (RuntimeException ex) {
                    log.error(runId, fallbackSkillId, LOG_TAG_ERROR, "run.failed", "エージェント実行が失敗しました", "", "", ex);
                    spec.commandLine().getErr().println("エージェント実行が失敗しました: " + ex.getMessage());
                    spec.commandLine().getErr().flush();
                    return EXIT_EXECUTION_ERROR;
                }
            }
        } finally {
            if (fileHandler != null) {
                fileHandler.close();
            }
        }
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    static <T> T executeWithRetry(Supplier<T> action, VisibilityLog log, boolean basic, String runId, String skillId,
            VisibilityEventPublisher publisher) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(skillId, "skillId");
        Objects.requireNonNull(publisher, "publisher");
        try {
            return action.get();
        } catch (RuntimeException first) {
            if (first instanceof SkillExecutionParseException
                    || first instanceof SkillExecutionConfigurationException) {
                throw first;
            }
            log.warn(runId, skillId, LOG_TAG_ERROR, "run.retry", "エラーが発生したため再試行します", "", "", first);
            publishRetryEvent(publisher, runId, skillId, first, 1);
            try {
                return action.get();
            } catch (RuntimeException second) {
                second.addSuppressed(first);
                log.error(runId, skillId, LOG_TAG_ERROR, "run.failed", "再試行しても失敗しました", "", "", second);
                publishRetryEvent(publisher, runId, skillId, second, 2);
                throw second;
            }
        }
    }

    private static void publishRetryEvent(VisibilityEventPublisher publisher, String runId, String skillId,
            Throwable error, int retryCount) {
        VisibilityEventMetadata errorMetadata = new VisibilityEventMetadata(runId, skillId, "error", "run.retry", null);
        publisher.publish(new VisibilityEvent(VisibilityEventType.ERROR, errorMetadata,
                new ErrorPayload("エージェント実行でエラーが発生しました: " + error.getMessage(), error.getClass().getSimpleName())));
        VisibilityEventMetadata metricsMetadata = new VisibilityEventMetadata(runId, skillId, "metrics", "run.retry",
                null);
        publisher.publish(new VisibilityEvent(VisibilityEventType.METRICS, metricsMetadata,
                new MetricsPayload(null, null, null, retryCount)));
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

    private static final class ExporterTypeConverter implements ITypeConverter<ExporterType> {

        @Override
        public ExporterType convert(String value) {
            return ExporterType.parse(value);
        }
    }

    private ExecutionBackend resolveExecutionBackend() {
        if (executionBackend != null) {
            return executionBackend;
        }
        return ExecutionBackend.parse(System.getenv("SKILL_EXECUTION_BACKEND"));
    }

    private Path resolveArtifactsDir() {
        if (artifactsDir != null) {
            return artifactsDir;
        }
        String env = System.getenv("SKILL_ARTIFACTS_DIR");
        return env == null || env.isBlank() ? null : Path.of(env);
    }

    private FileHandler configureLogFile(Logger logger) {
        Path resolved = resolveLogFile();
        if (resolved == null) {
            return null;
        }
        try {
            Path parent = resolved.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            FileHandler handler = new FileHandler(resolved.toString(), true);
            handler.setFormatter(new SimpleFormatter());
            logger.addHandler(handler);
            return handler;
        } catch (IOException ex) {
            throw new IllegalStateException("ログファイルの初期化に失敗しました: " + resolved, ex);
        }
    }

    private Path resolveLogFile() {
        if (logFile != null) {
            return logFile;
        }
        String env = System.getenv("SKILL_LOG_FILE");
        return env == null || env.isBlank() ? null : Path.of(env);
    }

    private static final class ExecutionBackendConverter implements ITypeConverter<ExecutionBackend> {

        @Override
        public ExecutionBackend convert(String value) {
            return ExecutionBackend.parse(value);
        }
    }
}
