package io.github.hide212131.langchain4j.claude.skills.runtime.visibility;

import java.util.Locale;

/** 出力情報の可視化ペイロード。 */
public record OutputPayload(String outputType, String taskId, String sourcePath, String destinationPath, String content)
        implements SkillPayload {

    public OutputPayload(String outputType, String taskId, String sourcePath, String destinationPath, String content) {
        this.outputType = normalizeType(outputType);
        this.taskId = taskId == null ? "" : taskId.trim();
        this.sourcePath = sourcePath == null ? "" : sourcePath.trim();
        this.destinationPath = destinationPath == null ? "" : destinationPath.trim();
        this.content = content == null ? "" : content;
    }

    private static String normalizeType(String outputType) {
        if (outputType == null || outputType.isBlank()) {
            throw new IllegalArgumentException("outputType は download/stdout/llm のいずれかにしてください: " + outputType);
        }
        String normalized = outputType.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
        case "download", "stdout", "llm" -> normalized;
        default -> throw new IllegalArgumentException("outputType は download/stdout/llm のいずれかにしてください: " + outputType);
        };
    }
}
