package io.github.hide212131.langchain4j.claude.skills.runtime;

import io.github.hide212131.langchain4j.claude.skills.runtime.AgentFlow.AgentFlowResult;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.VisibilityMasking;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * スキル実行エージェントの標準実装。
 */
public final class DefaultSkillExecutionAgent implements SkillExecutionAgent {

    private final LlmConfigurationLoader configurationLoader;

    public DefaultSkillExecutionAgent() {
        this(new LlmConfigurationLoader());
    }

    DefaultSkillExecutionAgent(LlmConfigurationLoader configurationLoader) {
        this.configurationLoader = Objects.requireNonNull(configurationLoader, "configurationLoader は必須です");
    }

    @Override
    public SkillExecutionResult execute(SkillExecutionRequest request) {
        Objects.requireNonNull(request, "request は必須です");
        String runId = normalizeRunId(request.runId());
        SkillDocumentParser parser = new SkillDocumentParser(request.events(), VisibilityMasking.defaultRules());
        SkillDocument document;
        try {
            document = parser.parse(request.skillMdPath(), request.skillId(), runId);
        } catch (RuntimeException ex) {
            throw new SkillExecutionParseException("SKILL.md のパースに失敗しました: " + ex.getMessage(), ex);
        }

        LlmConfiguration configuration;
        try {
            configuration = configurationLoader.load(request.llmProvider());
        } catch (RuntimeException ex) {
            throw new SkillExecutionConfigurationException("LLM 設定の読み込みに失敗しました: " + ex.getMessage(), ex);
        }

        AgentFlowFactory factory = new AgentFlowFactory(configuration, request.executionBackend());
        AgentFlow flow = factory.create();
        boolean basic = request.visibilityLevel() == VisibilityLevel.BASIC;
        String goal = request.goal() == null ? "" : request.goal();
        String artifactsDir = request.artifactsDir() == null ? "" : request.artifactsDir().toString();
        String inputFilePath = request.inputFilePath() == null ? null : request.inputFilePath().toString();
        String outputDirectoryPath = request.outputDirectoryPath() == null ? null
                : request.outputDirectoryPath().toString();
        AgentFlowResult result = flow.run(document, goal, inputFilePath, outputDirectoryPath, request.log(), basic,
                runId, request.skillMdPath().toString(), artifactsDir, request.events());
        return new SkillExecutionResult(result, runId, document.id(), List.of());
    }

    private String normalizeRunId(String runId) {
        if (runId == null || runId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return runId;
    }
}
