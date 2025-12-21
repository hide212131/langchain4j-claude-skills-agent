package io.github.hide212131.langchain4j.claude.skills.runtime.langfuse;

import java.util.Objects;

/** LangFuse API への接続情報。 */
public record LangfuseConfiguration(String host, String publicKey, String secretKey) {

    public static LangfuseConfiguration load() {
        String host = firstNonBlank(System.getProperty("langfuse.host"), System.getenv("LANGFUSE_HOST"));
        String publicKey = firstNonBlank(System.getProperty("langfuse.publicKey"),
                System.getenv("LANGFUSE_PUBLIC_KEY"));
        String secretKey = firstNonBlank(System.getProperty("langfuse.secretKey"),
                System.getenv("LANGFUSE_SECRET_KEY"));
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
}
