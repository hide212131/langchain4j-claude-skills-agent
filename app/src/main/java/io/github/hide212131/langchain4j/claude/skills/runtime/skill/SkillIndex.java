package io.github.hide212131.langchain4j.claude.skills.runtime.skill;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Index of skills discovered under a root directory.
 * <p>
 * The index retains both metadata and the physical location of each skill in order to support
 * reference resolution without relying on hard-coded directory names (e.g. {@code resources/}).
 */
public final class SkillIndex {

    private final Path skillsRoot;
    private final Map<String, SkillMetadata> skills;

    public SkillIndex() {
        this(Path.of("").toAbsolutePath().normalize(), Collections.emptyMap());
    }

    public SkillIndex(Map<String, SkillMetadata> skills) {
        this(Path.of("").toAbsolutePath().normalize(), skills);
    }

    public SkillIndex(Path skillsRoot, Map<String, SkillMetadata> skills) {
        Objects.requireNonNull(skillsRoot, "skillsRoot");
        Objects.requireNonNull(skills, "skills");
        this.skillsRoot = skillsRoot.toAbsolutePath().normalize();
        this.skills = Collections.unmodifiableMap(new HashMap<>(skills));
    }

    public Path skillsRoot() {
        return skillsRoot;
    }

    public Map<String, SkillMetadata> skills() {
        return skills;
    }

    public Optional<SkillMetadata> find(String id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(skills.get(id));
    }

    /**
     * Resolve a list of file system paths referenced by a skill using relative links, explicit paths
     * (prefixed with the skill id) or glob expressions.
     *
     * @param skillId   the identifier of the skill
     * @param reference relative link, explicit path or glob
     * @return list of resolved paths (absolute and normalised)
     * @throws IllegalArgumentException if the skill is unknown or no resources can be resolved
     */
    public List<Path> resolveReferences(String skillId, String reference) {
        SkillMetadata metadata = find(skillId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown skill: " + skillId));
        String normalizedReference = Objects.requireNonNull(reference, "reference").trim();
        if (normalizedReference.isEmpty()) {
            throw new IllegalArgumentException("reference must not be blank");
        }

        Set<Path> matches = new LinkedHashSet<>();
        Set<String> variants = expandReferenceVariants(metadata, normalizedReference);
        for (String variant : variants) {
            if (isGlobPattern(variant)) {
                matches.addAll(resolveGlob(metadata, variant));
            } else {
                resolveFromBase(metadata.skillRoot(), variant).ifPresent(matches::add);
                resolveFromBase(skillsRoot, variant).ifPresent(matches::add);
            }
        }

        if (matches.isEmpty()) {
            throw new IllegalArgumentException(
                    "No resources matched '%s' for skill '%s'".formatted(reference, skillId));
        }

        return matches.stream()
                .map(Path::toAbsolutePath)
                .map(Path::normalize)
                .collect(Collectors.toUnmodifiableList());
    }

    private Set<String> expandReferenceVariants(SkillMetadata metadata, String reference) {
        Set<String> variants = new LinkedHashSet<>();
        variants.add(reference);

        String skillId = metadata.id().replace('\\', '/');
        if (reference.startsWith("./")) {
            variants.add(reference.substring(2));
        }
        if (reference.startsWith("/")) {
            String trimmed = reference.substring(1);
            variants.add(trimmed);
            if (trimmed.startsWith(skillId + "/")) {
                variants.add(trimmed.substring(skillId.length() + 1));
            }
        }
        if (reference.startsWith(skillId + "/")) {
            variants.add(reference.substring(skillId.length() + 1));
        }

        return variants;
    }

    private boolean isGlobPattern(String reference) {
        return reference.contains("*")
                || reference.contains("?")
                || reference.contains("{")
                || reference.contains("[");
    }

    private List<Path> resolveGlob(SkillMetadata metadata, String pattern) {
        Set<Path> results = new LinkedHashSet<>();
        results.addAll(resolveGlobUnderBase(metadata.skillRoot(), pattern));
        results.addAll(resolveGlobUnderBase(skillsRoot, pattern));
        return new ArrayList<>(results);
    }

    private List<Path> resolveGlobUnderBase(Path base, String pattern) {
        Path normalisedBase = base.toAbsolutePath().normalize();
        String adaptedPattern = adaptPattern(pattern);
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + adaptedPattern);
        try (var stream = Files.walk(normalisedBase)) {
            return stream
                    .filter(path -> !path.equals(normalisedBase))
                    .filter(path -> matcher.matches(normalisedBase.relativize(path)))
                    .map(Path::toAbsolutePath)
                    .map(Path::normalize)
                    .collect(Collectors.toCollection(ArrayList::new));
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to resolve glob pattern '%s' under '%s'".formatted(pattern, base), e);
        }
    }

    private Optional<Path> resolveFromBase(Path base, String candidate) {
        Path candidatePath = Path.of(candidate);
        Path resolved = candidatePath.isAbsolute()
                ? candidatePath.normalize()
                : base.toAbsolutePath().normalize().resolve(candidatePath).normalize();

        if (!resolved.startsWith(base.toAbsolutePath().normalize())) {
            return Optional.empty();
        }
        if (!Files.exists(resolved)) {
            return Optional.empty();
        }
        return Optional.of(resolved.toAbsolutePath().normalize());
    }

    private String adaptPattern(String pattern) {
        String separator = FileSystems.getDefault().getSeparator();
        if ("/".equals(separator)) {
            return pattern;
        }
        return pattern.replace("/", separator);
    }

    public record SkillMetadata(
            String id,
            String name,
            String description,
            List<String> keywords,
            List<String> warnings,
            Path skillRoot) {

        public SkillMetadata {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(keywords, "keywords");
            Objects.requireNonNull(warnings, "warnings");
            Objects.requireNonNull(skillRoot, "skillRoot");
            keywords = List.copyOf(keywords);
            warnings = List.copyOf(warnings);
            skillRoot = skillRoot.toAbsolutePath().normalize();
        }
    }
}
