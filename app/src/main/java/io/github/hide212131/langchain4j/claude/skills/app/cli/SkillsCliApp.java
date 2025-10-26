package io.github.hide212131.langchain4j.claude.skills.app.cli;

import io.github.hide212131.langchain4j.claude.skills.runtime.provider.LangChain4jLlmClient;
import io.github.hide212131.langchain4j.claude.skills.runtime.skill.SkillIndex;
import io.github.hide212131.langchain4j.claude.skills.runtime.skill.SkillIndexLoader;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.AgentService;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.AgentService.StageVisit;
import io.github.hide212131.langchain4j.claude.skills.runtime.workflow.support.WorkflowFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
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
    Path skillsDir;

    @Option(
        names = "--debug-skill-ids",
        split = ",",
        description =
            "Comma-separated list of skill IDs to execute in order (bypasses the planner)")
    List<String> debugSkillIds;

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
            Path primarySkillsDir = toAbsolute(skillsDir);
            Path resolvedSkillsDir = primarySkillsDir;
            if (!Files.exists(primarySkillsDir) && !skillsDir.isAbsolute()) {
                Path ancestorMatch = findInAncestors(skillsDir);
                if (ancestorMatch != null) {
                    resolvedSkillsDir = ancestorMatch;
                    commandSpec
                            .commandLine()
                            .getOut()
                            .println("Info: using skills directory " + resolvedSkillsDir);
                }
            }
            if (!Files.exists(resolvedSkillsDir)) {
                commandSpec
                        .commandLine()
                        .getErr()
                        .println("Warning: skills directory not found at " + resolvedSkillsDir);
            }
            SkillIndexLoader.LoadResult loadResult = loader.load(resolvedSkillsDir);
            loadResult.warnings().forEach(warning -> commandSpec.commandLine().getOut().println("Warning: " + warning));
            LangChain4jLlmClient client = dryRun
                    ? LangChain4jLlmClient.fake()
                    : LangChain4jLlmClient.forOpenAi(System::getenv);
            AgentService agentService = agentServiceFactory.create(dryRun, loadResult.index(), client);
        List<String> forcedSkillIds = normaliseSkillIds(debugSkillIds);
        if (!forcedSkillIds.isEmpty()) {
        commandSpec
            .commandLine()
            .getOut()
            .println(
                "Debug: forcing skill sequence " + String.join(" -> ", forcedSkillIds));
        List<String> missingSkillIds = forcedSkillIds.stream()
            .filter(id -> !loadResult.index().skills().containsKey(id))
            .toList();
        if (!missingSkillIds.isEmpty()) {
            commandSpec
                .commandLine()
                .getErr()
                .println(
                    "Warning: skill IDs not found in index: "
                        + String.join(", ", missingSkillIds));
        }
        }
        AgentService.ExecutionResult result = agentService.run(
            new AgentService.AgentRunRequest(goal, dryRun, forcedSkillIds));
            if (!result.visitedStages().isEmpty()) {
                LinkedHashMap<Integer, ArrayList<String>> stagesPerAttempt = new LinkedHashMap<>();
                for (StageVisit visit : result.visitedStages()) {
                    stagesPerAttempt
                            .computeIfAbsent(visit.attempt(), ignored -> new ArrayList<>())
                            .add(visit.stage());
                }
                stagesPerAttempt.forEach((attempt, stages) -> commandSpec
                        .commandLine()
                        .getOut()
                        .println(
                                "Attempt " + attempt + ": " + String.join(" -> ", stages)));
            }
            if (result.planResult() != null) {
                if (result.plan() != null && !result.plan().steps().isEmpty()) {
                    String skillSequence = result.plan().steps().stream()
                            .map(step -> step.skillId())
                            .collect(Collectors.joining(" -> "));
                    commandSpec
                            .commandLine()
                            .getOut()
                            .println("Plan skills: " + skillSequence);
                }
                String assistantDraft = result.planResult().content();
                if (assistantDraft != null && !assistantDraft.isBlank()) {
                    commandSpec
                            .commandLine()
                            .getOut()
                            .println("Assistant draft: " + assistantDraft);
                }
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
            if (result.actResult() != null) {
                if (!result.actResult().invokedSkills().isEmpty()) {
                    commandSpec
                            .commandLine()
                            .getOut()
                            .println(
                                    "Invoked skills: "
                                            + String.join(", ", result.actResult().invokedSkills()));
                }
                if (!result.actResult().outputs().isEmpty()) {
                    commandSpec
                            .commandLine()
                            .getOut()
                            .println("Act outputs:");
                    result.actResult().outputs().forEach((skillId, output) -> commandSpec
                            .commandLine()
                            .getOut()
                            .println("  - " + skillId + ": " + summariseOutput(output)));
                }
                if (result.actResult().hasArtifact()) {
                    commandSpec
                            .commandLine()
                            .getOut()
                            .println("Artifact: " + result.actResult().finalArtifact());
                }
            }
            if (result.evaluation() != null) {
                commandSpec
                        .commandLine()
                        .getOut()
                        .println("Reflect summary: " + result.evaluation().finalSummary());
                if (result.evaluation().retryAdvice() != null
                        && !"none".equalsIgnoreCase(result.evaluation().retryAdvice())) {
                    commandSpec
                            .commandLine()
                            .getOut()
                            .println("Retry advice: " + result.evaluation().retryAdvice());
                }
            }
            commandSpec.commandLine().getOut().flush();
            return 0;
        }

        private String summariseOutput(Object value) {
            if (value == null) {
                return "(null)";
            }
            if (value instanceof java.util.Map<?, ?> map) {
                return summariseMap(map);
            }
            if (value instanceof CharSequence sequence) {
                return abbreviate(sequence.toString());
            }
            return abbreviate(String.valueOf(value));
        }

        private String summariseMap(java.util.Map<?, ?> map) {
            String body = map.entrySet().stream()
                    .limit(4)
                    .map(entry -> entry.getKey() + "=" + abbreviate(String.valueOf(entry.getValue())))
                    .collect(Collectors.joining(", "));
            if (map.size() > 4) {
                body = body + ", …";
            }
            return "{" + body + "}";
        }

        private String abbreviate(String value) {
            String singleLine = value.replaceAll("\n", " ").trim();
            if (singleLine.length() <= 120) {
                return singleLine;
            }
            return singleLine.substring(0, 117) + "…";
        }

        private Path toAbsolute(Path path) {
            return path.isAbsolute()
                    ? path.normalize()
                    : Path.of("").toAbsolutePath().normalize().resolve(path).normalize();
        }

        private Path findInAncestors(Path relativePath) {
            Path current = Path.of("").toAbsolutePath().normalize();
            while (current.getParent() != null) {
                current = current.getParent();
                Path candidate = current.resolve(relativePath).normalize();
                if (Files.exists(candidate)) {
                    return candidate;
                }
            }
            return null;
        }

        private List<String> normaliseSkillIds(List<String> rawValues) {
            if (rawValues == null || rawValues.isEmpty()) {
                return List.of();
            }
            LinkedHashSet<String> ordered = new LinkedHashSet<>();
            for (String value : rawValues) {
                if (value == null) {
                    continue;
                }
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    ordered.add(trimmed);
                }
            }
            return ordered.isEmpty() ? List.of() : List.copyOf(ordered);
        }
    }

    @FunctionalInterface
    interface AgentServiceFactory {
        AgentService create(boolean dryRun, SkillIndex index, LangChain4jLlmClient client);
    }
}
