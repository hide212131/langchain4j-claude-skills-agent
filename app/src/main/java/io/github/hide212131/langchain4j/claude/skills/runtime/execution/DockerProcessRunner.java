package io.github.hide212131.langchain4j.claude.skills.runtime.execution;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("PMD.CloseResource")
final class DockerProcessRunner {

    private DockerProcessRunner() {
        throw new AssertionError("インスタンス化できません");
    }

    static ExecutionResult run(List<String> command, String logicalCommand) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(logicalCommand, "logicalCommand");
        long startedAt = System.nanoTime();
        Process process;
        try {
            process = new ProcessBuilder(command).start();
        } catch (IOException ex) {
            throw new IllegalStateException("コマンドの起動に失敗しました: " + logicalCommand, ex);
        }
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> stdoutFuture = executor.submit(() -> readStream(process.getInputStream()));
            Future<String> stderrFuture = executor.submit(() -> readStream(process.getErrorStream()));
            int exitCode = waitForProcess(process, logicalCommand);
            String stdout = getFuture(stdoutFuture, logicalCommand);
            String stderr = getFuture(stderrFuture, logicalCommand);
            long elapsedMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            return new ExecutionResult(logicalCommand, exitCode, stdout, stderr, elapsedMs);
        } finally {
            shutdownExecutor(executor);
        }
    }

    private static String readStream(InputStream stream) throws IOException {
        try (InputStream input = stream) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static int waitForProcess(Process process, String logicalCommand) {
        try {
            return process.waitFor();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("コマンドの実行が中断されました: " + logicalCommand, ex);
        }
    }

    private static String getFuture(Future<String> future, String logicalCommand) {
        try {
            return future.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("コマンドの出力取得が中断されました: " + logicalCommand, ex);
        } catch (ExecutionException ex) {
            throw new IllegalStateException("コマンドの出力取得に失敗しました: " + logicalCommand, ex);
        }
    }

    private static void shutdownExecutor(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
