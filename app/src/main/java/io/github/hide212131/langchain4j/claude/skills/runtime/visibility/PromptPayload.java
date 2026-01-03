package io.github.hide212131.langchain4j.claude.skills.runtime.visibility;

/** LLM プロンプト/応答の可視化ペイロード。 */
public record PromptPayload(String prompt, String response, String model, String role, TokenUsage usage)
        implements SkillPayload {

    public PromptPayload(String prompt, String response, String model, String role, TokenUsage usage) {
        this.prompt = requirePrompt(prompt);
        this.response = response;
        this.model = model;
        this.role = normalizeRole(role);
        this.usage = usage;
    }

    private String requirePrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt");
        }
        return prompt;
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "assistant";
        }
        return role.trim();
    }
}
