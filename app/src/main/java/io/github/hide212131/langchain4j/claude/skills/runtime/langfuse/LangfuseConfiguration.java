package io.github.hide212131.langchain4j.claude.skills.runtime.langfuse;

import io.github.cdimascio.dotenv.Dotenv;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** LangFuse API への接続情報。 */
public record LangfuseConfiguration(String host, String publicKey, String secretKey) {

    public static LangfuseConfiguration load() {
        Dotenv dotenv = loadDotenvWithFallback();
        String host = firstNonBlank(System.getProperty("langfuse.host"), resolveWithPriority(dotenv, "LANGFUSE_HOST"));
        String publicKey = firstNonBlank(System.getProperty("langfuse.publicKey"),
                resolveWithPriority(dotenv, "LANGFUSE_PUBLIC_KEY"));
        String secretKey = firstNonBlank(System.getProperty("langfuse.secretKey"),
                resolveWithPriority(dotenv, "LANGFUSE_SECRET_KEY"));
        return new LangfuseConfiguration(host, publicKey, secretKey);
    }

    public boolean isConfigured() {
        return host != null && !host.isBlank() && publicKey != null && !publicKey.isBlank() && secretKey != null
                && !secretKey.isBlank();
    }

    public String baseUrl() {
        Objects.requireNonNull(host, "host");
        return host.endsWith("/") ? host.substring(0, host.length() - 1) : host;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        if (b != null && !b.isBlank()) {
            return b.trim();
        }
        return null;
    }

    private static String resolveWithPriority(Dotenv dotenv, String key) {
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) {
            return env;
        }
        return dotenv.get(key);
    }

    private static Dotenv loadDotenvWithFallback() {
        Path cwd = Path.of("").toAbsolutePath();
        if (Files.exists(cwd.resolve(".env"))) {
            return Dotenv.configure().directory(cwd.toString()).ignoreIfMalformed().ignoreIfMissing().load();
        }
        Path parent = cwd.getParent();
        if (parent != null && Files.exists(parent.resolve(".env"))) {
            return Dotenv.configure().directory(parent.toString()).ignoreIfMalformed().ignoreIfMissing().load();
        }
        return Dotenv.configure().ignoreIfMalformed().ignoreIfMissing().load();
    }
}
