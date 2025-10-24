package io.github.hide212131.langchain4j.claude.skills.runtime.workflow.support;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.workflow.SequentialAgentService;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Factory responsible for constructing LangChain4j workflows via {@link AgenticServices}.
 * <p>
 * The factory delegates to {@code AgenticServices.sequenceBuilder()} and therefore callers can only
 * customise a workflow by configuring the provided builder.
 */
public final class WorkflowFactory {

    public UntypedAgent createWorkflow(Consumer<SequentialAgentService<UntypedAgent>> pipelineConfigurer) {
        Objects.requireNonNull(pipelineConfigurer, "pipelineConfigurer");
        SequentialAgentService<UntypedAgent> sequenceBuilder = AgenticServices.sequenceBuilder();
        pipelineConfigurer.accept(sequenceBuilder);
        UntypedAgent workflow = sequenceBuilder.build();
        if (workflow == null) {
            sequenceBuilder.subAgents(AgenticServices.agentAction(() -> {
                // no-op stub stage
            }));
            workflow = sequenceBuilder.build();
        }
        return Objects.requireNonNull(workflow, "AgenticServices returned null workflow");
    }
}
