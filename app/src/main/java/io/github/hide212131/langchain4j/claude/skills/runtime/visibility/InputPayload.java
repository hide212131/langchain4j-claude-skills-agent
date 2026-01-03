package io.github.hide212131.langchain4j.claude.skills.runtime.visibility;

/** 入力情報の可視化ペイロード。 */
public record InputPayload(String goal, String inputFilePath) implements SkillPayload {

    public InputPayload(String goal, String inputFilePath) {
        this.goal = goal == null ? "" : goal.trim();
        this.inputFilePath = inputFilePath == null ? "" : inputFilePath.trim();
    }
}
