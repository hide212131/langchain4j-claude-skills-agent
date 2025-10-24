package io.github.hide212131.langchain4j.claude.skills.app.cli;

import io.github.hide212131.langchain4j.claude.skills.runtime.provider.LangChain4jLlmClient;
import io.github.hide212131.langchain4j.claude.skills.runtime.skill.SkillIndex;
import io.github.hide212131.langchain4j.claude.skills.runtime.skill.SkillIndexLoader;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.AgentService;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.support.WorkflowFactory;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

/**
 * Entry point that wires PicoCLI with the agent runtime.
 */
@Command(name = "skills", mixinStandardHelpOptions = true, description = "Run the Plan→Act→Reflect workflow")
public final class SkillsCliApp implements Runnable {

    public static void main(String[] args) {
        int exitCode = commandLineInstance().execute(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static CommandLine commandLineInstance() {
        CommandLine cmd = new CommandLine(new SkillsCliApp());
        SkillIndexLoader loader = new SkillIndexLoader();
        AgentServiceFactory factory = (dryRun, index, client) -> AgentService.withDefaults(new WorkflowFactory(), client, index);
        cmd.addSubcommand("run", new RunCommand(factory, loader));
        return cmd;
    }

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    @Command(name = "run", description = "Execute the workflow for a given goal")
    static final class RunCommand implements Callable<Integer> {

        @Option(names = "--goal", required = true, description = "High-level objective to accomplish")
        String goal;

        @Option(names = "--dry-run", description = "Use fake LLM to run without external API calls")
        boolean dryRun;

        @Option(names = "--skills-dir", description = "Path to skills directory", defaultValue = "skills")
        java.nio.file.Path skillsDir;

        private final AgentServiceFactory agentServiceFactory;
        private final SkillIndexLoader loader;

        @Spec
        CommandSpec commandSpec;

        RunCommand(AgentServiceFactory agentServiceFactory, SkillIndexLoader loader) {
            this.agentServiceFactory = agentServiceFactory;
            this.loader = loader;
        }

        @Override
        public Integer call() {
            SkillIndexLoader.LoadResult loadResult = loader.load(skillsDir);
            loadResult.warnings().forEach(warning -> commandSpec.commandLine().getOut().println("Warning: " + warning));
            LangChain4jLlmClient client = dryRun
                    ? LangChain4jLlmClient.fake()
                    : LangChain4jLlmClient.forOpenAi(System::getenv);
            AgentService agentService = agentServiceFactory.create(dryRun, loadResult.index(), client);
            AgentService.ExecutionResult result = agentService.run(new AgentService.AgentRunRequest(goal, dryRun));
            result.visitedStages().forEach(stage -> commandSpec.commandLine().getOut().println("Stage: " + stage));
            if (result.planResult() != null) {
                commandSpec.commandLine().getOut().println("Plan: " + result.plan().systemPromptSummary());
                commandSpec.commandLine().getOut().println("Assistant: " + result.planResult().content());
            }
            if (result.metrics() != null) {
                commandSpec
                        .commandLine()
                        .getOut()
                        .printf(
                                "Tokens in/out/total: %d/%d/%d (calls=%d, durationMs=%d)%n",
                                result.metrics().totalInputTokens(),
                                result.metrics().totalOutputTokens(),
                                result.metrics().totalTokenCount(),
                                result.metrics().callCount(),
                                result.metrics().totalDurationMs());
            }
            commandSpec.commandLine().getOut().flush();
            return 0;
        }
    }

    @FunctionalInterface
    interface AgentServiceFactory {
        AgentService create(boolean dryRun, SkillIndex index, LangChain4jLlmClient client);
    }
}
