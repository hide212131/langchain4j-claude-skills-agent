package io.github.hide212131.langchain4j.claude.skills.runtime.workflow.act;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.internal.AgentInvocation;
import dev.langchain4j.agentic.scope.AgenticScope;
import io.github.hide212131.langchain4j.claude.skills.infra.logging.WorkflowLogger;
import io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.ActInputBundleState;
import io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.ActState;
import io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.BlackboardStore;
import io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.SharedBlackboardIndexState;
import io.github.hide212131.langchain4j.claude.skills.runtime.guard.SkillInvocationGuard;
import io.github.hide212131.langchain4j.claude.skills.runtime.skill.DryRunSkillRuntimeOrchestrator;
import io.github.hide212131.langchain4j.claude.skills.runtime.skill.SkillIndex;
import io.github.hide212131.langchain4j.claude.skills.runtime.skill.SkillRuntime;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.plan.PlanModels;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class DefaultInvokerTest {

    private final WorkflowLogger logger = new WorkflowLogger();
    private final DryRunSkillRuntimeOrchestrator orchestrator = new DryRunSkillRuntimeOrchestrator();

    @Test
    void invokeShouldExecuteSkillsInPlannedOrderAndPopulateBlackboard() throws Exception {
        Path tempDir = Files.createTempDirectory("default-invoker-test");
        Path skillsRoot = Path.of("skills").toAbsolutePath().normalize();
        SkillIndex index = new SkillIndex(skillsRoot, Map.of(
                "brand-guidelines",
                new SkillIndex.SkillMetadata(
                        "brand-guidelines",
                        "Brand Guidelines",
                        "Summarise brand rules",
                        List.of("brand"),
                        List.of(),
                        skillsRoot.resolve("brand-guidelines")),
                "document-skills/pptx",
                new SkillIndex.SkillMetadata(
                        "document-skills/pptx",
                        "PPTX Generator",
                        "Build slide decks",
                        List.of("pptx"),
                        List.of(),
                        skillsRoot.resolve("document-skills/pptx"))));
        BlackboardStore blackboardStore = new BlackboardStore();
        SkillRuntime runtime = new SkillRuntime(index, tempDir, logger, orchestrator);
        InvokeSkillTool tool = new InvokeSkillTool(runtime);
        DefaultInvoker invoker =
                new DefaultInvoker(tool, new SkillInvocationGuard(), blackboardStore, logger);

        List<PlanModels.PlanStep> steps = List.of(
                new PlanModels.PlanStep(
                        "brand-guidelines",
                        "Brand Guidelines",
                        "Summarise brand rules",
                        List.of("brand"),
                        "Extract the latest brand positioning guidance",
                        skillsRoot.resolve("brand-guidelines")),
                new PlanModels.PlanStep(
                        "document-skills/pptx",
                        "PPTX Generator",
                        "Build slide decks",
                        List.of("pptx"),
                        "Apply the brand rules to craft a slide deck",
                        skillsRoot.resolve("document-skills/pptx")));
        PlanModels.PlanResult plan = new PlanModels.PlanResult(
                "Create a brand aligned deck",
                steps,
                "brand-guidelines: Brand Guidelines — Summarise brand rules Goal: Extract the latest brand positioning guidance (keywords: brand)\n"
                        + "document-skills/pptx: PPTX Generator — Build slide decks Goal: Apply the brand rules to craft a slide deck (keywords: pptx)");
        RecordingAgenticScope scope = new RecordingAgenticScope();

        DefaultInvoker.ActResult result = invoker.invoke(scope, plan);

        assertThat(result.invokedSkills())
                .containsExactly("brand-guidelines", "document-skills/pptx");
        assertThat(result.hasArtifact()).isTrue();
        assertThat(result.finalArtifact()).isEqualTo(tempDir.resolve("deck.pptx"));
        assertThat(invoker.blackboardStore().contains(ActState.outputKey("brand-guidelines"))).isTrue();
        assertThat(invoker.blackboardStore().contains(ActState.outputKey("document-skills/pptx"))).isTrue();
    assertThat(scope.hasState(SharedBlackboardIndexState.KEY)).isTrue();
    Object indexState = scope.readState(SharedBlackboardIndexState.KEY);
    assertThat(indexState)
        .isInstanceOf(SharedBlackboardIndexState.class);
    SharedBlackboardIndexState typedIndex = (SharedBlackboardIndexState) indexState;
    assertThat(typedIndex.invokedSkillIds())
        .containsExactly("brand-guidelines", "document-skills/pptx");

    Object lastInputBundle = scope.readState(ActInputBundleState.KEY);
    assertThat(lastInputBundle)
        .isInstanceOf(ActInputBundleState.class);
    ActInputBundleState bundleState = (ActInputBundleState) lastInputBundle;
    assertThat(bundleState.skillRoot())
        .isEqualTo(skillsRoot.resolve("document-skills/pptx").toAbsolutePath().normalize());
    }

    private static final class RecordingAgenticScope implements AgenticScope {

        private final Map<String, Object> state = new HashMap<>();

        @Override
        public Object memoryId() {
            return "test-scope";
        }

        @Override
        public void writeState(String key, Object value) {
            state.put(key, value);
        }

        @Override
        public void writeStates(Map<String, Object> values) {
            state.putAll(values);
        }

        @Override
        public boolean hasState(String key) {
            return state.containsKey(key);
        }

        @Override
        public Object readState(String key) {
            return state.get(key);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T readState(String key, T defaultValue) {
            return (T) state.getOrDefault(key, defaultValue);
        }

        @Override
        public Map<String, Object> state() {
            return Map.copyOf(state);
        }

        @Override
        public String contextAsConversation(String... lines) {
            return String.join("\n", lines);
        }

        @Override
        public String contextAsConversation(Object... objects) {
            return Arrays.stream(objects).map(Object::toString).collect(Collectors.joining("\n"));
        }

        @Override
        public List<AgentInvocation> agentInvocations(String agentId) {
            return new ArrayList<>();
        }
    }
}
