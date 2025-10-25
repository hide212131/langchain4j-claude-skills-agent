package io.github.hide212131.langchain4j.claude.skills.runtime.workflow.act;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.internal.AgentInvocation;
import dev.langchain4j.agentic.scope.AgenticScope;
import io.github.hide212131.langchain4j.claude.skills.infra.logging.WorkflowLogger;
import io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.ActState;
import io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.BlackboardStore;
import io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.SharedBlackboardIndexState;
import io.github.hide212131.langchain4j.claude.skills.runtime.guard.SkillInvocationGuard;
import io.github.hide212131.langchain4j.claude.skills.runtime.skill.SkillIndex;
import io.github.hide212131.langchain4j.claude.skills.runtime.skill.SkillRuntime;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.plan.DefaultPlanner;
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

    @Test
    void invokeShouldExecuteSkillsInPlannedOrderAndPopulateBlackboard() throws Exception {
        Path tempDir = Files.createTempDirectory("default-invoker-test");
        SkillIndex index = new SkillIndex(Map.of(
                "brand-guidelines",
                new SkillIndex.SkillMetadata(
                        "brand-guidelines", "Brand Guidelines", "Summarise brand rules", List.of("brand"), List.of()),
                "document-skills/pptx",
                new SkillIndex.SkillMetadata(
                        "document-skills/pptx",
                        "PPTX Generator",
                        "Build slide decks",
                        List.of("pptx"),
                        List.of())));
        BlackboardStore blackboardStore = new BlackboardStore();
        SkillRuntime runtime = new SkillRuntime(index, tempDir, logger);
        InvokeSkillTool tool = new InvokeSkillTool(runtime);
        DefaultInvoker invoker =
                new DefaultInvoker(tool, new SkillInvocationGuard(), blackboardStore, logger);

        DefaultPlanner planner = new DefaultPlanner(index);
        DefaultPlanner.PlanResult plan = planner.plan("Create a brand aligned deck");
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
                .isInstanceOf(List.class)
                .asList()
                .containsExactly("brand-guidelines", "document-skills/pptx");
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
