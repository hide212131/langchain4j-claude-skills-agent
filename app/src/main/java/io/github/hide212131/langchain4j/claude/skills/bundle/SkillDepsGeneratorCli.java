package io.github.hide212131.langchain4j.claude.skills.bundle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

@Command(name = "generate-skill-deps", description = "SKILL.md から skill-deps.yaml を生成します。", mixinStandardHelpOptions = true)
@SuppressWarnings("checkstyle:LineLength")
public final class SkillDepsGeneratorCli implements Callable<Integer> {

    private static final int EXIT_FAILURE = 1;
    private static final String SKILL_FILE_NAME = "SKILL.md";
    private static final String RUNTIME_DIR = ".skill-runtime";
    private static final String OUTPUT_FILE = "skill-deps.yaml";

    @Spec
    private CommandSpec spec;

    @SuppressWarnings("PMD.ImmutableField")
    @Option(names = "--skills-root", paramLabel = "DIR", description = "SKILL.md 探索ルート (default: build/skills)")
    private Path skillsRoot = Path.of("build/skills");

    @SuppressWarnings("PMD.ImmutableField")
    @Option(names = "--skill-path", paramLabel = "FILE", description = "単一の SKILL.md を指定して再生成する")
    private Path skillPath;

    @SuppressWarnings("PMD.UnnecessaryConstructor")
    public SkillDepsGeneratorCli() {
    }

    @Override
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public Integer call() {
        try {
            List<Path> skillFiles = resolveSkillFiles();
            if (skillFiles.isEmpty()) {
                spec.commandLine().getErr().println("SKILL.md が見つかりませんでした: " + skillsRoot);
                spec.commandLine().getErr().flush();
                return EXIT_FAILURE;
            }
            DependencyDeclarationParser parser = new DependencyDeclarationParser();
            SkillDepsWriter writer = new SkillDepsWriter();
            for (Path skillFile : skillFiles) {
                SkillDependency dependency = parser.parse(skillFile);
                Path runtimeDir = skillFile.getParent().resolve(RUNTIME_DIR);
                Path outputPath = runtimeDir.resolve(OUTPUT_FILE);
                writer.write(dependency, outputPath);
            }
            spec.commandLine().getOut().printf("生成完了: skills=%d%n", skillFiles.size());
            spec.commandLine().getOut().flush();
            return 0;
        } catch (RuntimeException ex) {
            spec.commandLine().getErr().println("生成に失敗しました: " + ex.getMessage());
            spec.commandLine().getErr().flush();
            return EXIT_FAILURE;
        }
    }

    private List<Path> resolveSkillFiles() {
        if (skillPath != null) {
            if (!Files.exists(skillPath)) {
                throw new IllegalStateException("指定された SKILL.md が見つかりません: " + skillPath);
            }
            return List.of(skillPath);
        }
        return collectSkillFiles(skillsRoot);
    }

    private List<Path> collectSkillFiles(Path root) {
        if (!Files.exists(root)) {
            throw new IllegalStateException("探索ルートが見つかりません: " + root);
        }
        try (java.util.stream.Stream<Path> stream = Files.walk(root)) {
            return stream.filter(path -> SKILL_FILE_NAME.equals(path.getFileName().toString())).toList();
        } catch (IOException ex) {
            throw new IllegalStateException("SKILL.md の探索に失敗しました: " + root, ex);
        }
    }
}
