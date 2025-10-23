package io.github.hide212131.langchain4j.claude.skills.index;

import io.github.hide212131.langchain4j.claude.skills.index.SkillFrontMatterExtractor.SkillFrontMatter;
import io.github.hide212131.langchain4j.claude.skills.index.SkillFrontMatterExtractor.SkillFrontMatterExtraction;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Loads {@link SkillIndexEntry} metadata for {@code SKILL.md} files discovered under a skills root directory.
 */
public class SkillIndexLoader {

    private final SkillFrontMatterExtractor extractor = new SkillFrontMatterExtractor();

    /**
     * Recursively walks the {@code skillsRoot} directory, parsing each {@code SKILL.md} file that is discovered and
     * returning an immutable list of {@link SkillIndexEntry} instances sorted by skill identifier.
     *
     * @param skillsRoot the root directory containing skill folders
     * @param logger     consumer for warning messages (use {@link SkillIndexLogger#NO_OP} when not needed)
     * @return immutable list of skill metadata entries, sorted by {@code skillId}
     * @throws IOException if file traversal fails
     */
    public List<SkillIndexEntry> load(Path skillsRoot, SkillIndexLogger logger) throws IOException {
        Objects.requireNonNull(skillsRoot, "skillsRoot");
        SkillIndexLogger effectiveLogger = logger == null ? SkillIndexLogger.NO_OP : logger;

        if (!Files.exists(skillsRoot)) {
            return List.of();
        }

        List<SkillIndexEntry> entries = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(skillsRoot)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals("SKILL.md"))
                    .sorted()
                    .forEach(skillFile -> {
                        Path skillDir = skillFile.getParent();
                        String skillId = normaliseSkillId(skillsRoot, skillDir);
                        try {
                            SkillIndexEntry entry = loadSingleSkill(skillFile, skillId, effectiveLogger);
                            entries.add(entry);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }

        entries.sort(Comparator.comparing(SkillIndexEntry::skillId));
        return List.copyOf(entries);
    }

    private SkillIndexEntry loadSingleSkill(Path skillFile, String skillId, SkillIndexLogger logger) throws IOException {
        String markdown = Files.readString(skillFile);
        SkillFrontMatterExtraction extraction;
        try {
            extraction = extractor.extract(markdown);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Failed to parse SKILL.md for skill '" + skillId + "': " + ex.getMessage(), ex);
        }

        for (String unsupported : extraction.unsupportedKeys()) {
            logger.warn(skillId, "Unsupported front matter field: " + unsupported);
        }

        SkillFrontMatter frontMatter = extraction.frontMatter();
        Path skillDir = skillFile.getParent();
        List<String> resourceFiles = collectRelativeFiles(skillDir, "resources");
        List<String> scriptFiles = collectRelativeFiles(skillDir, "scripts");

        return new SkillIndexEntry(
                skillId,
                frontMatter.name(),
                frontMatter.description(),
                frontMatter.version(),
                frontMatter.inputs(),
                frontMatter.outputs(),
                frontMatter.keywords(),
                frontMatter.stages(),
                resourceFiles,
                scriptFiles,
                buildSummary(frontMatter));
    }

    private List<String> collectRelativeFiles(Path skillDir, String subdirectory) throws IOException {
        Path targetDir = skillDir.resolve(subdirectory);
        if (!Files.isDirectory(targetDir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(targetDir)) {
            String separator = skillDir.getFileSystem().getSeparator();
            return stream.filter(Files::isRegularFile)
                    .map(path -> skillDir.relativize(path).toString().replace(separator, "/"))
                    .sorted()
                    .collect(Collectors.toUnmodifiableList());
        }
    }

    private static String normaliseSkillId(Path skillsRoot, Path skillDir) {
        Path relative = skillsRoot.relativize(skillDir);
        String id = relative.toString().replace(relative.getFileSystem().getSeparator(), "/");
        if (id.isEmpty()) {
            id = skillDir.getFileName().toString();
        }
        return id;
    }

    private String buildSummary(SkillFrontMatter frontMatter) {
        String trigger = frontMatter.keywords().isEmpty()
                ? "manually invoked"
                : "keywords: " + String.join(", ", frontMatter.keywords());
        String description = ensureSentence(frontMatter.description());
        if (description.isEmpty()) {
            return "%s — Trigger when %s."
                    .formatted(frontMatter.name(), trigger);
        }
        return "%s — %s Trigger when %s."
                .formatted(frontMatter.name(), description, trigger);
    }

    private static String ensureSentence(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.strip();
        if (normalized.isEmpty()) {
            return "";
        }
        char last = normalized.charAt(normalized.length() - 1);
        if (last == '.' || last == '!' || last == '?') {
            return normalized;
        }
        return normalized + ".";
    }
}
