package io.github.hide212131.langchain4j.claude.skills.bundle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 生成された Dockerfile をビルドし、規約ベースのタグを付与する
 */
public class SkillImageBuilder {

    private final Path projectRoot;

    /**
     * コンストラクタ
     *
     * @param projectRoot プロジェクトルートディレクトリ（Docker ビルドコンテキスト）
     */
    public SkillImageBuilder(Path projectRoot) {
        this.projectRoot = projectRoot;
    }

    /**
     * 指定されたスキルの Dockerfile をビルドしてイメージを作成
     *
     * @param skillPath      スキルパス（例: "anthropics/skills/document-skills/pptx"）
     * @param dockerfilePath Dockerfile のパス
     * @return ビルドしたイメージタグ
     * @throws IOException ビルド失敗時
     */
    public String buildImage(String skillPath, Path dockerfilePath) throws IOException {
        if (!Files.exists(dockerfilePath)) {
            throw new IOException("Dockerfile が存在しません: " + dockerfilePath);
        }

        String imageTag = SkillImageTagGenerator.generateImageTag(skillPath);

        List<String> command = new ArrayList<>();
        command.add("docker");
        command.add("build");
        command.add("-t");
        command.add(imageTag);
        command.add("-f");
        command.add(dockerfilePath.toString());
        command.add(projectRoot.toString());

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        Process process = pb.start();

        // 出力をリアルタイムで表示
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        }

        int exitCode;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Docker ビルドが中断されました: " + skillPath, e);
        }

        if (exitCode != 0) {
            throw new IOException("Docker ビルド失敗 (exit code: " + exitCode + "): " + skillPath);
        }

        return imageTag;
    }

    /**
     * Docker がインストールされているか確認
     *
     * @return Docker が利用可能な場合は true
     */
    public static boolean isDockerAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "--version");
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }
}
