package io.github.hide212131.langchain4j.claude.skills.runtime.execution;

import java.util.List;
import java.util.StringJoiner;

final class ExecutionReportFormatter {

    private ExecutionReportFormatter() {
        throw new AssertionError("インスタンス化できません");
    }

    static String format(List<ExecutionResult> results, List<String> artifacts) {
        StringJoiner joiner = new StringJoiner(System.lineSeparator());
        joiner.add("実行結果:");
        for (ExecutionResult result : results) {
            joiner.add("- cmd: " + result.command());
            joiner.add("  exit: " + result.exitCode());
            joiner.add("  elapsedMs: " + result.elapsedMs());
            joiner.add("  stdout: " + preview(result.stdout()));
            joiner.add("  stderr: " + preview(result.stderr()));
        }
        joiner.add("成果物:");
        if (artifacts.isEmpty()) {
            joiner.add("- (なし)");
        } else {
            for (String artifact : artifacts) {
                joiner.add("- " + artifact);
            }
        }
        return joiner.toString();
    }

    private static String preview(String text) {
        if (text == null || text.isBlank()) {
            return "(empty)";
        }
        String trimmed = text.trim();
        int limit = Math.min(trimmed.length(), 500);
        return trimmed.substring(0, limit);
    }
}
