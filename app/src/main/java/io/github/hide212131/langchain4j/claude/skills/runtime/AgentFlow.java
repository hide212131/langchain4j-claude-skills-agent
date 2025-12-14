package io.github.hide212131.langchain4j.claude.skills.runtime;

/**
 * Plan/Act/Reflect を実行するフローの共通インターフェース。
 */
public interface AgentFlow {

    AgentFlowResult run(SkillDocument document, String goal, VisibilityLog log, boolean basicLog, String runId);

    record AgentFlowResult(String planLog, String actLog, String reflectLog, String artifactContent) {

        public String formatted() {
            return planLog
                    + System.lineSeparator()
                    + actLog
                    + System.lineSeparator()
                    + reflectLog
                    + System.lineSeparator()
                    + "---"
                    + System.lineSeparator()
                    + artifactContent;
        }
    }
}
