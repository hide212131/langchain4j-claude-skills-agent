package io.github.hide212131.langchain4j.claude.skills.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@SuppressWarnings({ "PMD.JUnitTestContainsTooManyAsserts", "PMD.GuardLogStatement" })
class VisibilityLogTest {

    @SuppressWarnings("PMD.UnnecessaryConstructor")
    VisibilityLogTest() {
        // default
    }

    @Test
    @DisplayName("basic では phase/skill/run/step を含む INFO を出力する")
    void logInfoWhenBasic() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        VisibilityLog log = new VisibilityLog(newLogger(out));

        log.info(true, "run-1", "skill-1", "plan", "plan.prompt", "Plan を生成しました", "goal=demo", "Plan: demo");

        String text = out.toString(StandardCharsets.UTF_8);
        assertThat(text).contains("[phase=plan]").contains("[skill=skill-1]").contains("[run=run-1]")
                .contains("[step=plan.prompt]").contains("Plan を生成しました").contains("input=goal=demo")
                .contains("output=Plan: demo");
    }

    @Test
    @DisplayName("off では INFO を抑止し WARN/SEVERE は出力する")
    void suppressInfoWhenOff() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        VisibilityLog log = new VisibilityLog(newLogger(out));

        log.info(false, "run-2", "skill-2", "plan", "plan.prompt", "info", "", "");
        log.warn("run-2", "skill-2", "error", "retry", "warn", "", "", null);

        String text = out.toString(StandardCharsets.UTF_8);
        assertThat(text).doesNotContain("info");
        assertThat(text).contains("warn");
    }

    private Logger newLogger(ByteArrayOutputStream out) {
        Logger log = Logger.getLogger("visibility-log-" + UUID.randomUUID());
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
}
