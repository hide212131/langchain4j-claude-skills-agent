package io.github.hide212131.langchain4j.claude.skills.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.workflow.SequentialAgentService;
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
    private static Stream<String> expectedSkeletons() {
        return Stream.of(
                "io.github.hide212131.langchain4j.claude.skills.app.cli.SkillsCliApp",
                "io.github.hide212131.langchain4j.claude.skills.runtime.workflow.support.WorkflowFactory",
                "io.github.hide212131.langchain4j.claude.skills.runtime.provider.ProviderAdapter",
                "io.github.hide212131.langchain4j.claude.skills.runtime.skill.SkillIndex",
                "io.github.hide212131.langchain4j.claude.skills.runtime.blackboard.AgenticScopeBridge",
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
}
