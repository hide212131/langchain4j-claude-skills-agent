package io.github.hide212131.langchain4j.claude.skills.runtime;

import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.SkillEventPublisher;

/** Plan/Act/Reflect を実行するフローの共通インターフェース。 */
public interface AgentFlow {

    AgentFlowResult run(SkillDocument document, String goal, SkillLog log, boolean basicLog, String runId,
            String skillPath, String artifactsDir);

    default AgentFlowResult run(SkillDocument document, String goal, SkillLog log, boolean basicLog, String runId,
            String skillPath, String artifactsDir, SkillEventPublisher events) {
        return run(document, goal, log, basicLog, runId, skillPath, artifactsDir);
    }

    default AgentFlowResult run(SkillDocument document, String goal, String inputFilePath, SkillLog log,
            boolean basicLog, String runId, String skillPath, String artifactsDir) {
        return run(document, goal, log, basicLog, runId, skillPath, artifactsDir);
    }

    default AgentFlowResult run(SkillDocument document, String goal, String inputFilePath, SkillLog log,
            boolean basicLog, String runId, String skillPath, String artifactsDir, SkillEventPublisher events) {
        return run(document, goal, inputFilePath, log, basicLog, runId, skillPath, artifactsDir);
    }

    default AgentFlowResult run(SkillDocument document, String goal, String inputFilePath, String outputDirectoryPath,
            SkillLog log, boolean basicLog, String runId, String skillPath, String artifactsDir) {
        return run(document, goal, inputFilePath, log, basicLog, runId, skillPath, artifactsDir);
    }

    default AgentFlowResult run(SkillDocument document, String goal, String inputFilePath, String outputDirectoryPath,
            SkillLog log, boolean basicLog, String runId, String skillPath, String artifactsDir,
            SkillEventPublisher events) {
        return run(document, goal, inputFilePath, outputDirectoryPath, log, basicLog, runId, skillPath, artifactsDir);
    }

    record AgentFlowResult(String planLog, String actLog, String reflectLog, String artifactContent) {

        public String formatted() {
            return planLog + System.lineSeparator() + actLog + System.lineSeparator() + reflectLog
                    + System.lineSeparator() + "---" + System.lineSeparator() + artifactContent;
        }
    }
}
