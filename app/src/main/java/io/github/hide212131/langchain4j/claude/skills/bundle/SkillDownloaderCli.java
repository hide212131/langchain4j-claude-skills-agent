package io.github.hide212131.langchain4j.claude.skills.bundle;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

@Command(name = "download-skills", description = "skill-sources.yaml から SKILL.md を取得します。", mixinStandardHelpOptions = true)
@SuppressWarnings("checkstyle:LineLength")
public final class SkillDownloaderCli implements Callable<Integer> {

    private static final int EXIT_FAILURE = 1;

    @Spec
    private CommandSpec spec;

    @SuppressWarnings("PMD.ImmutableField")
    @Option(names = "--config", paramLabel = "FILE", description = "取得元定義ファイル (default: run-env/skill-sources.yaml)")
    private Path configPath = Path.of("run-env/skill-sources.yaml");

    @SuppressWarnings("PMD.ImmutableField")
    @Option(names = "--output", paramLabel = "DIR", description = "SKILL.md 出力先 (default: build/skills)")
    private Path outputRoot = Path.of("build/skills");

    @SuppressWarnings("PMD.UnnecessaryConstructor")
    public SkillDownloaderCli() {
    }

    @Override
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public Integer call() {
        try {
            SkillDownloader downloader = new SkillDownloader();
            SkillDownloader.SkillDownloadReport report = downloader.download(configPath, outputRoot);
            int sources = report.sources().size();
            int skills = report.sources().stream().mapToInt(source -> source.skillsCount()).sum();
            spec.commandLine().getOut().printf("取得完了: sources=%d skills=%d%n", sources, skills);
            spec.commandLine().getOut().flush();
            return 0;
        } catch (RuntimeException ex) {
            spec.commandLine().getErr().println("取得に失敗しました: " + ex.getMessage());
            spec.commandLine().getErr().flush();
            return EXIT_FAILURE;
        }
    }
}
