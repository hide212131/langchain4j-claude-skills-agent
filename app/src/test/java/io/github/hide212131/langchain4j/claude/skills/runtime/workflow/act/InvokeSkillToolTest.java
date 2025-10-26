package io.github.hide212131.langchain4j.claude.skills.runtime.workflow.act;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.hide212131.langchain4j.claude.skills.infra.logging.WorkflowLogger;
import io.github.hide212131.langchain4j.claude.skills.runtime.skill.ScriptedSkillRuntimeChatModel;
import io.github.hide212131.langchain4j.claude.skills.runtime.skill.SkillIndex;
import io.github.hide212131.langchain4j.claude.skills.runtime.skill.SkillRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InvokeSkillToolTest {

    private final WorkflowLogger logger = new WorkflowLogger();
        private final ScriptedSkillRuntimeChatModel orchestrator = new ScriptedSkillRuntimeChatModel();

    @Test
    void specificationShouldExposeSkillIdAndInputsSchema() throws Exception {
        InvokeSkillTool tool = new InvokeSkillTool(new SkillRuntime(
                new SkillIndex(),
                Files.createTempDirectory("invoke-tool"),
                logger,
                orchestrator));

        ToolSpecification specification = tool.specification();

        assertThat(specification.name()).isEqualTo(InvokeSkillTool.TOOL_NAME);
        assertThat(specification.parameters().properties()).containsKeys("skillId", "inputs");
    }

    @Test
    void invokeShouldDelegateToSkillRuntime() throws Exception {
        Path tempDir = Files.createTempDirectory("invoke-tool-runtime");
        Path skillsRoot = Path.of("skills").toAbsolutePath().normalize();
        SkillIndex index = new SkillIndex(skillsRoot, Map.of(
                "document-skills/pptx",
                new SkillIndex.SkillMetadata(
                        "document-skills/pptx",
                        "PPTX Generator",
                        "Build slide decks",
                        List.of("pptx"),
                        List.of(),
                        skillsRoot.resolve("document-skills/pptx"))));
        SkillRuntime runtime = new SkillRuntime(index, tempDir, logger, orchestrator);
        InvokeSkillTool tool = new InvokeSkillTool(runtime);

        SkillRuntime.ExecutionResult result =
                tool.invoke("document-skills/pptx", Map.of("goal", "tool test"));

        assertThat(result.hasArtifact()).isTrue();
        assertThat(result.outputs()).containsKey("artifactPath");
        assertThat(result.outputs())
                .containsEntry(
                        "skillRoot",
                        skillsRoot.resolve("document-skills/pptx").toAbsolutePath().normalize().toString());
    }
}
