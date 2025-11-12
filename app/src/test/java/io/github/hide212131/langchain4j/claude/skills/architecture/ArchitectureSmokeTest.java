package io.github.hide212131.langchain4j.claude.skills.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.workflow.SequentialAgentService;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class ArchitectureSmokeTest {

    @TestFactory
    Stream<DynamicTest> moduleSkeletonShouldExposeExpectedTypes() {
        return expectedSkeletons().map(className -> DynamicTest.dynamicTest(
                "Expect skeleton for " + className,
                () -> assertThatCode(() -> Class.forName(className))
                        .as("Missing skeleton class %s", className)
                        .doesNotThrowAnyException()));
    }

    @Test
    void workflowFactoryMustReturnLangChain4jWorkflow() throws Exception {
        Class<?> factoryClass = Class.forName(
                "io.github.hide212131.langchain4j.claude.skills.runtime.workflow.support.WorkflowFactory");
        Object factory = factoryClass.getConstructor().newInstance();

        Method createMethod = factoryClass.getMethod("createWorkflow", java.util.function.Consumer.class);

        AtomicReference<UntypedAgent> customAgentAttempt = new AtomicReference<>(
                new CustomUntypedAgent("attempt"));

        UntypedAgent workflow = (UntypedAgent) createMethod.invoke(
                factory,
                (java.util.function.Consumer<SequentialAgentService<UntypedAgent>>) builder -> {
                    builder.name("demo-workflow");
                    builder.subAgents(AgenticServices.agentAction(() -> {}));
                    customAgentAttempt.set(new CustomUntypedAgent("custom"));
                });

        assertThat(workflow)
                .as("WorkflowFactory must use LangChain4j sequence builder")
                .isNotNull()
                .isNotSameAs(customAgentAttempt.get())
                .isInstanceOf(UntypedAgent.class);
    }

    @Test
    void agenticScopeBridgeShouldRejectMissingRequiredKeys() throws Exception {
        Class<?> bridgeClass = Class.forName(
                "io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.AgenticScopeBridge");
        Object bridge = bridgeClass.getConstructor().newInstance();

        Method readPlanState = bridgeClass.getMethod("readPlanGoal", AgenticScope.class);
        AgenticScope emptyScope = new MapBackedAgenticScope(Map.of());

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() -> invoke(readPlanState, bridge, emptyScope));
        assertThat(thrown).isInstanceOf(InvocationTargetException.class);
        assertThat(thrown.getCause())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("plan.goal");
    }

    private static Object invoke(Method method, Object target, Object... args) throws Exception {
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static Stream<String> expectedSkeletons() {
        return Stream.of(
                "io.github.hide212131.langchain4j.claude.skills.app.cli.SkillsCliApp",
                "io.github.hide212131.langchain4j.claude.skills.runtime.workflow.support.WorkflowFactory",
                "io.github.hide212131.langchain4j.claude.skills.runtime.provider.ProviderAdapter",
                "io.github.hide212131.langchain4j.claude.skills.runtime.skill.SkillIndex",
                "io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.AgenticScopeBridge",
                "io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.PlanState",
                "io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.PlanInputsState",
                "io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.PlanCandidateStepsState",
                "io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.PlanConstraintsState",
                "io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.PlanEvaluationCriteriaState",
                "io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.ActState",
                "io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.ActWindowState",
                "io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.ActCurrentStepState",
                "io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.ActInputBundleState",
                "io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.SharedBlackboardIndexState",
                "io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.ReflectReviewState",
                "io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.ReflectRetryAdviceState",
                "io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.ReflectFinalSummaryState",
                "io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.SharedContextSnapshotState",
                "io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.SharedGuardState",
                "io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.SharedMetricsState",
                "io.github.hide212131.langchain4j.claude.skills.runtime.context.ContextPackingService",
                "io.github.hide212131.langchain4j.claude.skills.runtime.guard.SkillInvocationGuard",
                "io.github.hide212131.langchain4j.claude.skills.runtime.human.HumanReviewAgentFactory",
                "io.github.hide212131.langchain4j.claude.skills.infra.logging.WorkflowLogger",
                "io.github.hide212131.langchain4j.claude.skills.infra.config.RuntimeConfig");
    }

    private static final class CustomUntypedAgent implements UntypedAgent {
        private final String name;

        private CustomUntypedAgent(String name) {
            this.name = name;
        }

        @Override
        public Object invoke(Map<String, Object> inputs) {
            return "custom-" + name;
        }

        @Override
        public dev.langchain4j.agentic.scope.ResultWithAgenticScope<String> invokeWithAgenticScope(
                Map<String, Object> inputs) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public dev.langchain4j.agentic.scope.AgenticScope getAgenticScope(Object memoryId) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public boolean evictAgenticScope(Object memoryId) {
            throw new UnsupportedOperationException("not implemented");
        }
    }

    private static final class MapBackedAgenticScope implements AgenticScope {
        private final Map<String, Object> delegate;

        private MapBackedAgenticScope(Map<String, Object> delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object memoryId() {
            return "test";
        }

        @Override
        public void writeState(String key, Object value) {
            throw new UnsupportedOperationException("read-only");
        }

        @Override
        public void writeStates(Map<String, Object> states) {
            throw new UnsupportedOperationException("read-only");
        }

        @Override
        public boolean hasState(String key) {
            return delegate.containsKey(key);
        }

        @Override
        public Object readState(String key) {
            return delegate.get(key);
        }

        @Override
        public <T> T readState(String key, T defaultValue) {
            return (T) delegate.getOrDefault(key, defaultValue);
        }

        @Override
        public Map<String, Object> state() {
            return delegate;
        }

        @Override
        public String contextAsConversation(String... keys) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public String contextAsConversation(Object... keys) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public java.util.List<dev.langchain4j.agentic.internal.AgentInvocation> agentInvocations(String skillId) {
            throw new UnsupportedOperationException("not implemented");
        }
    }
}
