package io.github.hide212131.langchain4j.claude.skills.runtime.visibility;

/** 例外や失敗イベントのペイロード。 */
public record ErrorPayload(String message, String errorType) implements VisibilityPayload {

    public ErrorPayload(String message, String errorType) {
        this.message = requireMessage(message);
        this.errorType = normalizeErrorType(errorType);
    }

    private static String requireMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message");
        }
        return message;
    }

    private static String normalizeErrorType(String raw) {
        if (raw == null || raw.isBlank()) {
            return "UnknownError";
        }
        return raw;
    }
}
