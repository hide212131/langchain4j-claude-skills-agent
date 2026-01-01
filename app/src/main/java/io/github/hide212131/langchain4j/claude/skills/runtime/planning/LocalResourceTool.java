package io.github.hide212131.langchain4j.claude.skills.runtime.planning;

import dev.langchain4j.agent.tool.Tool;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * スキル実行計画のためにローカル資源を参照するツール。
 */
public final class LocalResourceTool {

    private static final int MAX_PREVIEW_CHARS = 4000;
    private final Path baseDir;

    public LocalResourceTool(Path skillMdPath) {
        Objects.requireNonNull(skillMdPath, "skillMdPath");
        Path parent = skillMdPath.toAbsolutePath().getParent();
        if (parent == null) {
            throw new IllegalArgumentException("SKILL.md の親ディレクトリを取得できません: " + skillMdPath);
        }
        this.baseDir = parent.normalize();
    }

    @Tool("ローカルのファイルを読み取り、内容を返します。relativePath は SKILL.md の親ディレクトリ基準で指定します。")
    public LocalResourceContent readFile(String relativePath) {
        Path resolved = resolveRelativePath(relativePath);
        if (!Files.exists(resolved)) {
            throw new IllegalArgumentException("指定ファイルが存在しません: " + relativePath);
        }
        if (!Files.isRegularFile(resolved)) {
            throw new IllegalArgumentException("指定パスがファイルではありません: " + relativePath);
        }
        try {
            String content = Files.readString(resolved, StandardCharsets.UTF_8);
            if (content.length() <= MAX_PREVIEW_CHARS) {
                return new LocalResourceContent(relativePath, content, false);
            }
            String truncated = content.substring(0, MAX_PREVIEW_CHARS) + "...(省略)";
            return new LocalResourceContent(relativePath, truncated, true);
        } catch (IOException ex) {
            throw new IllegalStateException("ファイル読み取りに失敗しました: " + relativePath, ex);
        }
    }

    @Tool("ローカルのファイル一覧を取得します。pattern は glob 形式で指定します。")
    public LocalResourceList listFiles(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("pattern は空にできません");
        }
        if (!Files.exists(baseDir)) {
            return new LocalResourceList(pattern, List.of());
        }
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        List<String> results = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(baseDir)) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                Path relative = baseDir.relativize(path);
                if (matcher.matches(relative)) {
                    results.add(relative.toString().replace('\\', '/'));
                }
            });
        } catch (IOException ex) {
            throw new IllegalStateException("ファイル一覧の取得に失敗しました: " + pattern, ex);
        }
        results.sort(String::compareTo);
        return new LocalResourceList(pattern, List.copyOf(results));
    }

    private Path resolveRelativePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("relativePath は空にできません");
        }
        Path candidate = Path.of(relativePath);
        if (candidate.isAbsolute()) {
            throw new IllegalArgumentException("absolutePath は許可されていません: " + relativePath);
        }
        Path resolved = baseDir.resolve(candidate).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new IllegalArgumentException("SKILL.md 配下以外の参照は許可されていません: " + relativePath);
        }
        return resolved;
    }

    public record LocalResourceContent(String path, String content, boolean truncated) {
    }

    public record LocalResourceList(String pattern, List<String> paths) {
    }
}
