package io.github.hide212131.langchain4j.claude.skills.runtime.planning;

import dev.langchain4j.agent.tool.Tool;
import io.github.hide212131.langchain4j.claude.skills.runtime.execution.ExecutionResult;
import io.github.hide212131.langchain4j.claude.skills.runtime.execution.CodeExecutionEnvironment;
import io.github.hide212131.langchain4j.claude.skills.runtime.execution.CodeExecutionEnvironmentFactory;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * 実行環境の状態確認を行うツール。
 */
public final class ExecutionEnvironmentTool {

    private static final String WORKSPACE_PREFIX = "/workspace/";

    private final CodeExecutionEnvironmentFactory factory;
    private final Path skillMdPath;

    public ExecutionEnvironmentTool(CodeExecutionEnvironmentFactory factory, Path skillMdPath) {
        this.factory = Objects.requireNonNull(factory, "factory");
        this.skillMdPath = Objects.requireNonNull(skillMdPath, "skillMdPath");
    }

    @Tool("リモート実行環境のファイル一覧を取得します。pattern は glob 形式です。")
    public RemoteFileList listRemoteFiles(String pattern) {
        try (CodeExecutionEnvironment environment = factory.create(skillMdPath)) {
            return new RemoteFileList(pattern, environment.listFiles(pattern));
        }
    }

    @Tool("リモート実行環境に指定ファイルが存在するか確認します。")
    public RemoteFileCheck existsFile(String remotePath) {
        String normalized = normalizeRemotePath(remotePath);
        String relative = normalized.substring(WORKSPACE_PREFIX.length());
        RemoteFileList matches = listRemoteFiles(relative);
        return new RemoteFileCheck(normalized, matches.paths().contains(normalized));
    }

    public String uploadFile(Path localPath) {
        Objects.requireNonNull(localPath, "localPath");
        try (CodeExecutionEnvironment environment = factory.create(skillMdPath)) {
            return environment.uploadFile(localPath);
        }
    }

    public byte[] downloadFile(String remotePath) {
        Objects.requireNonNull(remotePath, "remotePath");
        try (CodeExecutionEnvironment environment = factory.create(skillMdPath)) {
            return environment.downloadFile(remotePath);
        }
    }

    @Tool("リモート実行環境でコマンドを実行します。")
    public ExecutionResult executeCommand(String command) {
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("command は空にできません");
        }
        try (CodeExecutionEnvironment environment = factory.create(skillMdPath)) {
            return environment.executeCommand(command);
        }
    }

    private String normalizeRemotePath(String remotePath) {
        if (remotePath == null || remotePath.isBlank()) {
            throw new IllegalArgumentException("remotePath は空にできません");
        }
        String normalized = remotePath.replace('\\', '/').trim();
        if (!normalized.startsWith(WORKSPACE_PREFIX)) {
            throw new IllegalArgumentException("remotePath は /workspace/ 配下に限定してください: " + remotePath);
        }
        return normalized;
    }

    public record RemoteFileList(String pattern, List<String> paths) {
    }

    public record RemoteFileCheck(String path, boolean exists) {
    }
}
