package io.github.hide212131.langchain4j.claude.skills.app.langfuse;

import java.util.List;
import java.util.Optional;

/**
 * CLI entry point that prints Langfuse prompts for workflow.act llm.chat observations.
 */
public final class LangfusePromptTool {

    private LangfusePromptTool() {}

    public static void main(String[] args) {
        try {
            Arguments options = Arguments.parse(args);

            LangfuseTraceUtil util = options.buildUtil();

            String effectiveTraceId = options.traceId;
            if (effectiveTraceId == null) {
                effectiveTraceId = util.getLatestTraceId()
                        .orElseThrow(() -> new IllegalStateException("Failed to locate latest trace from Langfuse"));
                System.out.println("Info: using latest traceId=" + effectiveTraceId);
            }

            if (options.all) {
                List<String> prompts = util.getAllLlmChatPrompts(effectiveTraceId);
                if (prompts.isEmpty()) {
                    System.out.println("No prompts were found.");
                } else {
                    for (int i = 0; i < prompts.size(); i++) {
                        System.out.println("# Prompt " + i);
                        System.out.println(prompts.get(i));
                        System.out.println();
                    }
                }
                return;
            }

            int index = options.index != null ? options.index : 0;
            Optional<String> prompt = util.getLlmChatPrompt(effectiveTraceId, index);
            if (prompt.isPresent()) {
                System.out.println(prompt.get());
            } else {
                System.err.printf(
                        "Prompt not found. traceId=%s, index=%d%n",
                        effectiveTraceId,
                        index);
                System.exit(1);
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println("Use --help for usage information.");
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private record Arguments(
            String traceId,
            Integer index,
            boolean all,
            String baseUrl,
            String publicKey,
            String secretKey) {

        static Arguments parse(String[] args) {
            String traceId = null;
            Integer index = null;
            boolean all = false;
            String baseUrl = null;
            String publicKey = null;
            String secretKey = null;

            for (int i = 0; i < args.length; i++) {
                String current = args[i];
                switch (current) {
                    case "--trace-id" -> {
                        traceId = requireValue(args, ++i, "--trace-id");
                    }
                    case "--index" -> {
                        String value = requireValue(args, ++i, "--index");
                        index = Integer.parseInt(value);
                        if (index < 0) {
                            throw new IllegalArgumentException("--index must be >= 0");
                        }
                    }
                    case "--all" -> all = true;
                    case "--base-url" -> baseUrl = requireValue(args, ++i, "--base-url");
                    case "--public-key" -> publicKey = requireValue(args, ++i, "--public-key");
                    case "--secret-key" -> secretKey = requireValue(args, ++i, "--secret-key");
                    case "--help", "-h" -> {
                        printHelp();
                        System.exit(0);
                    }
                    default -> throw new IllegalArgumentException("Unknown option: " + current);
                }
            }

            if (all && index != null) {
                throw new IllegalArgumentException("--all and --index cannot be used together");
            }

            return new Arguments(traceId, index, all, baseUrl, publicKey, secretKey);
        }

        private static String requireValue(String[] args, int index, String optionName) {
            if (index >= args.length) {
                throw new IllegalArgumentException(optionName + " requires a value");
            }
            return args[index];
        }

        private static void printHelp() {
        System.out.println("Usage: [--trace-id <id>] [--index <n> | --all] [--base-url <url>]"
                    + " [--public-key <key>] [--secret-key <key>]");
            System.out.println();
        System.out.println("When --trace-id is omitted the latest workflow trace is used.");
        System.out.println("When keys are omitted the tool falls back to environment variables.");
        }

        LangfuseTraceUtil buildUtil() {
        String resolvedBaseUrl = firstNonBlank(
            baseUrl,
            System.getenv("LANGFUSE_BASE_URL"),
            System.getenv("LANGFUSE_BASEURL"));
            String resolvedPublicKey = firstNonBlank(publicKey, System.getenv("LANGFUSE_PUBLIC_KEY"));
            String resolvedSecretKey = firstNonBlank(secretKey, System.getenv("LANGFUSE_SECRET_KEY"));
            String resolvedProjectId = System.getenv("LANGFUSE_PROJECT_ID");

            if (resolvedPublicKey == null || resolvedSecretKey == null) {
                throw new IllegalArgumentException("Langfuse credentials are missing. Set env vars or provide CLI options.");
            }
            if (resolvedBaseUrl == null || resolvedBaseUrl.isBlank()) {
                resolvedBaseUrl = "http://localhost:3000";
            }

            return new LangfuseTraceUtil(resolvedBaseUrl, resolvedPublicKey, resolvedSecretKey, resolvedProjectId);
        }

        private static String firstNonBlank(String... values) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
            return null;
        }
    }
}
