package io.github.hide212131.langchain4j.claude.skills.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.hide212131.langchain4j.claude.skills.runtime.VisibilityLog;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

@SuppressWarnings({ "PMD.JUnitTestContainsTooManyAsserts", "checkstyle:NoWhitespaceAfter" })
class SkillsCliAppTest {

    private static final Logger APP_LOGGER = Logger.getLogger(SkillsCliApp.class.getName());

    @SuppressWarnings("PMD.UnnecessaryConstructor")
    SkillsCliAppTest() {
        // default
    }

    @Test
    @DisplayName("失敗後に1回リトライして成功する")
    void retryOnceAndSucceed() {
        AtomicInteger counter = new AtomicInteger();
        Supplier<String> action = () -> {
            if (counter.getAndIncrement() == 0) {
                throw new IllegalStateException("first");
            }
            return "ok";
        };
        ByteArrayOutputStream logs = new ByteArrayOutputStream();
        VisibilityLog log = new VisibilityLog(newLogger(logs));

        String result = SkillsCliApp.executeWithRetry(action, log, true, "run-retry", "skill-x");

        assertThat(result).isEqualTo("ok");
        assertThat(logs.toString(StandardCharsets.UTF_8)).contains("再試行します");
    }

    @Test
    @DisplayName("リトライしても失敗したら例外を再送出する")
    void retryThenFail() {
        Supplier<String> action = () -> {
            throw new IllegalStateException("always");
        };
        VisibilityLog log = new VisibilityLog(newLogger(new ByteArrayOutputStream()));

        assertThatThrownBy(() -> SkillsCliApp.executeWithRetry(action, log, true, "run-retry", "skill-x"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("picocli 経由で SKILL.md を実行できる")
    void runCliWithPicocli() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        CommandLine cmd = new CommandLine(new SkillsCliApp());
        cmd.setOut(new PrintWriter(out, true, StandardCharsets.UTF_8));
        cmd.setErr(new PrintWriter(err, true, StandardCharsets.UTF_8));

        int exit = cmd.execute("--skill", "src/test/resources/skills/sample/SKILL.md", "--goal", "demo goal",
                "--skill-id", "sample-skill", "--visibility-level", "off");

        assertThat(exit).isZero();
        String stdout = out.toString(StandardCharsets.UTF_8);
        assertThat(stdout).contains("Plan:").contains("Skill: sample-skill");
        assertThat(err.toString(StandardCharsets.UTF_8)).isBlank();
    }

    @Test
    @DisplayName("Plan/Act/Reflect の e2e 実行でログと成果物を確認できる")
    void runE2eFlowWithLogging() {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        ByteArrayOutputStream logs = new ByteArrayOutputStream();

        Handler handler = attachHandler(logs);
        Level originalLevel = APP_LOGGER.getLevel();
        boolean originalUseParent = APP_LOGGER.getUseParentHandlers();
        APP_LOGGER.setUseParentHandlers(false);
        APP_LOGGER.setLevel(Level.ALL);
        handler.setLevel(Level.ALL);
        APP_LOGGER.addHandler(handler);

        try {
            // spotless:off
            // CHECKSTYLE:OFF: NoWhitespaceAfter
            // @formatter:off
            int exit = SkillsCliApp.run(
                    new String[] { "--skill", "src/test/resources/skills/e2e/SKILL.md", "--goal", "e2e goal",
                            "--visibility-level", "basic" },
                    new PrintStream(stdout, true, StandardCharsets.UTF_8),
                    new PrintStream(stderr, true, StandardCharsets.UTF_8));
            // @formatter:on
            // CHECKSTYLE:ON
            // spotless:on

            assertThat(exit).isZero();
            assertThat(stdout.toString(StandardCharsets.UTF_8)).contains("Plan:").contains("Act:").contains("Reflect:")
                    .contains("Goal: e2e goal").contains("Skill: e2e-skill");
            assertThat(logs.toString(StandardCharsets.UTF_8)).contains("phase=plan").contains("phase=act")
                    .contains("phase=reflect").contains("skill=e2e-skill").contains("run=");
            assertThat(stderr.toString(StandardCharsets.UTF_8)).isBlank();
        } finally {
            APP_LOGGER.removeHandler(handler);
            APP_LOGGER.setUseParentHandlers(originalUseParent);
            APP_LOGGER.setLevel(originalLevel);
            handler.close();
        }
    }

    @Test
    @DisplayName("OpenAI 指定で API キーがなければ設定エラーで終了する")
    void failWhenOpenAiApiKeyMissing() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        // spotless:off
        // CHECKSTYLE:OFF: NoWhitespaceAfter
        // @formatter:off
        int exit = SkillsCliApp.run(
                new String[] { "--skill", "src/test/resources/skills/sample/SKILL.md", "--llm-provider", "openai",
                        "--visibility-level", "off" },
                new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
        // @formatter:on
        // CHECKSTYLE:ON
        // spotless:on

        assertThat(exit).isEqualTo(4);
        assertThat(out.toString(StandardCharsets.UTF_8)).isBlank();
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("OPENAI_API_KEY");
    }

    private Logger newLogger(ByteArrayOutputStream out) {
        Logger log = Logger.getLogger("test-log-" + UUID.randomUUID());
        log.setUseParentHandlers(false);
        log.setLevel(Level.ALL);
        Handler handler = new StreamHandler(out, new SimpleFormatter()) {
            @Override
            public synchronized void publish(LogRecord record) {
                super.publish(record);
                flush();
            }
        };
        handler.setLevel(Level.ALL);
        log.addHandler(handler);
        return log;
    }

    private Handler attachHandler(ByteArrayOutputStream out) {
        Handler handler = new StreamHandler(out, new SimpleFormatter()) {
            @Override
            public synchronized void publish(LogRecord record) {
                super.publish(record);
                flush();
            }
        };
        handler.setLevel(Level.ALL);
        return handler;
    }
}
