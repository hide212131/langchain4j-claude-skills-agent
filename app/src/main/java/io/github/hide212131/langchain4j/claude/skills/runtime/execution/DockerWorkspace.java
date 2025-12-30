package io.github.hide212131.langchain4j.claude.skills.runtime.execution;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

final class DockerWorkspace {

    private static final String WORKSPACE_PATH = "/workspace";

    private final Path workspaceRoot;

    private DockerWorkspace(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot.toAbsolutePath();
    }

    static DockerWorkspace createTemporary() {
        try {
            return new DockerWorkspace(Files.createTempDirectory("skill-runtime-"));
        } catch (IOException ex) {
            throw new IllegalStateException("作業用ディレクトリの作成に失敗しました", ex);
        }
    }

    String uploadFile(Path localPath) {
        Objects.requireNonNull(localPath, "localPath");
        if (!Files.exists(localPath)) {
            throw new IllegalArgumentException("アップロード対象ファイルが存在しません: " + localPath);
        }
        if (!Files.isRegularFile(localPath)) {
            throw new IllegalArgumentException("アップロード対象がファイルではありません: " + localPath);
        }
        try {
            Files.createDirectories(workspaceRoot);
            Path destination = workspaceRoot.resolve(localPath.getFileName().toString());
            Files.copy(localPath, destination, StandardCopyOption.REPLACE_EXISTING);
            return WORKSPACE_PATH + "/" + destination.getFileName().toString();
        } catch (IOException ex) {
            throw new IllegalStateException("ファイルのアップロードに失敗しました: " + localPath, ex);
        }
    }

    byte[] downloadFile(String remotePath) {
        Objects.requireNonNull(remotePath, "remotePath");
        Path localPath = resolveWorkspacePath(remotePath);
        if (!Files.exists(localPath)) {
            throw new IllegalArgumentException("ダウンロード対象ファイルが存在しません: " + remotePath);
        }
        try {
            return Files.readAllBytes(localPath);
        } catch (IOException ex) {
            throw new IllegalStateException("ファイルのダウンロードに失敗しました: " + remotePath, ex);
        }
    }

    List<String> listFiles(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("pattern は空にできません");
        }
        if (!Files.exists(workspaceRoot)) {
            return List.of();
        }
        PathMatcher matcher = workspaceRoot.getFileSystem().getPathMatcher("glob:" + pattern);
        List<String> results = new ArrayList<>();
        try {
            Files.walk(workspaceRoot).filter(Files::isRegularFile).forEach(path -> {
                Path relative = workspaceRoot.relativize(path);
                if (matcher.matches(relative)) {
                    String normalized = relative.toString().replace('\\', '/');
                    results.add(WORKSPACE_PATH + "/" + normalized);
                }
            });
        } catch (IOException ex) {
            throw new IllegalStateException("ファイル一覧の取得に失敗しました: " + pattern, ex);
        }
        results.sort(Comparator.naturalOrder());
        return List.copyOf(results);
    }

    void deleteWorkspace() {
        if (!Files.exists(workspaceRoot)) {
            return;
        }
        try {
            Files.walkFileTree(workspaceRoot, new SimpleFileVisitor<>() {
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
            throw new IllegalStateException("作業用ディレクトリの削除に失敗しました: " + workspaceRoot, ex);
        }
    }

    Path workspaceRootPath() {
        return workspaceRoot;
    }

    String containerPath() {
        return WORKSPACE_PATH;
    }

    private Path resolveWorkspacePath(String remotePath) {
        String normalized = remotePath.replace('\\', '/');
        if (!normalized.startsWith(WORKSPACE_PATH + "/")) {
            throw new IllegalArgumentException("想定外のパスが指定されました: " + remotePath);
        }
        String relative = normalized.substring((WORKSPACE_PATH + "/").length());
        Path relativePath = Path.of(relative).normalize();
        if (relativePath.isAbsolute() || relativePath.startsWith("..")) {
            throw new IllegalArgumentException("想定外のパスが指定されました: " + remotePath);
        }
        return workspaceRoot.resolve(relativePath);
    }
}
