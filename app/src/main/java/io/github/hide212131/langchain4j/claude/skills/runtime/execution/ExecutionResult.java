package io.github.hide212131.langchain4j.claude.skills.runtime.execution;

import java.util.Objects;

public record ExecutionResult(String command, int exitCode, String stdout, String stderr, long elapsedMs) {

    public ExecutionResult {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(stdout, "stdout");
        Objects.requireNonNull(stderr, "stderr");
    }
}
