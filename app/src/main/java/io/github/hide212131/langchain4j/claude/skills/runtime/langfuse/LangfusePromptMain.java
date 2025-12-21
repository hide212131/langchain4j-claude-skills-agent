package io.github.hide212131.langchain4j.claude.skills.runtime.langfuse;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** LangFuse からプロンプト関連情報を抽出する。 */
@SuppressWarnings({ "PMD.SystemPrintln", "PMD.CognitiveComplexity", "PMD.NPathComplexity" })
public final class LangfusePromptMain {

    private static final List<String> PROMPT_KEYS = List.of("gen_ai.request.prompt", "visibility.prompt.content",
            "gen_ai.request.messages", "gen_ai.request.system", "gen_ai.request.user");

    private LangfusePromptMain() {
    }

    public static void main(String[] args) {
        Arguments parsed = Arguments.parse(args);

        LangfuseConfiguration config = LangfuseConfiguration.load();
        if (!config.isConfigured()) {
            System.out.println("LangFuse の資格情報が未設定のためスキップします。");
            System.out.println("必要な環境変数: LANGFUSE_HOST, LANGFUSE_PUBLIC_KEY, LANGFUSE_SECRET_KEY");
            return;
        }

        LangfuseApiClient client = new LangfuseApiClient(config);
        JsonNode trace;
        if (parsed.traceId() != null) {
            trace = client.getTrace(parsed.traceId());
        } else {
            JsonNode list = client.listTraces(parsed.limit());
            JsonNode data = list.get("data");
            if (data == null || !data.isArray() || data.isEmpty() || data.get(0).get("id") == null) {
                System.out.println("トレースが見つかりません。");
                return;
            }
            trace = client.getTrace(data.get(0).get("id").asText());
        }

        List<LangfuseJsonScan.FoundValue> found = LangfuseJsonScan.findValuesAnyOf(trace, PROMPT_KEYS);
        System.out.println("=== LangFuse Prompt Extract ===");
        if (parsed.traceId() != null) {
            System.out.println("traceId=" + parsed.traceId());
        }
        if (found.isEmpty()) {
            System.out.println("対象キーが見つかりません。");
            System.out.println("探索キー: " + PROMPT_KEYS);
            return;
        }
        for (LangfuseJsonScan.FoundValue item : found) {
            System.out.println(item.path() + ": " + item.value());
        }
    }

    record Arguments(String traceId, int limit) {

        static Arguments parse(String[] args) {
            String traceId = null;
            int limit = 5;
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if ("--trace-id".equals(arg) && i + 1 < args.length) {
                    traceId = args[i + 1];
                    i++;
                } else if ("--limit".equals(arg) && i + 1 < args.length) {
                    limit = Integer.parseInt(args[i + 1]);
                    i++;
                }
            }
            return new Arguments(traceId, limit);
        }
    }
}
