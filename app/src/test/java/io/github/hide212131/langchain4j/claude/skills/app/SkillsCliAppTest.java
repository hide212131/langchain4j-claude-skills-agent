package io.github.hide212131.langchain4j.claude.skills.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.hide212131.langchain4j.claude.skills.runtime.VisibilityLog;
import java.io.ByteArrayOutputStream;
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

class SkillsCliAppTest {

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
}
