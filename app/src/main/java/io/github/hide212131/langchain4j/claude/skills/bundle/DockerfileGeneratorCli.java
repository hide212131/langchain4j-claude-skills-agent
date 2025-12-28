package io.github.hide212131.langchain4j.claude.skills.bundle;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

@Command(name = "generate-dockerfiles", description = "skill-deps.yaml から Dockerfile を生成します。", mixinStandardHelpOptions = true)
@SuppressWarnings("checkstyle:LineLength")
public final class DockerfileGeneratorCli implements Callable<Integer> {

    private static final int EXIT_FAILURE = 1;

    @Spec
    private CommandSpec spec;

    @SuppressWarnings("PMD.ImmutableField")
    @Option(names = "--skills-root", paramLabel = "DIR", description = "SKILL.md 探索ルート (default: build/skills)")
    private Path skillsRoot = Path.of("build/skills");

    @SuppressWarnings("PMD.ImmutableField")
    @Option(names = "--template", paramLabel = "FILE", description = "テンプレート Dockerfile (default: run-env/template/Dockerfile)")
    private Path templatePath = Path.of("run-env/template/Dockerfile");

    @SuppressWarnings("PMD.UnnecessaryConstructor")
    public DockerfileGeneratorCli() {
    }

    @Override
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public Integer call() {
        try {
            DockerfileGenerator generator = new DockerfileGenerator();
            List<DockerfileGenerator.DockerfileSpec> specs = generator.generate(skillsRoot, templatePath);
            spec.commandLine().getOut().printf("生成完了: dockerfiles=%d%n", specs.size());
            spec.commandLine().getOut().flush();
            return 0;
        } catch (RuntimeException ex) {
            spec.commandLine().getErr().println("Dockerfile 生成に失敗しました: " + ex.getMessage());
            spec.commandLine().getErr().flush();
            return EXIT_FAILURE;
        }
    }
}
