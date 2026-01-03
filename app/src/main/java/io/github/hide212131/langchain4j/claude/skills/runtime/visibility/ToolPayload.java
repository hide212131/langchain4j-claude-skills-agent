package io.github.hide212131.langchain4j.claude.skills.runtime.visibility;

/** ツール呼び出しの入出力を記録するペイロード。 */
public record ToolPayload(String toolName, String input, String output, String errorType, String errorMessage)
        implements SkillPayload {
}
