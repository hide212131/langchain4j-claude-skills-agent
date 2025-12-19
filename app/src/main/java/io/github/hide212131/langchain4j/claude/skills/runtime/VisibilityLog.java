package io.github.hide212131.langchain4j.claude.skills.runtime;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class VisibilityLog {

  private final Logger logger;

  public VisibilityLog(Logger logger) {
    this.logger = Objects.requireNonNull(logger, "logger");
  }

  public void info(
      boolean emitBasic,
      String runId,
      String skillId,
      String phase,
      String step,
      String message,
      String input,
      String output) {
    if (!emitBasic) {
      return;
    }
    logger.log(
        Level.INFO, format(Level.INFO, runId, skillId, phase, step, message, input, output, null));
  }

  public void warn(
      String runId,
      String skillId,
      String phase,
      String step,
      String message,
      String input,
      String output,
      Throwable error) {
    logger.log(
        Level.WARNING,
        format(Level.WARNING, runId, skillId, phase, step, message, input, output, error),
        error);
  }

  public void error(
      String runId,
      String skillId,
      String phase,
      String step,
      String message,
      String input,
      String output,
      Throwable error) {
    logger.log(
        Level.SEVERE,
        format(Level.SEVERE, runId, skillId, phase, step, message, input, output, error),
        error);
  }

  private String format(
      Level level,
      String runId,
      String skillId,
      String phase,
      String step,
      String message,
      String input,
      String output,
      Throwable error) {
    StringBuilder sb = new StringBuilder();
    sb.append("[phase=").append(valueOrDash(phase)).append("]");
    sb.append("[level=").append(level.getName()).append("]");
    sb.append("[skill=").append(valueOrDash(skillId)).append("]");
    sb.append("[run=").append(valueOrDash(runId)).append("]");
    sb.append("[step=").append(valueOrDash(step)).append("] ");
    sb.append(valueOrEmpty(message));
    if (input != null && !input.isBlank()) {
      sb.append(" input=").append(input.trim());
    }
    if (output != null && !output.isBlank()) {
      sb.append(" output=").append(output.trim());
    }
    if (error != null) {
      sb.append(" error=")
          .append(error.getClass().getSimpleName())
          .append(": ")
          .append(error.getMessage());
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
