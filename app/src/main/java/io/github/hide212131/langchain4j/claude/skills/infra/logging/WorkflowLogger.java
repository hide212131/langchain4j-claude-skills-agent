package io.github.hide212131.langchain4j.claude.skills.infra.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thin wrapper around SLF4J that will expose structured logging helpers in later iterations.
 */
public final class WorkflowLogger {

    private final Logger logger = LoggerFactory.getLogger(WorkflowLogger.class);

    public void info(String message, Object... args) {
        logger.info(message, args);
    }

    public void debug(String message, Object... args) {
        logger.debug(message, args);
    }

    public void warn(String message, Object... args) {
        logger.warn(message, args);
    }
}
