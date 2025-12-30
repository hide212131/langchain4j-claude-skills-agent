package io.github.hide212131.langchain4j.claude.skills.bundle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

/**
 * スキルの Docker イメージをビルドする CLI コマンド
 */
@Command(name = "build-skill-images", description = "生成された Dockerfile をビルドし、規約ベースのタグを付与します。", mixinStandardHelpOptions = true)
@SuppressWarnings("checkstyle:LineLength")
public final class SkillImageBuilderCli implements Callable<Integer> {

    private static final int EXIT_FAILURE = 1;
    private static final String RUNTIME_DIR = ".skill-runtime";
    private static final String DOCKERFILE = "Dockerfile";

    @Spec
    private CommandSpec spec;

    @SuppressWarnings("PMD.ImmutableField")
    @Option(names = "--skills-root", paramLabel = "DIR", description = "SKILL.md 探索ルート (default: build/skills)")
    private Path skillsRoot = Path.of("build/skills");

    @SuppressWarnings("PMD.ImmutableField")
    @Option(names = "--skill-path", paramLabel = "PATH", description = "単一のスキルパスを指定してビルドする（例: anthropics/skills/document-skills/pptx）")
    private String skillPath;

    @SuppressWarnings("PMD.ImmutableField")
    @Option(names = "--project-root", paramLabel = "DIR", description = "プロジェクトルート（Docker ビルドコンテキスト） (default: .)")
    private Path projectRoot = Path.of(".");

    @SuppressWarnings("PMD.UnnecessaryConstructor")
    public SkillImageBuilderCli() {
    }

    @Override
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public Integer call() {
        if (!SkillImageBuilder.isDockerAvailable()) {
            spec.commandLine().getErr().println("エラー: Docker がインストールされていないか、利用できません");
            spec.commandLine().getErr().flush();
            return EXIT_FAILURE;
        }

        try {
            List<SkillBuildTarget> targets = resolveBuildTargets();
            if (targets.isEmpty()) {
                spec.commandLine().getErr().println("ビルド対象が見つかりませんでした");
                spec.commandLine().getErr().flush();
                return EXIT_FAILURE;
            }

            SkillImageBuilder builder = new SkillImageBuilder(projectRoot);
            int successCount = 0;
            int failureCount = 0;

            for (SkillBuildTarget target : targets) {
                spec.commandLine().getOut().printf("ビルド開始: %s%n", target.skillPath);
                spec.commandLine().getOut().flush();

                try {
                    String imageTag = builder.buildImage(target.skillPath, target.dockerfilePath);
                    spec.commandLine().getOut().printf("✓ ビルド成功: %s → %s%n", target.skillPath, imageTag);
                    spec.commandLine().getOut().flush();
                    successCount++;
                } catch (IOException ex) {
                    spec.commandLine().getErr().printf("✗ ビルド失敗: %s (%s)%n", target.skillPath, ex.getMessage());
                    spec.commandLine().getErr().flush();
                    failureCount++;
                }
            }

            spec.commandLine().getOut().printf("%n=== ビルド結果 ===%n");
            spec.commandLine().getOut().printf("成功: %d%n", successCount);
            spec.commandLine().getOut().printf("失敗: %d%n", failureCount);
            spec.commandLine().getOut().printf("合計: %d%n", targets.size());
            spec.commandLine().getOut().flush();

            return failureCount == 0 ? 0 : EXIT_FAILURE;
        } catch (RuntimeException ex) {
            spec.commandLine().getErr().println("ビルドに失敗しました: " + ex.getMessage());
            spec.commandLine().getErr().flush();
            return EXIT_FAILURE;
        }
    }

    private List<SkillBuildTarget> resolveBuildTargets() {
        if (skillPath != null) {
            Path dockerfilePath = skillsRoot.resolve(skillPath).resolve(RUNTIME_DIR).resolve(DOCKERFILE);
            if (!Files.exists(dockerfilePath)) {
                throw new IllegalStateException("Dockerfile が見つかりません: " + dockerfilePath);
            }
            return List.of(new SkillBuildTarget(skillPath, dockerfilePath));
        }
        return collectBuildTargets(skillsRoot);
    }

    private List<SkillBuildTarget> collectBuildTargets(Path root) {
        if (!Files.exists(root)) {
            throw new IllegalStateException("探索ルートが見つかりません: " + root);
        }

        List<SkillBuildTarget> targets = new ArrayList<>();

        try (java.util.stream.Stream<Path> stream = Files.walk(root)) {
            stream.filter(path -> path.endsWith(RUNTIME_DIR + "/" + DOCKERFILE)).forEach(dockerfilePath -> {
                // build/skills/<skillPath>/.skill-runtime/Dockerfile から <skillPath> を抽出
                Path relativePath = root.relativize(dockerfilePath);
                Path skillPathPart = relativePath.getParent().getParent(); // .skill-runtime の2階層上
                String skillPathStr = skillPathPart.toString().replace('\\', '/');
                targets.add(new SkillBuildTarget(skillPathStr, dockerfilePath));
            });
        } catch (IOException ex) {
            throw new IllegalStateException("Dockerfile の探索に失敗しました: " + root, ex);
        }

        return targets;
    }

    private record SkillBuildTarget(String skillPath, Path dockerfilePath) {
    }

    /**
     * メインエントリーポイント
     */
    public static void main(String[] args) {
        int exitCode = new picocli.CommandLine(new SkillImageBuilderCli()).execute(args);
        System.exit(exitCode);
    }
}
