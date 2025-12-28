package io.github.hide212131.langchain4j.claude.skills.bundle;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Objects;

public record SkillSourceSpec(URI repository, String ref, String sourcePath, String destination) {

    private static final String SCHEME_HTTP = "http";
    private static final String SCHEME_HTTPS = "https";
    private static final int REPO_SPLIT_PARTS = 2;
    private static final int REST_SPLIT_PARTS = 3;

    public SkillSourceSpec {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(ref, "ref");
        Objects.requireNonNull(sourcePath, "sourcePath");
        Objects.requireNonNull(destination, "destination");
        if (!SCHEME_HTTP.equals(repository.getScheme()) && !SCHEME_HTTPS.equals(repository.getScheme())) {
            throw new IllegalArgumentException("取得元は http/https のみに対応しています: " + repository);
        }
        if (ref.isBlank()) {
            throw new IllegalArgumentException("ref は必須です。");
        }
        ensureRelativePath(sourcePath, "sourcePath");
        ensureRelativePath(destination, "destination");
    }

    public static SkillSourceSpec parse(String raw) {
        Objects.requireNonNull(raw, "raw");
        if (raw.isBlank()) {
            throw new IllegalArgumentException("取得元の定義が空です。");
        }
        String[] repoSplit = raw.split("#", REPO_SPLIT_PARTS);
        if (repoSplit.length != REPO_SPLIT_PARTS) {
            throw new IllegalArgumentException("取得元の形式が不正です（# が必要）: " + raw);
        }
        URI repository;
        try {
            repository = new URI(repoSplit[0]);
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("取得元 URL が不正です: " + raw, ex);
        }
        String[] restSplit = repoSplit[1].split(":", REST_SPLIT_PARTS);
        if (restSplit.length != REST_SPLIT_PARTS) {
            throw new IllegalArgumentException("取得元の形式が不正です（ref:path:dest が必要）: " + raw);
        }
        String ref = restSplit[0];
        String sourcePath = restSplit[1];
        String destination = restSplit[2];
        return new SkillSourceSpec(repository, ref, sourcePath, destination);
    }

    private static void ensureRelativePath(String value, String label) {
        if (value.isBlank()) {
            throw new IllegalArgumentException(label + " は必須です。");
        }
        Path path = Path.of(value).normalize();
        if (path.isAbsolute() || path.startsWith("..")) {
            throw new IllegalArgumentException(label + " は相対パスで指定してください: " + value);
        }
    }
}
