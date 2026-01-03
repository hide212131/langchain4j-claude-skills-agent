package io.github.hide212131.langchain4j.claude.skills.runtime.planning;

import dev.langchain4j.agent.tool.Tool;
import io.github.hide212131.langchain4j.claude.skills.runtime.SkillLog;
import io.github.hide212131.langchain4j.claude.skills.runtime.execution.ExecutionResult;
import io.github.hide212131.langchain4j.claude.skills.runtime.execution.CodeExecutionEnvironment;
import io.github.hide212131.langchain4j.claude.skills.runtime.execution.CodeExecutionEnvironmentFactory;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.SkillEvent;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.SkillEventMetadata;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.SkillEventPublisher;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.SkillEventType;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.ToolPayload;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * 実行環境の状態確認を行うツール。
 */
public final class ExecutionEnvironmentTool {

    private static final String WORKSPACE_PREFIX = "/workspace/";
    private static final String PHASE_TOOL = "tool";
    private static final String STEP_LIST_REMOTE_FILES = "tool.listRemoteFiles";
    private static final String STEP_EXISTS_FILE = "tool.existsFile";
    private static final String STEP_UPLOAD_FILE = "tool.uploadFile";
    private static final String STEP_DOWNLOAD_FILE = "tool.downloadFile";
    private static final String STEP_EXECUTE_COMMAND = "tool.executeCommand";

    private final CodeExecutionEnvironmentFactory factory;
    private final Path skillMdPath;
    private final SkillLog log;
    private final boolean basicLog;
    private final String runId;
    private final String skillId;
    private final SkillEventPublisher events;

    public ExecutionEnvironmentTool(CodeExecutionEnvironmentFactory factory, Path skillMdPath) {
        this(factory, skillMdPath, null, false, null, null, SkillEventPublisher.noop());
    }

    public ExecutionEnvironmentTool(CodeExecutionEnvironmentFactory factory, Path skillMdPath, SkillLog log,
            boolean basicLog, String runId, String skillId, SkillEventPublisher events) {
        this.factory = Objects.requireNonNull(factory, "factory");
        this.skillMdPath = Objects.requireNonNull(skillMdPath, "skillMdPath");
        this.log = log;
        this.basicLog = basicLog;
        this.runId = runId;
        this.skillId = skillId;
        this.events = Objects.requireNonNull(events, "events");
    }

    @Tool("リモート実行環境のファイル一覧を取得します。pattern は glob 形式です。")
    public RemoteFileList listRemoteFiles(String pattern) {
        String input = "pattern=" + pattern;
        RemoteFileList result;
        try (CodeExecutionEnvironment environment = factory.create(skillMdPath)) {
            result = new RemoteFileList(pattern, environment.listFiles(pattern));
        }
        String output = "paths=" + result.paths();
        logToolInfo(STEP_LIST_REMOTE_FILES, "リモートファイル一覧を取得しました", input, output);
        publishToolEvent(STEP_LIST_REMOTE_FILES, "listRemoteFiles", input, output, null);
        return result;
    }

    @Tool("リモート実行環境に指定ファイルが存在するか確認します。")
    public RemoteFileCheck existsFile(String remotePath) {
        String input = "remotePath=" + remotePath;
        String normalized = normalizeRemotePath(remotePath);
        String relative = normalized.substring(WORKSPACE_PREFIX.length());
        RemoteFileList matches = listRemoteFiles(relative);
        RemoteFileCheck result = new RemoteFileCheck(normalized, matches.paths().contains(normalized));
        String output = "exists=" + result.exists();
        logToolInfo(STEP_EXISTS_FILE, "リモートファイルの存在確認が完了しました", input, output);
        publishToolEvent(STEP_EXISTS_FILE, "existsFile", input, output, null);
        return result;
    }

    public String uploadFile(Path localPath) {
        Objects.requireNonNull(localPath, "localPath");
        String input = "localPath=" + localPath;
        String remotePath;
        try (CodeExecutionEnvironment environment = factory.create(skillMdPath)) {
            remotePath = environment.uploadFile(localPath);
        }
        String output = "remotePath=" + remotePath;
        logToolInfo(STEP_UPLOAD_FILE, "入力ファイルをアップロードしました", input, output);
        publishToolEvent(STEP_UPLOAD_FILE, "uploadFile", input, output, null);
        return remotePath;
    }

    public byte[] downloadFile(String remotePath) {
        Objects.requireNonNull(remotePath, "remotePath");
        String input = "remotePath=" + remotePath;
        byte[] data;
        try (CodeExecutionEnvironment environment = factory.create(skillMdPath)) {
            data = environment.downloadFile(remotePath);
        }
        String output = "bytes=" + data.length;
        logToolInfo(STEP_DOWNLOAD_FILE, "成果物をダウンロードしました", input, output);
        publishToolEvent(STEP_DOWNLOAD_FILE, "downloadFile", input, output, null);
        return data;
    }

    @Tool("リモート実行環境でコマンドを実行します。")
    public ExecutionResult executeCommand(String command) {
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("command は空にできません");
        }
        String input = "command=" + command;
        ExecutionResult result;
        try (CodeExecutionEnvironment environment = factory.create(skillMdPath)) {
            result = environment.executeCommand(command);
        }
        String output = result.toString();
        logToolInfo(STEP_EXECUTE_COMMAND, "リモートコマンドを実行しました", input, output);
        publishToolEvent(STEP_EXECUTE_COMMAND, "executeCommand", input, output, null);
        return result;
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

    private void logToolInfo(String step, String message, String input, String output) {
        if (log == null) {
            return;
        }
        log.info(basicLog, runId, skillId, PHASE_TOOL, step, message, input, output);
    }

    private void publishToolEvent(String step, String toolName, String input, String output, Throwable error) {
        SkillEventMetadata metadata = new SkillEventMetadata(runId, skillId, PHASE_TOOL, step, null);
        String errorType = error == null ? "" : error.getClass().getSimpleName();
        String errorMessage = error == null ? "" : safeErrorMessage(error);
        events.publish(new SkillEvent(SkillEventType.TOOL, metadata,
                new ToolPayload(toolName, input, output, errorType, errorMessage)));
    }

    private String safeErrorMessage(Throwable error) {
        String message = error.getMessage();
        return message == null ? "" : message;
    }
}
