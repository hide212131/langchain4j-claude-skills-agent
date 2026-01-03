package io.github.hide212131.langchain4j.claude.skills.runtime.execution;

import java.util.Objects;

public record ExecutionResult(String command, int exitCode, String stdout, String stderr, long elapsedMs) {

    public ExecutionResult {
        command = command == null ? "" : command;
        stdout = Objects.requireNonNull(stdout, "stdout");
        stderr = Objects.requireNonNull(stderr, "stderr");
    }
}
