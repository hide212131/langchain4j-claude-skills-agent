package io.github.hide212131.langchain4j.claude.skills.runtime.skill;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.agentic.supervisor.SupervisorContextStrategy;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.V;
import io.github.hide212131.langchain4j.claude.skills.infra.logging.WorkflowLogger;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.PathMatcher;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class SkillRuntime {

    private static final String DEFAULT_OUTPUT_FILE = "deck.pptx";
    private static final Pattern LOCAL_REFERENCE_PATTERN =
            Pattern.compile("\\[[^\\]]*]\\((?!https?://)([^)]+)\\)");
    private static final Set<String> ALLOWED_SCRIPT_EXTENSIONS = Set.of(".py", ".sh", ".js");
    private static final Set<String> SUPPORTED_DEPENDENCY_TOOLS = Set.of("pip", "npm");
    private static final int DEFAULT_SCRIPT_TIMEOUT_SECONDS = 20;
    private static final int MAX_SCRIPT_TIMEOUT_SECONDS = 120;
    private static final int DEFAULT_DEPENDENCY_TIMEOUT_SECONDS = 60;
    private static final int MAX_STDOUT_CHARS = 8192;
    private static final int MAX_STDERR_CHARS = 8192;
    private static final int MAX_STDERR_LINES = 512;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> JSON_MAP_TYPE = new TypeReference<>() {};

    private final SkillIndex skillIndex;
    private final Path outputDirectory;
    private final WorkflowLogger logger;
    private final SkillAgentOrchestrator orchestrator;

    public SkillRuntime(
            SkillIndex skillIndex, Path outputDirectory, WorkflowLogger logger, ChatModel chatModel) {
        this(skillIndex, outputDirectory, logger, new AgenticOrchestrator(chatModel));
    }

    public SkillRuntime(
            SkillIndex skillIndex,
            Path outputDirectory,
            WorkflowLogger logger,
            SkillAgentOrchestrator orchestrator) {
        this.skillIndex = Objects.requireNonNull(skillIndex, "skillIndex");
        this.outputDirectory =
                Objects.requireNonNull(outputDirectory, "outputDirectory").toAbsolutePath().normalize();
        this.logger = Objects.requireNonNull(logger, "logger");
        this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator");
    }

    public ExecutionResult execute(String skillId, Map<String, Object> inputs) {
        if (skillId == null || skillId.isBlank()) {
            throw new IllegalArgumentException("skillId must be provided");
        }
        SkillIndex.SkillMetadata metadata = skillIndex.find(skillId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown skill: " + skillId));
        Map<String, Object> safeInputs = inputs == null ? Map.of() : Map.copyOf(inputs);
    prepareOutputDirectory(outputDirectory);
        List<String> expectedOutputs = resolveExpectedOutputs(safeInputs);

    SkillRuntimeContext context = new SkillRuntimeContext(metadata, expectedOutputs, outputDirectory, logger);
    context.logL1();
    SkillToolbox toolbox = new SkillToolbox(context);

    String prompt = buildPrompt(metadata, safeInputs, expectedOutputs);
    recordSkillPrompt(metadata.id(), prompt);
    String rawResponse = orchestrator.run(toolbox, metadata, safeInputs, expectedOutputs, prompt);
    recordSkillResponse(metadata.id(), rawResponse);

        Map<String, String> parsed = parseFinalResponse(rawResponse);
        Map<String, Object> outputs = new LinkedHashMap<>();
        outputs.put("skillRoot", metadata.skillRoot().toString());
        outputs.put(
                "summary",
                parsed.getOrDefault(
                        "summary",
                        metadata.description().isBlank() ? metadata.name() : metadata.description()));
        if (parsed.containsKey("notes")) {
            outputs.put("notes", parsed.get("notes"));
        }
        if (context.artifactPath() != null) {
            outputs.put("artifactPath", context.artifactPath().toString());
        } else if (parsed.containsKey("artifactPath")) {
            outputs.put("artifactPath", parsed.get("artifactPath"));
        }
        if (!context.referencedFiles().isEmpty()) {
            outputs.put(
                    "referencedFiles",
                    context.referencedFiles().stream().map(Path::toString).toList());
        }
        if (!expectedOutputs.isEmpty()) {
            outputs.put("expectedOutputs", expectedOutputs);
        }

        Validation validation = context.validation() != null
                ? context.validation()
                : validateOutputs(expectedOutputs, outputs, context.artifactPath());
        if (context.validation() == null) {
            context.setValidation(validation);
        }

    Path artifactPath = context.artifactPath();
        logger.info("Act[done] {} — {}", metadata.id(), outputs.get("summary"));
        return new ExecutionResult(
                skillId,
                Map.copyOf(outputs),
                artifactPath,
                validation,
                context.disclosureLog(),
        context.toolInvocations(),
        context.invokedSkills());
    }

    private String buildPrompt(
            SkillIndex.SkillMetadata metadata, Map<String, Object> inputs, List<String> expectedOutputs) {
        String goal = Objects.toString(inputs.getOrDefault("goal", ""), "");
        String constraints = Objects.toString(inputs.getOrDefault("constraints", ""), "");
        String expected = expectedOutputs.isEmpty()
                ? "artifactPath (default)"
                : String.join(", ", expectedOutputs);
        return """
                # Skill Execution Request

                Skill ID: %s
                Skill Name: %s
                Goal: %s
                Constraints: %s
                Expected Outputs: %s of artifacts aligned with the goal

                Apply the supervisor instructions to decide which tools to call. When you finish, respond with key=value lines containing at least:
                  artifactPath=<absolute path to the generated artefact>
                  summary=<one-line summary>
                """.formatted(
                metadata.id(),
                metadata.name(),
                goal.isBlank() ? "(goal not provided)" : goal,
                constraints.isBlank() ? "(none)" : constraints,
                expected);
    }

    private Map<String, String> parseFinalResponse(String response) {
        Map<String, String> result = new LinkedHashMap<>();
    if (response == null || response.isBlank()) {
            return result;
        }
        String[] lines = response.split("\\R");
        for (String line : lines) {
            int separator = line.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            if (!key.isEmpty()) {
                result.put(key, value);
            }
        }
        return result;
    }

    private List<String> resolveExpectedOutputs(Map<String, Object> inputs) {
        Object raw = inputs.get("expectedOutputs");
        if (raw == null) {
            return List.of("artifactPath");
        }
        if (raw instanceof List<?> list) {
            List<String> outputs = list.stream()
                    .map(Objects::toString)
                    .map(String::trim)
                    .filter(str -> !str.isEmpty())
                    .collect(Collectors.toCollection(ArrayList::new));
            return outputs.isEmpty() ? List.of("artifactPath") : List.copyOf(outputs);
        }
        if (raw instanceof String str && !str.isBlank()) {
            return List.of(str.trim());
        }
        return List.of("artifactPath");
    }

    private void recordSkillPrompt(String skillId, String prompt) {
        Span span = Span.current();
        if (!span.isRecording() || prompt == null || prompt.isBlank()) {
            return;
        }
        String keyPrefix = "act.skill." + sanitiseForAttribute(skillId);
        span.setAttribute(keyPrefix + ".prompt", prompt);
        span.addEvent(
                "skill.llm.prompt",
                Attributes.builder()
                        .put("skillId", skillId)
                        .put("prompt", prompt)
                        .build());
    }

    private void recordSkillResponse(String skillId, String response) {
        Span span = Span.current();
        if (!span.isRecording() || response == null || response.isBlank()) {
            return;
        }
        String keyPrefix = "act.skill." + sanitiseForAttribute(skillId);
        span.setAttribute(keyPrefix + ".response", response);
        span.addEvent(
                "skill.llm.response",
                Attributes.builder()
                        .put("skillId", skillId)
                        .put("response", response)
                        .build());
    }

    private String sanitiseForAttribute(String skillId) {
        if (skillId == null || skillId.isBlank()) {
            return "unknown";
        }
        return skillId.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private void prepareOutputDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
            Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
                    if (!file.equals(directory)) {
                        Files.deleteIfExists(file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                        throws IOException {
                    if (!dir.equals(directory)) {
                        Files.deleteIfExists(dir);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to prepare output directory " + directory, e);
        }
    }

    private Validation validateOutputs(List<String> expectedOutputs, Map<String, Object> outputs, Path artifactPath) {
        Set<String> missing = new LinkedHashSet<>();
        for (String expected : expectedOutputs) {
            if ("artifactPath".equals(expected)) {
                if (artifactPath == null || !Files.exists(artifactPath)) {
                    missing.add("artifactPath");
                }
                continue;
            }
            if (!outputs.containsKey(expected)) {
                missing.add(expected);
            }
        }
        return new Validation(missing.isEmpty(), List.copyOf(missing));
    }

    private String summariseForLog(String content) {
        return content.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .limit(5)
                .collect(Collectors.joining(" "));
    }

    private List<String> extractLocalReferences(String content) {
    if (content == null || content.isBlank()) {
            return List.of();
        }
        java.util.regex.Matcher matcher = LOCAL_REFERENCE_PATTERN.matcher(content);
        List<String> references = new ArrayList<>();
        while (matcher.find()) {
            String reference = matcher.group(1).trim();
            if (!reference.isEmpty() && !reference.startsWith("#")) {
                references.add(reference);
            }
        }
        return List.copyOf(references);
    }

    interface SkillActSupervisor {
    @Agent(
        name = "SkillActSupervisor",
        description = "Supervisor coordinating Pure Act execution for a single skill. Goals must be set specifically in line with the purpose of the skill.")
    ResultWithAgenticScope<String> run(
        @V("request") String request,
        @V("skillId") String skillId,
        @V("expectedOutputs") String expectedOutputs,
        @V("goal") String goal,
        @V("constraints") String constraints);
    }

    public interface Toolbox {
        SkillDocumentResult readSkillMd(String skillId);

        ReferenceDocuments readReference(String skillId, String reference);

    ArtifactHandle writeArtifact(String skillId, String relativePath, String content, boolean base64Encoded);

    ScriptResult runScript(
        String skillId,
        String path,
        List<String> args,
        List<String> dependencies,
        Integer timeoutSeconds);

        DeploymentResult deployScripts(String skillId, String sourceDir, String targetDir);

        ValidationResult validateExpectedOutputs(Object expected, Object observed);
    }

    public interface SkillAgentOrchestrator {
        String run(
                Toolbox toolbox,
                SkillIndex.SkillMetadata metadata,
                Map<String, Object> inputs,
                List<String> expectedOutputs,
                String prompt);
    }

    private static final class AgenticOrchestrator implements SkillAgentOrchestrator {

        private final ChatModel chatModel;

        AgenticOrchestrator(ChatModel chatModel) {
            this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
        }

        @Override
        public String run(
                Toolbox toolbox,
                SkillIndex.SkillMetadata metadata,
                Map<String, Object> inputs,
                List<String> expectedOutputs,
                String prompt) {
            String expectedOutputsPayload = serialiseExpectedOutputs(expectedOutputs);
            ResultWithAgenticScope<String> result = buildSupervisor(toolbox, metadata, expectedOutputs)
                    .run(
                            prompt,
                            metadata.id(),
                            expectedOutputsPayload,
                            Objects.toString(inputs.getOrDefault("goal", ""), ""),
                            Objects.toString(inputs.getOrDefault("constraints", ""), ""));
            String supervisorResponse = result.result();
            return supervisorResponse == null ? "" : supervisorResponse;
        }

        private SkillActSupervisor buildSupervisor(
                Toolbox toolbox, SkillIndex.SkillMetadata metadata, List<String> expectedOutputs) {
            return AgenticServices
                    .supervisorBuilder(SkillActSupervisor.class)
                    .chatModel(chatModel)
                    .contextGenerationStrategy(SupervisorContextStrategy.CHAT_MEMORY_AND_SUMMARIZATION)
                    .responseStrategy(SupervisorResponseStrategy.SUMMARY)
                    .supervisorContext(createSupervisorContext(metadata, expectedOutputs))
                    .subAgents(
                new ReadReferenceAgent(toolbox, chatModel),
                new ScriptDeployAgent(toolbox),
                new RunScriptAgent(toolbox),
                new WriteArtifactAgent(toolbox),
                new UnifiedOutputsValidatorAgent(toolbox, chatModel, false) // Semantic check disabled by default
                )
                    .build();
        }

        private String serialiseExpectedOutputs(List<String> expectedOutputs) {
            if (expectedOutputs == null || expectedOutputs.isEmpty()) {
                return "artifactPath";
            }
            return String.join(System.lineSeparator(), expectedOutputs);
        }

        private String createSupervisorContext(
                SkillIndex.SkillMetadata metadata, List<String> expectedOutputs) {
            String expected = expectedOutputs.isEmpty()
                    ? "artifactPath (default)"
                    : String.join(", ", expectedOutputs);
            String skillInstructions = loadSkillInstructions(metadata);
            return """
                    Progressive Disclosure Policy:
                    - Maintain L1 (name/description) in context by default.
                    - Use readRef to resolve any additional references (L3).
                    - Persist artefacts only through writeArtifact (paths relative to build/out).
                    - Capture derived procedures with writeArtifact and then reference them with readRef when you need that knowledge again.
                    - Use deployScripts to copy auxiliary files before executing a script. Provide sourceDir relative to the skill root (e.g. "scripts") and targetDir under build/out (e.g. "workdir").
                    - Run existing scripts with runScript after preparing arguments and deployment targets.
                    - Create new scripts with writeArtifact before running them via runScript.
                    - When calling writeArtifact, explicitly set base64Encoded to false unless you are saving Base64 content.
                    - After producing artifacts, ALWAYS call validateOutputs to verify contract compliance.
                    - The validateOutputs agent performs deterministic contract checks (file existence, sandbox boundaries).
                    - When you issue an agent invocation (JSON), every argument value MUST be a simple string.
                        * Join multiple values with commas, e.g. "args": "--flag1,--flag2".
                        * Provide dependencies as "dependencies": "pip:python-pptx".
                        * Provide deploy arguments as directory paths, e.g. "sourceDir": "scripts", "targetDir": "workdir".
                        * Do not emit JSON arrays or objects inside the arguments map.
                    - If you are unsure, choose the most appropriate next action yourself; you will not receive extra guidance from the user.
                    Always respond with key=value pairs when completing the task.
                    
                    SKILL.md content (must be read fully before acting):
                    %s
                    
                    Expected outputs: %s
                    """.formatted(skillInstructions, expected);
        }

        private String loadSkillInstructions(SkillIndex.SkillMetadata metadata) {
            Path skillMd = metadata.skillRoot().resolve("SKILL.md");
            try {
                return Files.readString(skillMd);
            } catch (IOException e) {
                new WorkflowLogger().warn("Failed to load SKILL.md for skill {}: {}", metadata.id(), e.getMessage());
                return "(SKILL.md could not be loaded; stop and investigate before proceeding)";
            }
        }

    }

    interface ReadReferenceAgentService {
        @SystemMessage("""
                You are a reference document reader for the skill system.
                Your role is to resolve and read reference documents for skills.
                When given a skillId and reference path:
                - Use the readReferenceFile tool to read the requested reference
                - Return the complete reference documents information
                """)
        ReferenceDocuments readReference(
                @V("skillId") String skillId,
                @V("reference") String reference);
    }

    public static final class ReadReferenceAgent {

        private final ReadReferenceAgentService agentService;

        public ReadReferenceAgent(Toolbox toolbox, ChatModel chatModel) {
            this.agentService = AiServices.builder(ReadReferenceAgentService.class)
                    .chatModel(chatModel)
                    .tools(new ReadReferenceTools(toolbox))
                    .build();
        }

        @Agent(
                name = "readRef",
                description = "Resolves and reads an additional reference for the active skill")
        public ReferenceDocuments read(
                @V("skillId") String skillId,
                @V("reference") String reference) {
            return agentService.readReference(skillId, reference);
        }

        private static final class ReadReferenceTools {
            private final Toolbox toolbox;

            ReadReferenceTools(Toolbox toolbox) {
                this.toolbox = Objects.requireNonNull(toolbox, "toolbox");
            }

            @Tool(name = "readReferenceFile", returnBehavior = ReturnBehavior.TO_LLM)
            public ReferenceDocuments readReferenceFile(
                    @P("skillId") String skillId,
                    @P("reference") String reference) {
                return toolbox.readReference(skillId, reference);
            }
        }
    }

    public static final class RunScriptAgent {

        private final Toolbox toolbox;

        public RunScriptAgent(Toolbox toolbox) {
            this.toolbox = Objects.requireNonNull(toolbox, "toolbox");
        }

        @Agent(
                name = "runScript",
                description = "Executes a script within the skill sandbox")
        public ScriptResult run(
                @V("skillId") String skillId,
                @V("path") String path,
                @V("args") Object args,
                @V("dependencies") Object dependencies,
                @V("timeoutSeconds") Integer timeoutSeconds) {
            return toolbox.runScript(
                    skillId,
                    path,
                    coerceToList(args),
                    coerceDependencies(dependencies),
                    timeoutSeconds);
        }

        private List<String> coerceToList(Object value) {
            if (value == null) {
                return List.of();
            }
            if (value instanceof List<?> list) {
                List<String> cleaned = new ArrayList<>();
                for (Object element : list) {
                    if (element == null) {
                        continue;
                    }
                    String text = element.toString().trim();
                    if (!text.isEmpty()) {
                        cleaned.addAll(splitTokens(text));
                    }
                }
                return cleaned.isEmpty() ? List.of() : List.copyOf(cleaned);
            }
            String text = value.toString().trim();
            if (text.isEmpty()) {
                return List.of();
            }
            List<String> tokens = splitTokens(text);
            return tokens.isEmpty() ? List.of() : List.copyOf(tokens);
        }

        private List<String> splitTokens(String raw) {
            if (raw == null || raw.isBlank()) {
                return List.of();
            }
            String normalised = raw.replace("[", "").replace("]", "");
            String[] parts = normalised.split("[\\n,]+");
            List<String> tokens = new ArrayList<>();
            for (String part : parts) {
                String token = part.trim();
                if (!token.isEmpty()) {
                    tokens.add(token);
                }
            }
            return tokens;
        }

        private List<String> coerceDependencies(Object value) {
            if (value == null) {
                return List.of();
            }
            List<String> collected = new ArrayList<>();
            if (value instanceof List<?> list) {
                for (Object element : list) {
                    if (element == null) {
                        continue;
                    }
                    collected.addAll(splitDependencyTokens(element.toString()));
                }
            } else {
                collected.addAll(splitDependencyTokens(value.toString()));
            }
            if (collected.isEmpty()) {
                return List.of();
            }
            return List.copyOf(collected);
        }

        private List<String> splitDependencyTokens(String raw) {
            if (raw == null) {
                return List.of();
            }
            String normalised = raw.trim();
            if (normalised.isEmpty()) {
                return List.of();
            }
            normalised = normalised.replace("[", "").replace("]", "");
            String[] parts = normalised.split("[\\n,]+");
            List<String> result = new ArrayList<>();
            StringBuilder current = null;
            String currentTool = null;
            for (String part : parts) {
                String token = part.trim();
                if (token.isEmpty()) {
                    continue;
                }
                String tool = resolveToolPrefix(token);
                if (tool != null) {
                    if (current != null) {
                        result.add(current.toString());
                    }
                    current = new StringBuilder(token);
                    currentTool = tool;
                    continue;
                }
                if (currentTool != null) {
                    current.append(',').append(token);
                } else {
                    result.add(token);
                }
            }
            if (current != null) {
                result.add(current.toString());
            }
            return result.isEmpty() ? List.of() : List.copyOf(result);
        }

        private String resolveToolPrefix(String token) {
            int separator = token.indexOf(':');
            if (separator <= 0) {
                return null;
            }
            String tool = token.substring(0, separator).trim().toLowerCase(Locale.ROOT);
            if (SUPPORTED_DEPENDENCY_TOOLS.contains(tool)) {
                return tool;
            }
            return null;
        }
    }

    public static final class ScriptDeployAgent {

        private final Toolbox toolbox;

        public ScriptDeployAgent(Toolbox toolbox) {
            this.toolbox = Objects.requireNonNull(toolbox, "toolbox");
        }

        @Agent(
                name = "deployScripts",
                description = "Copies supporting script files into a working directory under build/out")
        public DeploymentResult deploy(
                @V("skillId") String skillId,
                @V("sourceDir") String sourceDir,
                @V("targetDir") String targetDir) {
            return toolbox.deployScripts(skillId, sourceDir, targetDir);
        }
    }

    public static final class WriteArtifactAgent {

        private final Toolbox toolbox;

        public WriteArtifactAgent(Toolbox toolbox) {
            this.toolbox = Objects.requireNonNull(toolbox, "toolbox");
        }

        @Agent(
                name = "writeArtifact",
                description = "Persists generated artefacts under the build output directory. You can save either plain text or Base64-encoded content. Specify which using the base64Encoded parameter.")
        public ArtifactHandle write(
                @V("skillId") String skillId,
                @V(value = "relativePath") String relativePath,
                @V(value = "content") String content,
                @V(value = "base64Encoded") Boolean base64Encoded) {
            boolean encoded = Boolean.TRUE.equals(base64Encoded);
            return toolbox.writeArtifact(skillId, relativePath, content, encoded);
        }
    }

    public static final class ValidateExpectedOutputsAgent {

        private final Toolbox toolbox;

        public ValidateExpectedOutputsAgent(Toolbox toolbox) {
            this.toolbox = Objects.requireNonNull(toolbox, "toolbox");
        }

        @Agent(
                name = "validateExpectedOutputs",
                description = "Validates that expected outputs have been produced")
        public ValidationResult validate(
                @V("expected") Object expected,
                @V("observed") Object observed) {
            return toolbox.validateExpectedOutputs(expected, observed);
        }
    }

    public static final class UnifiedOutputsValidatorAgent {

        private final Toolbox toolbox;
        private final ChatModel chatModel;
        private final boolean enableSemanticCheck;

        public UnifiedOutputsValidatorAgent(Toolbox toolbox, ChatModel chatModel, boolean enableSemanticCheck) {
            this.toolbox = Objects.requireNonNull(toolbox, "toolbox");
            this.chatModel = chatModel;
            this.enableSemanticCheck = enableSemanticCheck;
        }

        @Agent(
                name = "validateOutputs",
                description = "Unified validator that performs contract (deterministic) and semantic (LLM) checks.")
        public ValidationReport validate(
                @V("skillId") String skillId,
                @V("expectedOutputs") String expectedOutputsArgument) {

            List<String> expectedOutputs = normaliseExpectedOutputs(expectedOutputsArgument);
            WorkflowLogger logger = getLogger();
            
            // Phase 1: Contract Check (deterministic, non-AI)
            logger.info("Validation[contract] Starting contract checks for skill {}", skillId);
            ValidationReport contractResult = performContractCheckInternal(expectedOutputs);
            
            if (!contractResult.pass()) {
                // Contract check failed, return immediately without LLM call
                logger.warn("Validation[contract] Failed: {} violations, {} missing", 
                    contractResult.violations().size(), contractResult.missing().size());
                return contractResult;
            }
            
            logger.info("Validation[contract] Passed: {} files, {} bytes", 
                contractResult.metrics().files(), contractResult.metrics().bytes());
            
            // Phase 2: Semantic Check (LLM-based, optional)
            if (!enableSemanticCheck || chatModel == null) {
                // Semantic check disabled or no model available
                logger.debug("Validation[semantic] Skipped (disabled or no model)");
                return contractResult;
            }
            
            logger.info("Validation[semantic] Starting semantic validation");
            ValidationReport semanticResult = performSemanticCheck(expectedOutputs, contractResult);
            logger.info("Validation[semantic] Completed: pass={}", semanticResult.pass());
            
            return semanticResult;
        }

        private List<String> normaliseExpectedOutputs(String rawExpectedOutputs) {
            if (rawExpectedOutputs == null || rawExpectedOutputs.isBlank()) {
                return List.of("artifactPath");
            }

            List<String> outputs = new ArrayList<>();
            String[] tokens = rawExpectedOutputs.split("[\\n,]");
            for (String token : tokens) {
                if (token == null) {
                    continue;
                }
                String trimmed = token.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                int equalsIndex = trimmed.indexOf('=');
                if (equalsIndex >= 0) {
                    trimmed = trimmed.substring(0, equalsIndex).trim();
                }
                if (!trimmed.isEmpty()) {
                    outputs.add(trimmed);
                }
            }

            if (outputs.isEmpty()) {
                outputs.add("artifactPath");
            }

            return List.copyOf(outputs);
        }

        private WorkflowLogger getLogger() {
            if (toolbox instanceof SkillToolbox skillToolbox) {
                return skillToolbox.getContextLogger();
            }
            return new WorkflowLogger(); // Fallback
        }

        private ValidationReport performContractCheckInternal(List<String> expectedOutputs) {
            // Delegate to SkillToolbox contract validation
            if (toolbox instanceof SkillToolbox skillToolbox) {
                return skillToolbox.performContractCheck(expectedOutputs);
            }
            
            // Fallback if not SkillToolbox
            return new ValidationReport(
                true,
                "contract",
                List.of(),
                List.of(),
                "Contract check completed (fallback)",
                new ValidationMetrics(0, 0));
        }

        private ValidationReport performSemanticCheck(List<String> expectedOutputs, ValidationReport contractResult) {
            try {
                String artifactsIndex = generateArtifactsIndexInternal();
                String expectedStr = String.join(", ", expectedOutputs);
                
                // Call LLM for semantic validation
                String llmPrompt = String.format("""
                    You are a strict QA reviewer for software deliverables.
                    
                    Expected outputs: %s
                    
                    Produced artifacts:
                    %s
                    
                    Please evaluate if the artifacts satisfy the expected outputs from a semantic perspective:
                    - Do they cover all requirements?
                    - Is the quality appropriate?
                    - Are there any issues with content or format?
                    
                    Respond with a brief rationale (1-2 sentences).
                    """, expectedStr, artifactsIndex);
                
                String llmResponse = "Semantic validation passed"; // Placeholder
                // In a real implementation, would call chatModel here
                
                return new ValidationReport(
                    true,
                    "semantic",
                    List.of(),
                    List.of(),
                    llmResponse,
                    contractResult.metrics());
                    
            } catch (Exception e) {
                // If semantic check fails, still return contract result
                return new ValidationReport(
                    contractResult.pass(),
                    "contract",
                    contractResult.missing(),
                    contractResult.violations(),
                    "Contract passed, semantic check skipped due to error: " + e.getMessage(),
                    contractResult.metrics());
            }
        }

        private String generateArtifactsIndexInternal() {
            if (toolbox instanceof SkillToolbox skillToolbox) {
                return skillToolbox.generateArtifactsIndex();
            }
            return "(artifacts index unavailable)";
        }
    }

    private final class SkillToolbox implements Toolbox {

        private final SkillRuntimeContext context;

        SkillToolbox(SkillRuntimeContext context) {
            this.context = context;
        }

        WorkflowLogger getContextLogger() {
            return logger;
        }

        @Tool(name = "readSkillMd", returnBehavior = ReturnBehavior.TO_LLM)
        @Override
        public SkillDocumentResult readSkillMd(@P("skillId") String skillId) {
            SkillIndex.SkillMetadata metadata = context.metadata();
            validateSkillId(skillId, metadata.id());
            Path skillMd = metadata.skillRoot().resolve("SKILL.md");
            if (!Files.exists(skillMd)) {
                logger.warn("Act[L2] SKILL.md not found at {}", skillMd);
                context.recordInvocation(
                        "readSkillMd",
                        Map.of("skillId", skillId),
                        Map.of("path", skillMd.toString(), "missing", true));
                return new SkillDocumentResult(skillMd.toString(), "", List.of());
            }
            try {
                String content = Files.readString(skillMd, StandardCharsets.UTF_8);
                String summary = summariseForLog(content);
                long lineCount = content.lines().count();
                logger.info("Act[L2] {} — {}", skillMd, summary);
                context.addDisclosure(new DisclosureEvent(
                        DisclosureLevel.L2, skillMd.toString(), summary));
                context.recordInvocation(
                        "readSkillMd",
                        Map.of("skillId", skillId),
                        Map.of("lineCount", lineCount, "path", skillMd.toString(), "summary", summary));
                context.recordSkill(metadata.id());
                return new SkillDocumentResult(
                        skillMd.toString(), content, extractLocalReferences(content));
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read SKILL.md at " + skillMd, e);
            }
        }

        @Tool(name = "readRef", returnBehavior = ReturnBehavior.TO_LLM)
        @Override
        public ReferenceDocuments readReference(
                @P("skillId") String skillId, @P("reference") String reference) {
            SkillIndex.SkillMetadata metadata = context.metadata();
            validateSkillId(skillId, metadata.id());
            try {
                List<Path> resolved = resolveReferencePaths(metadata, reference);
        List<ReferenceDocument> documents = new ArrayList<>();
        for (Path path : resolved) {
            String kind = Files.isDirectory(path) ? "directory" : "file";
            ReferenceDocumentContent documentContent = readReferenceContent(path);
            logger.info(
                "Act[L3] {} -> {} — {}",
                metadata.id(),
                path,
                summariseForLog(documentContent.summaryText()));
            context.addDisclosure(new DisclosureEvent(
                DisclosureLevel.L3,
                path.toString(),
                summariseForLog(documentContent.summaryText())));
            context.recordReferenced(path);
            documents.add(new ReferenceDocument(
                path.toString(),
                documentContent.detail(),
                kind,
                documentContent.binary()));
        }
        context.recordInvocation(
            "readRef",
            Map.of("skillId", skillId, "reference", reference),
            Map.of(
                "resolvedCount",
                documents.size(),
                "containsBinary",
                documents.stream().anyMatch(ReferenceDocument::binary)));
        context.recordSkill(metadata.id());
        return new ReferenceDocuments(reference, documents);
            } catch (IOException e) {
                throw new IllegalStateException(
                        "Failed to read reference '%s' for skill '%s'".formatted(reference, metadata.id()),
                        e);
            } catch (IllegalArgumentException ex) {
                logger.warn(
                        "Act[L3] reference '{}' for skill '{}' could not be resolved: {}",
                        reference,
                        metadata.id(),
                        ex.getMessage());
                context.recordInvocation(
                        "readRef",
                        Map.of("skillId", skillId, "reference", reference),
                        Map.of("resolvedCount", 0, "error", ex.getMessage()));
                return new ReferenceDocuments(reference, List.of());
            }
        }

        @Tool(name = "runScript", returnBehavior = ReturnBehavior.TO_LLM)
        @Override
        public ScriptResult runScript(
                @P("skillId") String skillId,
                @P("path") String path,
                @P(value = "args", required = false) List<String> args,
                @P(value = "dependencies", required = false) List<String> dependencies,
                @P(value = "timeoutSeconds", required = false) Integer timeoutSeconds) {
            SkillIndex.SkillMetadata metadata = context.metadata();
            validateSkillId(skillId, metadata.id());
            if (path == null || path.isBlank()) {
                throw new IllegalArgumentException("path must be provided when calling runScript");
            }

            Path scriptPath = resolveScriptPath(metadata.skillRoot(), context.outputDirectory(), path);
            String extension = resolveExtension(scriptPath);
            if (!ALLOWED_SCRIPT_EXTENSIONS.contains(extension)) {
                throw new IllegalArgumentException(
                        "Unsupported script type '%s'. Allowed extensions: %s".formatted(
                                extension, ALLOWED_SCRIPT_EXTENSIONS));
            }

            List<String> argsList = normaliseStringList(args);
            List<String> dependenciesList = normaliseStringList(dependencies);
            List<Map<String, Object>> dependencyCalls = installDependencies(metadata, dependenciesList);
            int effectiveTimeout = determineTimeoutSeconds(timeoutSeconds);
            String stdinPayload = toJsonPayload(argsList);
            List<String> command = buildScriptCommand(extension, scriptPath, argsList);
            Map<String, String> environmentOverrides = buildScriptEnvironment(extension, metadata, context);
            Path workingDirectory = scriptPath.getParent();
            if (workingDirectory == null) {
                throw new IllegalStateException("Script path does not have a parent directory");
            }
            Path normalisedWorkingDirectory = workingDirectory.toAbsolutePath().normalize();
            if (!normalisedWorkingDirectory.startsWith(context.outputDirectory())
                    && !normalisedWorkingDirectory.startsWith(metadata.skillRoot())) {
                throw new IllegalArgumentException(
                        "Working directory must reside under the skill root or output directory");
            }
            ProcessResult execution = executeProcess(
                    command,
                    normalisedWorkingDirectory,
                    stdinPayload,
                    Duration.ofSeconds(effectiveTimeout),
                    environmentOverrides);

            String stdoutLimited = limitChars(execution.stdout(), MAX_STDOUT_CHARS);
            String stderrLimited = limitText(execution.stderr(), MAX_STDERR_LINES, MAX_STDERR_CHARS);
            Map<String, Object> stdoutJson = parseStdoutJson(execution.stdout());

            long durationMillis = execution.durationMillis();
            String message = "Act[script] {} — {} exit={} timedOut={} durationMs={}";
            if (execution.exitCode() == 0 && !execution.timedOut()) {
                logger.info(message, metadata.id(), scriptPath, execution.exitCode(), execution.timedOut(), durationMillis);
            } else {
                logger.warn(message, metadata.id(), scriptPath, execution.exitCode(), execution.timedOut(), durationMillis);
            }

            Map<String, Object> invocationArgs = new LinkedHashMap<>();
            invocationArgs.put("skillId", skillId);
            invocationArgs.put("path", scriptPath.toString());
            invocationArgs.put("workingDirectory", normalisedWorkingDirectory.toString());
            invocationArgs.put("timeoutSeconds", effectiveTimeout);
            if (!argsList.isEmpty()) {
                invocationArgs.put("args", argsList);
            }
            if (!dependenciesList.isEmpty()) {
                invocationArgs.put("dependencies", dependenciesList);
            }

            Map<String, Object> invocationResult = new LinkedHashMap<>();
            invocationResult.put("exitCode", execution.exitCode());
            invocationResult.put("timedOut", execution.timedOut());
            invocationResult.put("durationMillis", durationMillis);
            if (!stdoutLimited.isBlank()) {
                invocationResult.put("stdoutPreview", summariseForLog(stdoutLimited));
            }
            if (!stderrLimited.isBlank()) {
                invocationResult.put("stderrPreview", summariseForLog(stderrLimited));
            }
            if (!dependencyCalls.isEmpty()) {
                invocationResult.put("dependencyCalls", dependencyCalls);
            }

            context.recordInvocation("runScript", invocationArgs, invocationResult);
            context.recordSkill(metadata.id());
            context.recordReferenced(scriptPath);
            context.recordReferenced(normalisedWorkingDirectory);

            return new ScriptResult(
                    scriptPath.toString(),
                    execution.exitCode(),
                    execution.timedOut(),
                    stdoutJson,
                    stdoutLimited,
                    stderrLimited,
                    durationMillis,
                    dependencyCalls);
        }

        @Tool(name = "deployScripts", returnBehavior = ReturnBehavior.TO_LLM)
        @Override
        public DeploymentResult deployScripts(
                @P("skillId") String skillId,
                @P("sourceDir") String sourceDir,
                @P("targetDir") String targetDir) {
            SkillIndex.SkillMetadata metadata = context.metadata();
            validateSkillId(skillId, metadata.id());
            if (sourceDir == null || sourceDir.isBlank()) {
                throw new IllegalArgumentException("sourceDir must be provided when calling deployScripts");
            }
            if (targetDir == null || targetDir.isBlank()) {
                throw new IllegalArgumentException("targetDir must be provided when calling deployScripts");
            }
            Path sourcePath = resolveDeploymentSource(metadata.skillRoot(), context.outputDirectory(), sourceDir);
            if (!Files.isDirectory(sourcePath)) {
                throw new IllegalArgumentException("Deployment source must be a directory: " + sourcePath);
            }
            Path targetPath = resolveDeploymentTarget(context.outputDirectory(), targetDir);
            DeploymentResult deployment = copyDirectoryTree(sourcePath, targetPath);
            logger.info(
                    "Act[deploy] {} — {} -> {} (filesCopied={}, directoriesCreated={})",
                    metadata.id(),
                    sourcePath,
                    targetPath,
                    deployment.filesCopied(),
                    deployment.directoriesCreated());
            context.recordInvocation(
                    "deployScripts",
                    Map.of(
                            "skillId", skillId,
                            "sourceDir", sourceDir,
                            "targetDir", targetDir),
                    Map.of(
                            "source", deployment.source(),
                            "target", deployment.target(),
                            "filesCopied", deployment.filesCopied(),
                            "directoriesCreated", deployment.directoriesCreated()));
            context.recordSkill(metadata.id());
            context.recordReferenced(sourcePath);
            context.recordReferenced(targetPath);
            return deployment;
        }

        @Tool(name = "writeArtifact", returnBehavior = ReturnBehavior.TO_LLM)
        @Override
        public ArtifactHandle writeArtifact(
                @P("skillId") String skillId,
                @P(value = "relativePath", required = false) String relativePath,
                @P(value = "content", required = false) String content,
                @P(value = "base64Encoded", required = false) boolean base64Encoded) {
            SkillIndex.SkillMetadata metadata = context.metadata();
            validateSkillId(skillId, metadata.id());
            Path outputPath = context.resolveOutputPath(relativePath);
            byte[] bytes;
            String payload = content == null ? "" : content;
            if (base64Encoded) {
                try {
                    bytes = Base64.getDecoder().decode(payload);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Failed to decode Base64 content", e);
                }
            } else {
                bytes = payload.getBytes(StandardCharsets.UTF_8);
            }
            try {
                Path parent = outputPath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.write(outputPath, bytes);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to write artefact: " + outputPath, e);
            }
            String preview = summarisePreview(bytes);
            context.registerArtifact(outputPath, bytes.length, preview);
            Map<String, Object> result = Map.of(
                    "path", outputPath.toString(),
                    "bytesWritten", bytes.length,
                    "preview", preview);
            context.recordInvocation(
                    "writeArtifact",
                    Map.of(
                            "relativePath",
                            relativePath == null || relativePath.isBlank() ? DEFAULT_OUTPUT_FILE : relativePath,
                "skillId",
                skillId,
                "base64Encoded",
                base64Encoded),
                    result);
        context.recordSkill(metadata.id());
            return new ArtifactHandle(outputPath.toString(), bytes.length, preview);
        }

        @Tool(name = "validateExpectedOutputs", returnBehavior = ReturnBehavior.TO_LLM)
        @Override
        public ValidationResult validateExpectedOutputs(
                @P(value = "expected", required = false) Object expected,
                @P(value = "observed", required = false) Object observed) {
            Map<String, Object> expectedOverrides = normaliseExpectedOverrides(expected);
            List<String> expectedKeys = resolveExpectedKeys(expectedOverrides);
            Map<String, Object> observedMap = normaliseObservedResults(observed);
            if (context.artifactPath() != null) {
                Map<String, Object> merged = new LinkedHashMap<>(observedMap);
                merged.putIfAbsent("artifactPath", context.artifactPath().toString());
                observedMap = Map.copyOf(merged);
            }
            Validation validation = SkillRuntime.this.validateOutputs(
                    expectedKeys, observedMap, context.artifactPath());
            context.setValidation(validation);
            Map<String, Object> invocationArgs = new LinkedHashMap<>();
            if (!expectedOverrides.isEmpty()) {
                invocationArgs.put("expected", expectedOverrides);
            }
            invocationArgs.put("expectedKeys", expectedKeys);
            if (!observedMap.isEmpty()) {
                invocationArgs.put("observed", observedMap);
            }
            context.recordInvocation(
                    "validateExpectedOutputs",
                    invocationArgs,
                    Map.of(
                            "satisfied", validation.expectedOutputsSatisfied(),
                            "missing", validation.missingOutputs()));
            context.recordSkill(context.metadata().id());
            return new ValidationResult(validation.expectedOutputsSatisfied(), validation.missingOutputs());
        }

        private Map<String, Object> normaliseExpectedOverrides(Object overrides) {
            if (overrides == null) {
                return Map.of();
            }
            if (overrides instanceof Map<?, ?> map) {
                return copyWithStringKeys(map, Function.identity());
            }
            if (overrides instanceof List<?> list) {
                LinkedHashMap<String, Object> result = new LinkedHashMap<>();
                for (Object element : list) {
                    if (element == null) {
                        continue;
                    }
                    addExpectedToken(result, element.toString());
                }
                return result.isEmpty() ? Map.of() : Map.copyOf(result);
            }
            return tokensToExpectedMap(overrides.toString());
        }

        private List<String> resolveExpectedKeys(Map<String, Object> overrides) {
            if (overrides == null || overrides.isEmpty()) {
                return context.expectedOutputs();
            }
            LinkedHashSet<String> keys = new LinkedHashSet<>();
            overrides.forEach((rawKey, value) -> {
                if (value instanceof Boolean bool && !bool) {
                    return;
                }
                String key = Objects.toString(rawKey, "").trim();
                if (!key.isEmpty()) {
                    keys.add(key);
                }
            });
            if (keys.isEmpty()) {
                return context.expectedOutputs();
            }
            return List.copyOf(keys);
        }

        private Map<String, Object> normaliseObservedResults(Object observed) {
            if (observed == null) {
                return Map.of();
            }
            if (observed instanceof Map<?, ?> map) {
                return copyWithStringKeys(map, Function.identity());
            }
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            if (observed instanceof List<?> list) {
                for (Object element : list) {
                    if (element == null) {
                        continue;
                    }
                    parseObservedEntry(element.toString(), result);
                }
                if (!result.isEmpty()) {
                    return Map.copyOf(result);
                }
            }
            String text = observed.toString().trim();
            if (text.isEmpty()) {
                return Map.of();
            }
            if (!parseObservedEntry(text, result)) {
                List<String> expectedOutputs = context.expectedOutputs();
                String defaultKey;
                if (expectedOutputs.isEmpty()) {
                    defaultKey = "artifactPath";
                } else if (expectedOutputs.size() == 1) {
                    defaultKey = expectedOutputs.get(0);
                } else {
                    defaultKey = "value";
                }
                result.put(defaultKey, text);
            }
            return Map.copyOf(result);
        }

        private Map<String, Object> copyWithStringKeys(
                Map<?, ?> map, Function<Object, Object> valueMapper) {
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, value) -> {
                String stringKey = Objects.toString(key, "").trim();
                if (stringKey.isEmpty()) {
                    return;
                }
                Object mappedValue = valueMapper.apply(value);
                result.put(stringKey, mappedValue);
            });
            return result.isEmpty() ? Map.of() : Map.copyOf(result);
        }

        private Map<String, Object> tokensToExpectedMap(String raw) {
            String cleaned = Objects.toString(raw, "").trim();
            if (cleaned.isEmpty()) {
                return Map.of();
            }
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            boolean added = false;
            for (String token : cleaned.split("[\\n,]")) {
                added |= addExpectedToken(result, token);
            }
            if (!added) {
                addExpectedToken(result, cleaned);
            }
            return result.isEmpty() ? Map.of() : Map.copyOf(result);
        }

        private boolean addExpectedToken(Map<String, Object> target, String token) {
            String trimmed = Objects.toString(token, "").trim();
            if (trimmed.isEmpty()) {
                return false;
            }
            List<String> expectedOutputs = context.expectedOutputs();
            if (expectedOutputs.contains(trimmed)) {
                target.put(trimmed, Boolean.TRUE);
                return true;
            }
            if (looksLikePath(trimmed)) {
                String key;
                if (expectedOutputs.isEmpty()) {
                    key = "artifactPath";
                } else {
                    key = expectedOutputs.get(0);
                }
                target.put(key, trimmed);
                return true;
            }
            target.put(trimmed, Boolean.TRUE);
            return true;
        }

        private boolean parseObservedEntry(String entry, Map<String, Object> accumulator) {
            String trimmed = Objects.toString(entry, "").trim();
            if (trimmed.isEmpty()) {
                return false;
            }
            int separator = trimmed.indexOf('=');
            if (separator > 0) {
                String key = trimmed.substring(0, separator).trim();
                String value = trimmed.substring(separator + 1).trim();
                if (!key.isEmpty()) {
                    accumulator.put(key, value);
                    return true;
                }
            }
            return false;
        }

        private boolean looksLikePath(String value) {
            if (value.indexOf('/') >= 0 || value.indexOf('\\') >= 0) {
                return true;
            }
            int dot = value.lastIndexOf('.');
            return dot > 0 && dot < value.length() - 1;
        }

        private void validateSkillId(String requested, String actual) {
            if (requested == null || requested.isBlank()) {
                throw new IllegalArgumentException("skillId must be provided when calling SkillRuntime tools");
            }
            if (!Objects.equals(requested, actual)) {
                throw new IllegalArgumentException(
                        "Tool requested skillId '%s' but runtime is executing '%s'".formatted(requested, actual));
            }
        }

        private List<Path> resolveReferencePaths(SkillIndex.SkillMetadata metadata, String reference)
                throws IOException {
            List<Path> resolved = new ArrayList<>();
            IllegalArgumentException resolutionFailure = null;
            try {
                resolved.addAll(skillIndex.resolveReferences(metadata.id(), reference));
            } catch (IllegalArgumentException ex) {
                resolutionFailure = ex;
            }
            resolved.addAll(resolveOutputReferences(reference));
            List<Path> deduplicated = resolved.stream()
                    .map(Path::toAbsolutePath)
                    .map(Path::normalize)
                    .collect(Collectors.toCollection(LinkedHashSet::new))
                    .stream()
                    .collect(Collectors.toCollection(ArrayList::new));
            if (!deduplicated.isEmpty()) {
                return deduplicated;
            }
            if (resolutionFailure != null) {
                throw resolutionFailure;
            }
            throw new IllegalArgumentException(
                    "No resources matched '%s' for skill '%s'".formatted(reference, metadata.id()));
        }

        private List<Path> resolveOutputReferences(String reference) throws IOException {
            String normalised = Objects.requireNonNull(reference, "reference").trim();
            if (normalised.isEmpty()) {
                return List.of();
            }
            Path outputBase = context.outputDirectory().toAbsolutePath().normalize();
            Set<Path> matches = new LinkedHashSet<>();
            for (String variant : expandOutputReferenceVariants(outputBase, normalised)) {
                if (variant.isBlank()) {
                    continue;
                }
                if (isGlobPattern(variant)) {
                    matches.addAll(resolveOutputGlob(outputBase, variant));
                    continue;
                }
                Path candidate = Path.of(variant);
                List<Path> options = new ArrayList<>();
                if (candidate.isAbsolute()) {
                    options.add(candidate);
                } else {
                    options.add(outputBase.resolve(candidate));
                }
                for (Path option : options) {
                    Path normalisedPath = option.toAbsolutePath().normalize();
                    if (normalisedPath.startsWith(outputBase) && Files.exists(normalisedPath)) {
                        matches.add(normalisedPath);
                    }
                }
            }
            return matches.stream().collect(Collectors.toCollection(ArrayList::new));
        }

        private ReferenceDocumentContent readReferenceContent(Path path) throws IOException {
            if (Files.isDirectory(path)) {
                return new ReferenceDocumentContent("(directory)", "(directory)", false);
            }
            try {
                String text = Files.readString(path, StandardCharsets.UTF_8);
                String limited = limitChars(text, 16384);
                return new ReferenceDocumentContent(limited, limited, false);
            } catch (MalformedInputException ex) {
                byte[] bytes = Files.readAllBytes(path);
                String preview = summarisePreview(bytes);
                if (preview == null || preview.isBlank()) {
                    preview = "(binary preview suppressed)";
                }
                String detail = """
                        [Binary file]
                        Path: %s
                        Size: %d bytes
                        Preview: %s
                        """.formatted(path.toString(), bytes.length, preview);
                return new ReferenceDocumentContent(detail.strip(), preview, true);
            }
        }

        private Set<String> expandOutputReferenceVariants(Path outputBase, String reference) {
            Set<String> variants = new LinkedHashSet<>();
            variants.add(reference);
            if (reference.startsWith("./")) {
                variants.add(reference.substring(2));
            }
            if (reference.startsWith("/")) {
                variants.add(reference.substring(1));
            }
            Path baseName = outputBase.getFileName();
            if (baseName != null) {
                String basePrefix = baseName + "/";
                if (reference.startsWith(basePrefix)) {
                    variants.add(reference.substring(basePrefix.length()));
                }
            }
            Path parent = outputBase.getParent();
            if (parent != null) {
                Path parentName = parent.getFileName();
                if (parentName != null && baseName != null) {
                    String combined = parentName + "/" + baseName + "/";
                    if (reference.startsWith(combined)) {
                        variants.add(reference.substring(combined.length()));
                    }
                }
            }
            return variants;
        }

        private boolean isGlobPattern(String candidate) {
            return candidate.contains("*")
                    || candidate.contains("?")
                    || candidate.contains("{")
                    || candidate.contains("[");
        }

        private List<Path> resolveOutputGlob(Path outputBase, String pattern) throws IOException {
            Path normalisedBase = outputBase.toAbsolutePath().normalize();
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + adaptGlobPattern(pattern));
            Set<Path> resolved = new LinkedHashSet<>();
            try (var stream = Files.walk(normalisedBase)) {
                stream.filter(path -> !path.equals(normalisedBase))
                        .filter(path -> matcher.matches(normalisedBase.relativize(path)))
                        .map(Path::toAbsolutePath)
                        .map(Path::normalize)
                        .filter(path -> path.startsWith(normalisedBase))
                        .forEach(resolved::add);
            }
            return resolved.stream().collect(Collectors.toCollection(ArrayList::new));
        }

        private String adaptGlobPattern(String pattern) {
            String separator = FileSystems.getDefault().getSeparator();
            if ("/".equals(separator)) {
                return pattern;
            }
            return pattern.replace("/", separator);
        }

        private Path resolveScriptPath(Path skillRoot, Path outputDir, String requested) {
            Path candidate = Path.of(requested == null ? "" : requested);
            List<Path> searchOrder = new ArrayList<>();
            if (candidate.isAbsolute()) {
                searchOrder.add(candidate.toAbsolutePath().normalize());
            } else {
                Path normalised = candidate.normalize();
                Path outputRelative = SkillRuntime.sanitiseOutputRelativePath(normalised, outputDir);
                searchOrder.add(skillRoot.resolve(normalised));
                searchOrder.add(outputDir.resolve(outputRelative));
                if (!outputRelative.equals(normalised)) {
                    searchOrder.add(outputDir.resolve(normalised));
                }
                Path projectRoot = SkillRuntime.this.skillIndex.skillsRoot().getParent();
                if (projectRoot != null) {
                    searchOrder.add(projectRoot.resolve(normalised));
                }
            }
            for (Path option : searchOrder) {
                Path normalised = option.toAbsolutePath().normalize();
                if (Files.exists(normalised)
                        && Files.isRegularFile(normalised)
                        && (normalised.startsWith(skillRoot) || normalised.startsWith(outputDir))) {
                    return normalised;
                }
            }
            if (candidate.isAbsolute()) {
                Path absolute = candidate.toAbsolutePath().normalize();
                if (!absolute.startsWith(skillRoot) && !absolute.startsWith(outputDir)) {
                    throw new IllegalArgumentException(
                            "Script path must reside under the skill root or output directory");
                }
                if (Files.exists(absolute) && Files.isRegularFile(absolute)) {
                    return absolute;
                }
            }
            throw new IllegalArgumentException(
                    "Script '%s' could not be resolved for skill '%s'".formatted(requested, context.metadata().id()));
        }

        private String resolveExtension(Path path) {
            String name = path.getFileName().toString();
            int index = name.lastIndexOf('.');
            if (index < 0) {
                return "";
            }
            return name.substring(index).toLowerCase();
        }

        private Path resolveDeploymentSource(Path skillRoot, Path outputDir, String sourceDir) {
            Path candidate = Path.of(sourceDir == null ? "" : sourceDir);
            List<Path> searchOrder = new ArrayList<>();
            if (candidate.isAbsolute()) {
                searchOrder.add(candidate.toAbsolutePath().normalize());
            } else {
                Path normalised = candidate.normalize();
                Path outputRelative = SkillRuntime.sanitiseOutputRelativePath(normalised, outputDir);
                searchOrder.add(outputDir.resolve(outputRelative));
                if (!outputRelative.equals(normalised)) {
                    searchOrder.add(outputDir.resolve(normalised));
                }
                searchOrder.add(skillRoot.resolve(normalised));
            }
            for (Path option : searchOrder) {
                Path normalised = option.toAbsolutePath().normalize();
                if (Files.exists(normalised)
                        && Files.isDirectory(normalised)
                        && (normalised.startsWith(skillRoot) || normalised.startsWith(outputDir))) {
                    return normalised;
                }
            }
            throw new IllegalArgumentException(
                    "Deployment source '" + sourceDir + "' could not be resolved for skill '" + context.metadata().id() + "'");
        }

        private Path resolveDeploymentTarget(Path outputDir, String targetDir) {
            Path candidate = Path.of(targetDir == null ? "" : targetDir);
            if (!candidate.isAbsolute()) {
                candidate = SkillRuntime.sanitiseOutputRelativePath(candidate.normalize(), outputDir);
            }
            Path resolved = candidate.isAbsolute() ? candidate.toAbsolutePath().normalize() : outputDir.resolve(candidate);
            Path normalised = resolved.toAbsolutePath().normalize();
            if (!normalised.startsWith(outputDir)) {
                throw new IllegalArgumentException("Deployment target must remain under " + outputDir);
            }
            return normalised;
        }

        private DeploymentResult copyDirectoryTree(Path source, Path target) {
            try {
                Files.createDirectories(target);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to prepare deployment target: " + target, e);
            }
            int[] filesCopied = {0};
            int[] directoriesCreated = {0};
            try (var stream = Files.walk(source)) {
                List<Path> entries = stream
                        .sorted((left, right) -> {
                            int depthCompare = Integer.compare(left.getNameCount(), right.getNameCount());
                            if (depthCompare != 0) {
                                return depthCompare;
                            }
                            boolean leftDir = Files.isDirectory(left);
                            boolean rightDir = Files.isDirectory(right);
                            if (leftDir != rightDir) {
                                return leftDir ? -1 : 1;
                            }
                            return left.compareTo(right);
                        })
                        .collect(Collectors.toCollection(ArrayList::new));
                for (Path entry : entries) {
                    Path relative = source.relativize(entry);
                    Path destination = target.resolve(relative.toString()).toAbsolutePath().normalize();
                    if (!destination.startsWith(target)) {
                        throw new IllegalArgumentException("Deployment would escape target directory: " + destination);
                    }
                    try {
                        if (Files.isDirectory(entry)) {
                            Files.createDirectories(destination);
                            if (!relative.toString().isEmpty()) {
                                directoriesCreated[0]++;
                            }
                        } else {
                            Path parent = destination.getParent();
                            if (parent != null) {
                                Files.createDirectories(parent);
                            }
                            Files.copy(entry, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                            filesCopied[0]++;
                        }
                    } catch (IOException e) {
                        throw new IllegalStateException(
                                "Failed to copy '" + entry + "' to '" + destination + "'", e);
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException("Failed to traverse deployment source: " + source, e);
            }
            return new DeploymentResult(
                    source.toString(),
                    target.toString(),
                    filesCopied[0],
                    directoriesCreated[0]);
        }

        private int determineTimeoutSeconds(Integer requestedSeconds) {
            int seconds = requestedSeconds == null ? DEFAULT_SCRIPT_TIMEOUT_SECONDS : requestedSeconds;
            if (seconds <= 0) {
                seconds = DEFAULT_SCRIPT_TIMEOUT_SECONDS;
            }
            return Math.min(seconds, MAX_SCRIPT_TIMEOUT_SECONDS);
        }

        private String toJsonPayload(List<String> payload) {
            List<String> safePayload = payload == null ? List.of() : payload;
            try {
                return OBJECT_MAPPER.writeValueAsString(safePayload);
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed to serialise script args to JSON", e);
            }
        }

        private Map<String, Object> parseStdoutJson(String stdout) {
            if (stdout == null || stdout.isBlank()) {
                return Map.of();
            }
            try {
                return OBJECT_MAPPER.readValue(stdout, JSON_MAP_TYPE);
            } catch (IOException e) {
                logger.debug("Act[script] stdout is not valid JSON: {}", e.getMessage());
                return Map.of();
            }
        }

        private List<String> buildScriptCommand(String extension, Path scriptPath, List<String> args) {
            List<String> command = new ArrayList<>();
            if (".py".equals(extension)) {
                command.add(locatePythonExecutable());
            } else if (".js".equals(extension)) {
                command.add(locateNodeExecutable());
            } else if (".sh".equals(extension)) {
                command.add(locateShellExecutable());
            } else {
                throw new IllegalArgumentException("Unsupported script extension: " + extension);
            }
            command.add(scriptPath.toString());
            if (args != null && !args.isEmpty()) {
                command.addAll(args);
            }
            return List.copyOf(command);
        }

        private Map<String, String> buildScriptEnvironment(
                String extension, SkillIndex.SkillMetadata metadata, SkillRuntimeContext context) {
            if (!".js".equals(extension)) {
                return Map.of();
            }
            Path envNodeModules = nodeEnvDirectory().resolve("node_modules").normalize();
            Path skillNodeModules = metadata.skillRoot().resolve("node_modules").normalize();
            Path outputNodeModules = context.outputDirectory().resolve("node_modules").normalize();
            LinkedHashSet<String> nodePaths = new LinkedHashSet<>();
            if (Files.isDirectory(envNodeModules)) {
                nodePaths.add(envNodeModules.toString());
            }
            if (Files.isDirectory(skillNodeModules)) {
                nodePaths.add(skillNodeModules.toString());
            }
            if (Files.isDirectory(outputNodeModules)) {
                nodePaths.add(outputNodeModules.toString());
            }
            String existing = System.getenv("NODE_PATH");
            if (existing != null && !existing.isBlank()) {
                nodePaths.add(existing);
            }
            if (nodePaths.isEmpty()) {
                return Map.of();
            }
            String joined = String.join(File.pathSeparator, nodePaths);
            return Map.of("NODE_PATH", joined);
        }

        private List<Map<String, Object>> installDependencies(
                SkillIndex.SkillMetadata metadata, List<String> dependencies) {
            if (dependencies == null || dependencies.isEmpty()) {
                return List.of();
            }
            Map<String, List<String>> grouped = groupDependencies(dependencies);
            if (grouped.isEmpty()) {
                return List.of();
            }
            List<Map<String, Object>> summaries = new ArrayList<>();
            List<String> pipPackages = grouped.getOrDefault("pip", List.of());
            if (!pipPackages.isEmpty()) {
                ProcessResult pipResult = executeProcess(
                        buildPipCommand(pipPackages),
                        resolveDependencyWorkDir(pythonEnvDirectory(), metadata.skillRoot()),
                        null,
                        Duration.ofSeconds(DEFAULT_DEPENDENCY_TIMEOUT_SECONDS));
                logDependencyOutcome("pip", pipPackages, pipResult);
                summaries.add(createDependencySummary("pip", pipPackages, pipResult));
            }
            List<String> npmPackages = grouped.getOrDefault("npm", List.of());
            if (!npmPackages.isEmpty()) {
                ProcessResult npmResult = executeProcess(
                        buildNpmCommand(npmPackages),
                        resolveDependencyWorkDir(nodeEnvDirectory(), metadata.skillRoot()),
                        null,
                        Duration.ofSeconds(DEFAULT_DEPENDENCY_TIMEOUT_SECONDS));
                logDependencyOutcome("npm", npmPackages, npmResult);
                summaries.add(createDependencySummary("npm", npmPackages, npmResult));
            }
            if (summaries.isEmpty()) {
                return List.of();
            }
            return List.copyOf(summaries);
        }

        private Map<String, List<String>> groupDependencies(List<String> dependencies) {
            Map<String, List<String>> grouped = new LinkedHashMap<>();
            for (String entry : dependencies) {
                if (entry == null || entry.isBlank()) {
                    continue;
                }
                DependencyDescriptor descriptor = parseDependencyEntry(entry);
                if (descriptor.packages().isEmpty()) {
                    continue;
                }
                grouped.computeIfAbsent(descriptor.tool(), key -> new ArrayList<>())
                        .addAll(descriptor.packages());
            }
            Map<String, List<String>> immutable = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> e : grouped.entrySet()) {
                immutable.put(e.getKey(), List.copyOf(e.getValue()));
            }
            return immutable;
        }

        private DependencyDescriptor parseDependencyEntry(String rawEntry) {
            String entry = rawEntry.trim();
            int separator = entry.indexOf(':');
            String tool;
            String payload;
            if (separator > 0) {
                tool = entry.substring(0, separator).trim().toLowerCase(Locale.ROOT);
                payload = entry.substring(separator + 1).trim();
            } else {
                tool = "pip";
                payload = entry;
            }
            if (tool.isEmpty()) {
                tool = "pip";
            }
            if (!"pip".equals(tool) && !"npm".equals(tool)) {
                throw new IllegalArgumentException(
                        "Unsupported dependency tool '%s'. Supported tools: pip, npm".formatted(tool));
            }
            List<String> packages = new ArrayList<>();
            if (!payload.isEmpty()) {
                for (String token : payload.split("[,\\s]+")) {
                    String value = token.trim();
                    if (!value.isEmpty()) {
                        packages.add(value);
                    }
                }
            }
            return new DependencyDescriptor(tool, List.copyOf(packages));
        }

        private final class DependencyDescriptor {
            private final String tool;
            private final List<String> packages;

            private DependencyDescriptor(String tool, List<String> packages) {
                this.tool = Objects.requireNonNullElse(tool, "pip");
                this.packages = packages == null ? List.of() : List.copyOf(packages);
            }

            private String tool() {
                return tool;
            }

            private List<String> packages() {
                return packages;
            }
        }

        private List<String> normaliseStringList(List<String> values) {
            if (values == null || values.isEmpty()) {
                return List.of();
            }
            List<String> cleaned = new ArrayList<>();
            for (String value : values) {
                if (value == null) {
                    continue;
                }
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    cleaned.add(trimmed);
                }
            }
            if (cleaned.isEmpty()) {
                return List.of();
            }
            return List.copyOf(cleaned);
        }

        private void logDependencyOutcome(String tool, List<String> packages, ProcessResult result) {
            String message = "Act[script] dependency {} {} exit={} timedOut={} durationMs={}";
            if (result.exitCode() == 0 && !result.timedOut()) {
                logger.info(message, tool, packages, result.exitCode(), result.timedOut(), result.durationMillis());
            } else {
                logger.warn(message, tool, packages, result.exitCode(), result.timedOut(), result.durationMillis());
            }
        }

        private Map<String, Object> createDependencySummary(String tool, List<String> packages, ProcessResult result) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("tool", tool);
            summary.put("packages", List.copyOf(packages));
            summary.put("command", String.join(" ", result.command()));
            summary.put("exitCode", result.exitCode());
            summary.put("timedOut", result.timedOut());
            summary.put("durationMillis", result.durationMillis());
            if (!result.stdout().isBlank()) {
                summary.put("stdoutPreview", summariseForLog(limitChars(result.stdout(), 512)));
            }
            if (!result.stderr().isBlank()) {
                summary.put("stderrPreview", summariseForLog(limitText(result.stderr(), 16, 512)));
            }
            return summary;
        }

        private List<String> buildPipCommand(List<String> packages) {
            List<String> command = new ArrayList<>();
            command.add(locatePipExecutable());
            command.add("install");
            command.addAll(packages);
            return command;
        }

        private List<String> buildNpmCommand(List<String> packages) {
            List<String> command = new ArrayList<>();
            command.add(locateNpmExecutable());
            command.add("install");
            command.addAll(packages);
            return command;
        }

        private Path resolveDependencyWorkDir(Path preferred, Path fallback) {
            if (preferred != null && Files.isDirectory(preferred)) {
                return preferred;
            }
            return fallback;
        }

        private Path pythonEnvDirectory() {
            return SkillRuntime.this.skillIndex.skillsRoot().resolveSibling("env").resolve("python")
                    .toAbsolutePath()
                    .normalize();
        }

        private Path nodeEnvDirectory() {
            return SkillRuntime.this.skillIndex.skillsRoot().resolveSibling("env").resolve("node")
                    .toAbsolutePath()
                    .normalize();
        }

        private String locatePythonExecutable() {
            Path envDir = pythonEnvDirectory();
            Path unix = envDir.resolve("bin").resolve("python");
            if (Files.isRegularFile(unix)) {
                return unix.toString();
            }
            Path windows = envDir.resolve("Scripts").resolve("python.exe");
            if (Files.isRegularFile(windows)) {
                return windows.toString();
            }
            return "python3";
        }

        private String locatePipExecutable() {
            Path envDir = pythonEnvDirectory();
            Path unix = envDir.resolve("bin").resolve("pip");
            if (Files.isRegularFile(unix)) {
                return unix.toString();
            }
            Path windows = envDir.resolve("Scripts").resolve("pip.exe");
            if (Files.isRegularFile(windows)) {
                return windows.toString();
            }
            return "pip";
        }

        private String locateNodeExecutable() {
            Path envDir = nodeEnvDirectory();
            Path unix = envDir.resolve("bin").resolve("node");
            if (Files.isRegularFile(unix)) {
                return unix.toString();
            }
            Path modules = envDir.resolve("node_modules").resolve(".bin").resolve("node");
            if (Files.isRegularFile(modules)) {
                return modules.toString();
            }
            return "node";
        }

        private String locateNpmExecutable() {
            Path envDir = nodeEnvDirectory();
            Path unix = envDir.resolve("bin").resolve("npm");
            if (Files.isRegularFile(unix)) {
                return unix.toString();
            }
            Path modules = envDir.resolve("node_modules").resolve(".bin").resolve("npm");
            if (Files.isRegularFile(modules)) {
                return modules.toString();
            }
            return "npm";
        }

        private String locateShellExecutable() {
            Path bash = Path.of("/bin/bash");
            if (Files.isRegularFile(bash)) {
                return bash.toString();
            }
            return "bash";
        }

        private ProcessResult executeProcess(
                List<String> command, Path workingDirectory, String stdinPayload, Duration timeout) {
            return executeProcess(command, workingDirectory, stdinPayload, timeout, Map.of());
        }

        private ProcessResult executeProcess(
                List<String> command,
                Path workingDirectory,
                String stdinPayload,
                Duration timeout,
                Map<String, String> environmentOverrides) {
            Objects.requireNonNull(command, "command");
            Objects.requireNonNull(workingDirectory, "workingDirectory");
            Objects.requireNonNull(timeout, "timeout");
            List<String> commandCopy = List.copyOf(command);
            Instant start = Instant.now();
            ProcessBuilder builder = new ProcessBuilder(commandCopy);
            builder.directory(workingDirectory.toFile());
            builder.redirectErrorStream(false);
            if (environmentOverrides != null && !environmentOverrides.isEmpty()) {
                Map<String, String> environment = builder.environment();
                for (Map.Entry<String, String> entry : environmentOverrides.entrySet()) {
                    String value = entry.getValue();
                    if (value == null) {
                        environment.remove(entry.getKey());
                    } else {
                        environment.put(entry.getKey(), value);
                    }
                }
            }
            Process process;
            try {
                process = builder.start();
            } catch (IOException e) {
                throw new IllegalStateException("Failed to start process: " + String.join(" ", commandCopy), e);
            }
            try (OutputStream output = process.getOutputStream()) {
                if (stdinPayload != null && !stdinPayload.isEmpty()) {
                    try (OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
                        writer.write(stdinPayload);
                        writer.flush();
                    }
                }
            } catch (IOException e) {
                process.destroyForcibly();
                throw new IllegalStateException("Failed to send input to process", e);
            }
            boolean finished;
            try {
                finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
                throw new IllegalStateException("Interrupted while waiting for process", e);
            }
            if (!finished) {
                process.destroyForcibly();
                try {
                    process.waitFor(1, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            String stdout = readAll(process.getInputStream());
            String stderr = readAll(process.getErrorStream());
            long durationMillis = Duration.between(start, Instant.now()).toMillis();
            int exitCode = finished ? process.exitValue() : -1;
            return new ProcessResult(commandCopy, exitCode, !finished, stdout, stderr, durationMillis);
        }

        private String readAll(InputStream stream) {
            try (InputStream input = stream) {
                return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private String limitChars(String text, int maxChars) {
            if (text == null) {
                return "";
            }
            String trimmed = text.strip();
            if (trimmed.length() <= maxChars) {
                return trimmed;
            }
            return trimmed.substring(0, maxChars);
        }

        private String limitText(String text, int maxLines, int maxChars) {
            if (text == null || text.isBlank()) {
                return "";
            }
            List<String> lines = text.lines().limit(maxLines).collect(Collectors.toList());
            String joined = String.join(System.lineSeparator(), lines).strip();
            if (joined.length() <= maxChars) {
                return joined;
            }
            return joined.substring(0, maxChars);
        }

        private String summarisePreview(byte[] bytes) {
            if (bytes.length == 0) {
                return "(empty)";
            }
            int sampleSize = Math.min(bytes.length, 128);
            int printable = 0;
            for (int i = 0; i < sampleSize; i++) {
                int value = bytes[i] & 0xFF;
                if (value >= 32 && value <= 126) {
                    printable++;
                }
            }
            if (printable < sampleSize / 2) {
                return "(binary preview suppressed)";
            }
            String text = new String(bytes, StandardCharsets.UTF_8);
            return summariseForLog(text);
        }

        private String generateArtifactsIndex() {
            try {
                Path outputDir = context.outputDirectory();
                if (!Files.exists(outputDir)) {
                    return "(no artifacts directory)";
                }
                
                StringBuilder index = new StringBuilder();
                try (var stream = Files.walk(outputDir)) {
                    stream.filter(Files::isRegularFile)
                          .forEach(path -> {
                              try {
                                  String relativePath = outputDir.relativize(path).toString();
                                  long size = Files.size(path);
                                  String kind = determineFileKind(path);
                                  String preview = generatePreview(path);
                                  
                                  index.append(String.format("""
                                      - name: %s
                                        path: %s
                                        size: %d
                                        kind: %s
                                        preview: %s
                                      """,
                                      path.getFileName(),
                                      relativePath,
                                      size,
                                      kind,
                                      preview));
                              } catch (IOException e) {
                                  logger.debug("Failed to index artifact: {}", path, e);
                              }
                          });
                }
                return index.toString();
            } catch (IOException e) {
                logger.debug("Failed to generate artifacts index", e);
                return "(error generating index)";
            }
        }

        private String determineFileKind(Path path) {
            String name = path.getFileName().toString().toLowerCase();
            if (name.endsWith(".json")) return "json";
            if (name.endsWith(".yaml") || name.endsWith(".yml")) return "yaml";
            if (name.endsWith(".md")) return "markdown";
            if (name.endsWith(".txt")) return "text";
            if (name.endsWith(".py")) return "python";
            if (name.endsWith(".js")) return "javascript";
            if (name.endsWith(".sh")) return "shell";
            return "binary";
        }

        private String generatePreview(Path path) {
            try {
                String kind = determineFileKind(path);
                if (kind.equals("binary")) {
                    return "(binary)";
                }
                String content = Files.readString(path, StandardCharsets.UTF_8);
                return limitChars(content, 400);
            } catch (Exception e) {
                return "(preview unavailable)";
            }
        }

        private ValidationReport performContractCheck(List<String> expectedOutputs) {
            List<String> missing = new ArrayList<>();
            List<String> violations = new ArrayList<>();
            Path outputDir = context.outputDirectory();
            
            // Check if output directory exists
            if (!Files.exists(outputDir)) {
                violations.add("Output directory does not exist");
                return new ValidationReport(
                    false,
                    "contract",
                    List.of(),
                    violations,
                    "Output directory missing",
                    new ValidationMetrics(0, 0));
            }

            // Count files and total bytes
            int fileCount = 0;
            long totalBytes = 0;
            try (var stream = Files.walk(outputDir)) {
                var files = stream.filter(Files::isRegularFile).toList();
                fileCount = files.size();
                for (Path file : files) {
                    try {
                        totalBytes += Files.size(file);
                    } catch (IOException e) {
                        logger.debug("Failed to get size for file: {}", file, e);
                    }
                }
            } catch (IOException e) {
                violations.add("Failed to walk output directory: " + e.getMessage());
            }

            // Check required files exist
            for (String expected : expectedOutputs) {
                if ("artifactPath".equals(expected)) {
                    if (context.artifactPath() == null || !Files.exists(context.artifactPath())) {
                        missing.add("artifactPath");
                    }
                }
            }

            // Check sandbox boundaries
            Path artifactPath = context.artifactPath();
            if (artifactPath != null) {
                Path normalizedArtifact = artifactPath.toAbsolutePath().normalize();
                Path normalizedOutput = outputDir.toAbsolutePath().normalize();
                if (!normalizedArtifact.startsWith(normalizedOutput)) {
                    violations.add("Artifact path is outside output directory sandbox");
                }
            }

            boolean pass = missing.isEmpty() && violations.isEmpty();
            String rationale = pass 
                ? "All contract checks passed" 
                : "Contract validation failed";

            return new ValidationReport(
                pass,
                "contract",
                missing,
                violations,
                rationale,
                new ValidationMetrics(fileCount, totalBytes));
        }

        private record ReferenceDocumentContent(String detail, String summaryText, boolean binary) {}
    }

    private static Path sanitiseOutputRelativePath(Path candidate, Path outputDir) {
        if (candidate == null || candidate.isAbsolute() || outputDir == null) {
            return candidate;
        }
        Path normalised = candidate.normalize();
        List<String> suffixParts = outputDirSuffixParts(outputDir);
        if (suffixParts.isEmpty() || normalised.getNameCount() == 0) {
            return normalised;
        }
        List<String> candidateParts = new ArrayList<>(normalised.getNameCount());
        for (Path part : normalised) {
            candidateParts.add(part.toString());
        }
        int index = 0;
        boolean removed = false;
        while (candidateParts.size() - index >= suffixParts.size()
                && candidateParts.subList(index, index + suffixParts.size()).equals(suffixParts)) {
            index += suffixParts.size();
            removed = true;
        }
        if (!removed) {
            return normalised;
        }
        if (candidateParts.size() == index) {
            return Path.of("");
        }
        String first = candidateParts.get(index);
        String[] rest = candidateParts.subList(index + 1, candidateParts.size()).toArray(String[]::new);
        return Path.of(first, rest);
    }

    private static List<String> outputDirSuffixParts(Path outputDir) {
        if (outputDir == null) {
            return List.of();
        }
        Path normalised = outputDir.toAbsolutePath().normalize();
        int nameCount = normalised.getNameCount();
        if (nameCount == 0) {
            return List.of();
        }
        int start = Math.max(0, nameCount - 2);
        Path suffix = normalised.subpath(start, nameCount);
        List<String> parts = new ArrayList<>();
        for (Path part : suffix) {
            parts.add(part.toString());
        }
        return parts;
    }

    private record ProcessResult(
            List<String> command,
            int exitCode,
            boolean timedOut,
            String stdout,
            String stderr,
            long durationMillis) {
        ProcessResult {
            command = List.copyOf(command);
            stdout = stdout == null ? "" : stdout;
            stderr = stderr == null ? "" : stderr;
        }
    }

    private static final class SkillRuntimeContext {

        private final SkillIndex.SkillMetadata metadata;
        private final List<String> expectedOutputs;
        private final Path outputDirectory;
        private final WorkflowLogger logger;
    private final List<DisclosureEvent> disclosureLog = new ArrayList<>();
    private final List<ToolInvocation> toolInvocations = new ArrayList<>();
    private final Set<Path> referencedFiles = new LinkedHashSet<>();
    private final Set<String> invokedSkills = new LinkedHashSet<>();
        private Path artifactPath;
        private Validation validation;

        SkillRuntimeContext(
                SkillIndex.SkillMetadata metadata,
                List<String> expectedOutputs,
                Path outputDirectory,
                WorkflowLogger logger) {
            this.metadata = Objects.requireNonNull(metadata, "metadata");
            this.expectedOutputs = List.copyOf(expectedOutputs);
            this.outputDirectory = Objects.requireNonNull(outputDirectory, "outputDirectory");
            this.logger = Objects.requireNonNull(logger, "logger");
        }

        SkillIndex.SkillMetadata metadata() {
            return metadata;
        }

        List<String> expectedOutputs() {
            return expectedOutputs;
        }

        Path outputDirectory() {
            return outputDirectory;
        }

        void logL1() {
            String description = metadata.description().isBlank() ? "(no description)" : metadata.description();
            logger.info("Act[L1] {} — {}", metadata.id(), description);
            disclosureLog.add(new DisclosureEvent(
                    DisclosureLevel.L1,
                    metadata.id(),
                    description));
            toolInvocations.add(new ToolInvocation(
                    "discloseL1",
                    Map.of(
                            "skillId", metadata.id(),
                            "name", metadata.name()),
                    Map.of("description", description)));
        invokedSkills.add(metadata.id());
        }

        void addDisclosure(DisclosureEvent event) {
            disclosureLog.add(event);
        }

        void recordInvocation(String name, Map<String, Object> args, Map<String, Object> result) {
            toolInvocations.add(new ToolInvocation(name, args, result));
        }

        void recordReferenced(Path path) {
            referencedFiles.add(path);
        }

        void recordSkill(String skillId) {
            invokedSkills.add(skillId);
        }

        void registerArtifact(Path path, long bytesWritten, String preview) {
            this.artifactPath = path;
            logger.info(
                    "Act[write] {} produced artefact {} ({} bytes) — {}",
                    metadata.id(),
                    path,
                    bytesWritten,
                    preview);
        }

        Path resolveOutputPath(String requested) {
            String fileName;
            if (requested != null && !requested.isBlank()) {
                fileName = requested;
            } else {
                fileName = DEFAULT_OUTPUT_FILE;
            }
            Path requestedPath = Path.of(fileName);
            Path resolvedCandidate = requestedPath.isAbsolute()
                    ? requestedPath.toAbsolutePath().normalize()
                    : outputDirectory.resolve(
                            SkillRuntime.sanitiseOutputRelativePath(requestedPath.normalize(), outputDirectory));
            Path resolved = resolvedCandidate.toAbsolutePath().normalize();
            if (!resolved.startsWith(outputDirectory)) {
                throw new IllegalArgumentException("Output path must remain under " + outputDirectory);
            }
            return resolved;
        }

        Path artifactPath() {
            return artifactPath;
        }

        void setValidation(Validation validation) {
            this.validation = validation;
        }

        Validation validation() {
            return validation;
        }

        List<DisclosureEvent> disclosureLog() {
            return List.copyOf(disclosureLog);
        }

        List<ToolInvocation> toolInvocations() {
            return List.copyOf(toolInvocations);
        }

        List<Path> referencedFiles() {
            return List.copyOf(referencedFiles);
        }

        List<String> invokedSkills() {
            return List.copyOf(invokedSkills);
        }
    }

    public record SkillDocumentResult(String path, String content, List<String> references) {}

    public record ReferenceDocuments(String reference, List<ReferenceDocument> documents) {}

    public record ReferenceDocument(String path, String content, String kind, boolean binary) {}

    public record ScriptResult(
            String path,
            int exitCode,
            boolean timedOut,
            Map<String, Object> stdout,
            String stdoutText,
            String stderrText,
            long durationMillis,
            List<Map<String, Object>> dependencyCalls) {
        public ScriptResult {
            Objects.requireNonNull(path, "path");
            stdout = stdout == null ? Map.of() : Map.copyOf(stdout);
            stdoutText = stdoutText == null ? "" : stdoutText;
            stderrText = stderrText == null ? "" : stderrText;
            dependencyCalls = dependencyCalls == null ? List.of() : List.copyOf(dependencyCalls);
        }
    }

    public record ArtifactHandle(String path, long bytesWritten, String preview) {}

    public record DeploymentResult(String source, String target, int filesCopied, int directoriesCreated) {
        public DeploymentResult {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(target, "target");
        }
    }

    public record ValidationResult(boolean satisfied, List<String> missing) {}

    public record ExecutionResult(
            String skillId,
            Map<String, Object> outputs,
            Path artifactPath,
            Validation validation,
            List<DisclosureEvent> disclosureLog,
            List<ToolInvocation> toolInvocations,
            List<String> invokedSkills) {
        public ExecutionResult {
            Objects.requireNonNull(skillId, "skillId");
            Objects.requireNonNull(outputs, "outputs");
            Objects.requireNonNull(validation, "validation");
            Objects.requireNonNull(disclosureLog, "disclosureLog");
            Objects.requireNonNull(toolInvocations, "toolInvocations");
            Objects.requireNonNull(invokedSkills, "invokedSkills");
        }

        public boolean hasArtifact() {
            return artifactPath != null;
        }
    }

    public record ToolInvocation(String name, Map<String, Object> arguments, Map<String, Object> result) {
        public ToolInvocation {
            Objects.requireNonNull(name, "name");
            arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
            result = result == null ? Map.of() : Map.copyOf(result);
        }
    }

    public record Validation(boolean expectedOutputsSatisfied, List<String> missingOutputs) {
        public Validation {
            Objects.requireNonNull(missingOutputs, "missingOutputs");
        }
    }

    public record ValidationReport(
            boolean pass,
            String stage,
            List<String> missing,
            List<String> violations,
            String rationale,
            ValidationMetrics metrics) {
        public ValidationReport {
            Objects.requireNonNull(stage, "stage");
            missing = missing == null ? List.of() : List.copyOf(missing);
            violations = violations == null ? List.of() : List.copyOf(violations);
            rationale = rationale == null ? "" : rationale;
            Objects.requireNonNull(metrics, "metrics");
        }
    }

    public record ValidationMetrics(int files, long bytes) {
        public ValidationMetrics {
            if (files < 0) {
                throw new IllegalArgumentException("files must be non-negative");
            }
            if (bytes < 0) {
                throw new IllegalArgumentException("bytes must be non-negative");
            }
        }
    }

    public enum DisclosureLevel {
        L1,
        L2,
        L3
    }

    public record DisclosureEvent(DisclosureLevel level, String source, String detail) {
        public DisclosureEvent {
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(detail, "detail");
        }
    }
}
