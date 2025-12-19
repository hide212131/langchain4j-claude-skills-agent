package io.github.hide212131.langchain4j.claude.skills.runtime;

import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

@SuppressWarnings({ "PMD.UseObjectForClearerAPI", "PMD.GuardLogStatement", "PMD.AvoidDuplicateLiterals",
        "PMD.ConsecutiveLiteralAppends" })
public final class VisibilityLog {

    private static final int FORMAT_BUFFER_SIZE = 192;

    private final Logger logger;

    public VisibilityLog(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    public void info(boolean emitBasic, String runId, String skillId, String phase, String step, String message,
            String input, String output) {
        if (!emitBasic || !logger.isLoggable(Level.INFO)) {
            return;
        }
        logger.log(Level.INFO, format(Level.INFO, runId, skillId, phase, step, message, input, output, null));
    }

    @SuppressWarnings({ "checkstyle:ParameterNumber", "PMD.GuardLogStatement" })
    public void warn(String runId, String skillId, String phase, String step, String message, String input,
            String output, Throwable error) {
        if (!logger.isLoggable(Level.WARNING)) {
            return;
        }
        logger.log(Level.WARNING, format(Level.WARNING, runId, skillId, phase, step, message, input, output, error),
                error);
    }

    @SuppressWarnings({ "checkstyle:ParameterNumber", "PMD.GuardLogStatement" })
    public void error(String runId, String skillId, String phase, String step, String message, String input,
            String output, Throwable error) {
        if (!logger.isLoggable(Level.SEVERE)) {
            return;
        }
        logger.log(Level.SEVERE, format(Level.SEVERE, runId, skillId, phase, step, message, input, output, error),
                error);
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private String format(Level level, String runId, String skillId, String phase, String step, String message,
            String input, String output, Throwable error) {
        String header = String.format(Locale.ROOT, "[phase=%s][level=%s][skill=%s][run=%s][step=%s] %s",
                valueOrDash(phase), level.getName(), valueOrDash(skillId), valueOrDash(runId), valueOrDash(step),
                valueOrEmpty(message));
        StringBuilder sb = new StringBuilder(FORMAT_BUFFER_SIZE);
        sb.append(header);
        if (input != null && !input.isBlank()) {
            sb.append(" input=").append(input.trim());
        }
        if (output != null && !output.isBlank()) {
            sb.append(" output=").append(output.trim());
        }
        if (error != null) {
            sb.append(" error=").append(error.getClass().getSimpleName()).append(": ").append(error.getMessage());
        }
        return sb.toString();
    }

    private String valueOrDash(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.trim();
    }

    private String valueOrEmpty(String value) {
        if (value == null) {
            return "";
        }
        return value;
    }
}
