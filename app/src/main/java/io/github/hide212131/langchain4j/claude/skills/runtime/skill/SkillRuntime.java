package io.github.hide212131.langchain4j.claude.skills.runtime.skill;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.github.hide212131.langchain4j.claude.skills.infra.logging.WorkflowLogger;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class SkillRuntime {

    private static final String DEFAULT_OUTPUT_FILE = "deck.pptx";
    private static final Pattern LOCAL_REFERENCE_PATTERN =
            Pattern.compile("\\[[^\\]]*]\\((?!https?://)([^)]+)\\)");

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
        List<String> expectedOutputs = resolveExpectedOutputs(safeInputs);

    SkillRuntimeContext context = new SkillRuntimeContext(metadata, expectedOutputs, outputDirectory, logger);
    context.logL1();
    SkillToolbox toolbox = new SkillToolbox(context);

    String prompt = buildPrompt(metadata, safeInputs, expectedOutputs);
    String rawResponse = orchestrator.run(toolbox, metadata, safeInputs, expectedOutputs, prompt);

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
                Expected Outputs: %s

                Use the available tools to gather instructions and source material.
                Guidelines:
                - Call readSkillMd before performing other actions.
                - Use readRef for any additional resources referenced in the skill.
                - Generate artefacts with writeArtifact (paths are relative to build/out by default).
                - Call validateExpectedOutputs before finishing.
                - When you are done, reply with key=value lines containing at least:
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

    interface SkillActAgent {
        @SystemMessage("""
                You are the Act runtime for a single skill.
                Follow the user's goal, use the available tools responsibly, and call validateExpectedOutputs before finishing.
                """)
        String run(@UserMessage String prompt);
    }

    public interface Toolbox {
        SkillDocumentResult readSkillMd(String skillId);

        ReferenceDocuments readReference(String skillId, String reference);

        ArtifactHandle writeArtifact(String skillId, String relativePath, String content, String base64Content);

        ValidationResult validateExpectedOutputs(List<String> expected, Map<String, Object> observed);
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
            SkillActAgent agent = AiServices.builder(SkillActAgent.class)
                    .chatModel(chatModel)
                    .tools(toolbox)
                    .build();
            return agent.run(prompt);
        }
    }

    private final class SkillToolbox implements Toolbox {

        private final SkillRuntimeContext context;

        SkillToolbox(SkillRuntimeContext context) {
            this.context = context;
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
                List<Path> resolved = skillIndex.resolveReferences(metadata.id(), reference);
                List<ReferenceDocument> documents = new ArrayList<>();
                for (Path path : resolved) {
                    String detail;
                    String kind = Files.isDirectory(path) ? "directory" : "file";
                    if (Files.isDirectory(path)) {
                        detail = "(directory)";
                    } else {
                        detail = Files.readString(path, StandardCharsets.UTF_8);
                    }
                    logger.info("Act[L3] {} -> {} — {}", metadata.id(), path, summariseForLog(detail));
                    context.addDisclosure(new DisclosureEvent(
                            DisclosureLevel.L3, path.toString(), summariseForLog(detail)));
                    context.recordReferenced(path);
                    documents.add(new ReferenceDocument(path.toString(), detail, kind));
                }
                context.recordInvocation(
                        "readRef",
                        Map.of("skillId", skillId, "reference", reference),
                        Map.of("resolvedCount", documents.size()));
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

        @Tool(name = "writeArtifact", returnBehavior = ReturnBehavior.TO_LLM)
    @Override
    public ArtifactHandle writeArtifact(
                @P("skillId") String skillId,
                @P(value = "relativePath", required = false) String relativePath,
                @P(value = "content", required = false) String content,
                @P(value = "base64Content", required = false) String base64Content) {
            SkillIndex.SkillMetadata metadata = context.metadata();
            validateSkillId(skillId, metadata.id());
            Path outputPath = context.resolveOutputPath(relativePath);
            byte[] bytes;
            if (base64Content != null && !base64Content.isBlank()) {
                bytes = Base64.getDecoder().decode(base64Content);
            } else {
                String text = content == null ? "" : content;
                bytes = text.getBytes(StandardCharsets.UTF_8);
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
                            skillId),
                    result);
            context.recordSkill(metadata.id());
            return new ArtifactHandle(outputPath.toString(), bytes.length, preview);
        }

        @Tool(name = "validateExpectedOutputs", returnBehavior = ReturnBehavior.TO_LLM)
    @Override
    public ValidationResult validateExpectedOutputs(
                @P(value = "expected", required = false) List<String> expected,
                @P(value = "observed", required = false) Map<String, Object> observed) {
            List<String> expectedList = (expected == null || expected.isEmpty())
                    ? context.expectedOutputs()
                    : expected;
            Map<String, Object> observedMap = observed == null
                    ? Map.of()
                    : Map.copyOf(observed);
            if (context.artifactPath() != null) {
                Map<String, Object> merged = new LinkedHashMap<>(observedMap);
                merged.putIfAbsent("artifactPath", context.artifactPath().toString());
                observedMap = Map.copyOf(merged);
            }
            Validation validation = SkillRuntime.this.validateOutputs(
                    expectedList, observedMap, context.artifactPath());
            context.setValidation(validation);
            context.recordInvocation(
                    "validateExpectedOutputs",
                    Map.of("expected", expectedList),
                    Map.of(
                            "satisfied", validation.expectedOutputsSatisfied(),
                            "missing", validation.missingOutputs()));
        context.recordSkill(context.metadata().id());
            return new ValidationResult(validation.expectedOutputsSatisfied(), validation.missingOutputs());
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
            Path resolved = outputDirectory.resolve(fileName).toAbsolutePath().normalize();
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

    public record ReferenceDocument(String path, String content, String kind) {}

    public record ArtifactHandle(String path, long bytesWritten, String preview) {}

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
