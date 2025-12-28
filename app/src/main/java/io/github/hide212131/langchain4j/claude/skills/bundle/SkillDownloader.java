package io.github.hide212131.langchain4j.claude.skills.bundle;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.logging.Level;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

@SuppressWarnings({ "PMD.AvoidCatchingGenericException", "PMD.AvoidInstantiatingObjectsInLoops" })
public final class SkillDownloader {

    private static final String GIT = "git";
    private static final String LOCK_FILE_NAME = "skill-sources.lock.yaml";
    private static final String SKILL_FILE_NAME = "SKILL.md";
    private static final Logger LOGGER = Logger.getLogger(SkillDownloader.class.getName());

    @SuppressWarnings("PMD.UnnecessaryConstructor")
    public SkillDownloader() {
    }

    public SkillDownloadReport download(Path configPath, Path outputRoot) {
        Objects.requireNonNull(configPath, "configPath");
        Objects.requireNonNull(outputRoot, "outputRoot");
        List<SkillSourceSpec> sources = SkillSourcesConfigLoader.load(configPath);
        return downloadAll(sources, outputRoot);
    }

    public SkillDownloadReport downloadAll(List<SkillSourceSpec> sources, Path outputRoot) {
        Objects.requireNonNull(sources, "sources");
        Objects.requireNonNull(outputRoot, "outputRoot");
        try {
            Files.createDirectories(outputRoot);
        } catch (IOException ex) {
            throw new IllegalStateException("出力ディレクトリの作成に失敗しました: " + outputRoot, ex);
        }
        List<ResolvedSkillSource> resolved = new ArrayList<>();
        for (SkillSourceSpec source : sources) {
            resolved.add(downloadSource(source, outputRoot));
        }
        SkillDownloadReport report = new SkillDownloadReport(resolved);
        writeLockFile(outputRoot.resolve(LOCK_FILE_NAME), report);
        return report;
    }

    private ResolvedSkillSource downloadSource(SkillSourceSpec source, Path outputRoot) {
        Path tempDir;
        try {
            tempDir = Files.createTempDirectory("skill-source-");
        } catch (IOException ex) {
            throw new IllegalStateException("一時ディレクトリの作成に失敗しました。", ex);
        }
        try {
            initRepository(tempDir, source.repository().toString(), source.ref());
            String commitHash = runGit(tempDir, List.of(GIT, "rev-parse", "HEAD")).trim();
            Path sourceRoot = tempDir.resolve(source.sourcePath()).normalize();
            if (!sourceRoot.startsWith(tempDir) || !Files.isDirectory(sourceRoot)) {
                throw new IllegalArgumentException("取得元パスが存在しません: " + sourceRoot);
            }
            List<Path> skillFiles = new ArrayList<>();
            Files.walkFileTree(sourceRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (SKILL_FILE_NAME.equals(file.getFileName().toString())) {
                        skillFiles.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            if (skillFiles.isEmpty()) {
                throw new IllegalStateException("SKILL.md が見つかりませんでした: " + sourceRoot);
            }
            Path destinationRoot = outputRoot.resolve(source.destination()).normalize();
            if (!destinationRoot.startsWith(outputRoot)) {
                throw new IllegalArgumentException("出力先が不正です: " + destinationRoot);
            }
            for (Path file : skillFiles) {
                Path relative = sourceRoot.relativize(file);
                Path target = destinationRoot.resolve(relative);
                Files.createDirectories(target.getParent());
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return new ResolvedSkillSource(source.repository().toString(), source.ref(), commitHash,
                    source.sourcePath(), source.destination(), skillFiles.size());
        } catch (IOException ex) {
            throw new IllegalStateException("SKILL.md の取得に失敗しました。", ex);
        } finally {
            try {
                deleteRecursively(tempDir);
            } catch (RuntimeException ex) {
                if (LOGGER.isLoggable(Level.WARNING)) {
                    LOGGER.warning("一時ディレクトリの削除に失敗しました: " + ex.getMessage());
                }
            }
        }
    }

    private static void initRepository(Path directory, String repository, String ref) {
        runGit(directory, List.of(GIT, "init"));
        runGit(directory, List.of(GIT, "remote", "add", "origin", repository));
        runGit(directory, List.of(GIT, "fetch", "--depth", "1", "origin", ref));
        runGit(directory, List.of(GIT, "checkout", "FETCH_HEAD"));
    }

    private static String runGit(Path workDir, List<String> args) {
        ProcessBuilder builder = new ProcessBuilder(args);
        builder.directory(workDir.toFile());
        builder.environment().put("GIT_TERMINAL_PROMPT", "0");
        builder.redirectErrorStream(false);
        try {
            Process process = builder.start();
            String stdout;
            String stderr;
            try (InputStream stdoutStream = process.getInputStream();
                    InputStream stderrStream = process.getErrorStream()) {
                stdout = new String(stdoutStream.readAllBytes(), StandardCharsets.UTF_8);
                stderr = new String(stderrStream.readAllBytes(), StandardCharsets.UTF_8);
            }
            int exit = process.waitFor();
            if (exit != 0) {
                throw new IllegalStateException("git の実行に失敗しました: " + String.join(" ", args) + " " + stderr);
            }
            return stdout;
        } catch (IOException | InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("git の実行に失敗しました: " + String.join(" ", args), ex);
        }
    }

    private static void writeLockFile(Path lockFile, SkillDownloadReport report) {
        Objects.requireNonNull(lockFile, "lockFile");
        Objects.requireNonNull(report, "report");
        List<Map<String, Object>> entries = new ArrayList<>();
        for (ResolvedSkillSource source : report.sources()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("repository", source.repository());
            entry.put("ref", source.ref());
            entry.put("commit", source.commit());
            entry.put("path", source.sourcePath());
            entry.put("destination", source.destination());
            entry.put("skills", source.skillsCount());
            entries.add(entry);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("sources", entries);
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        Yaml yaml = new Yaml(options);
        try {
            Files.createDirectories(lockFile.getParent());
            Files.writeString(lockFile, yaml.dump(root), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("ロックファイルの書き込みに失敗しました: " + lockFile, ex);
        }
    }

    private static void deleteRecursively(Path target) {
        if (target == null || !Files.exists(target)) {
            return;
        }
        try {
            Files.walkFileTree(target, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ex) {
            throw new IllegalStateException("一時ディレクトリの削除に失敗しました: " + target, ex);
        }
    }

    public record ResolvedSkillSource(String repository, String ref, String commit, String sourcePath,
            String destination, int skillsCount) {
    }

    public record SkillDownloadReport(List<ResolvedSkillSource> sources) {
    }
}
