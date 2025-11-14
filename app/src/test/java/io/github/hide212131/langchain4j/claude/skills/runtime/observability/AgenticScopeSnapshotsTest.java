package io.github.hide212131.langchain4j.claude.skills.runtime.observability;

import dev.langchain4j.agentic.internal.AgentInvocation;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgenticScopeSnapshotsTest {

    @Test
    void snapshotIsEmptyWhenScopeHasNoState() {
        TestScope scope = new TestScope();

        assertThat(AgenticScopeSnapshots.snapshot(scope)).isEmpty();
    }

    @Test
    void snapshotSerialisesNestedCollections() {
        TestScope scope = new TestScope();
        scope.writeState("goal", "Create deck");
        scope.writeState("act.outputs", List.of(Map.of("artifactPath", "/tmp/out.pptx")));

        String snapshot = AgenticScopeSnapshots.snapshot(scope).orElseThrow();

        assertThat(snapshot)
                .contains("\"goal\":\"Create deck\"")
                .contains("\"artifactPath\":\"/tmp/out.pptx\"");
    }

    @Test
    void snapshotTruncatesOverlyLongValues() {
        TestScope scope = new TestScope();
        scope.writeState("plan.prompt", "x".repeat(3000));

        String snapshot = AgenticScopeSnapshots.snapshot(scope).orElseThrow();

        assertThat(snapshot).contains("...(truncated)");
    }

    private static final class TestScope implements AgenticScope {

        private final Map<String, Object> state = new LinkedHashMap<>();

        @Override
        public Object memoryId() {
            return "test";
        }

        @Override
        public void writeState(String key, Object value) {
            state.put(key, value);
        }

        @Override
        public void writeStates(Map<String, Object> newState) {
            state.putAll(newState);
        }

        @Override
        public boolean hasState(String key) {
            return state.containsKey(key);
        }

        @Override
        public Object readState(String key) {
            return state.get(key);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T readState(String key, T defaultValue) {
            return (T) state.getOrDefault(key, defaultValue);
        }

        @Override
        public Map<String, Object> state() {
            return Map.copyOf(state);
        }

        @Override
        public String contextAsConversation(String... agentNames) {
            return "";
        }

        @Override
        public String contextAsConversation(Object... agents) {
            return "";
        }

        @Override
        public List<AgentInvocation> agentInvocations(String agentName) {
            return List.of();
        }
    }
}
