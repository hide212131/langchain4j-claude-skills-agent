package io.github.hide212131.langchain4j.claude.skills.runtime.langfuse;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
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
        JsonNode trace = null;
        String traceId = parsed.traceId();
        List<String> checked = new ArrayList<>();
        if (traceId != null) {
            trace = client.getTrace(traceId);
        } else {
            JsonNode list = client.listTraces(parsed.limit());
            JsonNode data = list.get("data");
            if (data == null || !data.isArray() || data.isEmpty()) {
                System.out.println("トレースが見つかりません。");
                return;
            }
            for (JsonNode item : data) {
                JsonNode idNode = item.get("id");
                if (idNode == null || !idNode.isTextual()) {
                    continue;
                }
                String id = idNode.asText();
                checked.add(id);
                JsonNode candidate = client.getTrace(id);
                if (!LangfuseJsonScan.findValuesAnyOf(candidate, PROMPT_KEYS).isEmpty()) {
                    trace = candidate;
                    traceId = id;
                    break;
                }
            }
            if (trace == null) {
                // フォールバック: 先頭のトレースだけは表示対象にする（デバッグ用途）
                JsonNode idNode = data.get(0).get("id");
                if (idNode != null && idNode.isTextual()) {
                    traceId = idNode.asText();
                    trace = client.getTrace(traceId);
                }
            }
        }

        List<LangfuseJsonScan.FoundValue> found = LangfuseJsonScan.findValuesAnyOf(trace, PROMPT_KEYS);
        System.out.println("=== LangFuse Prompt Extract ===");
        if (traceId != null) {
            System.out.println("traceId=" + traceId);
        }
        if (found.isEmpty()) {
            System.out.println("対象キーが見つかりません。");
            System.out.println("探索キー: " + PROMPT_KEYS);
            if (!checked.isEmpty()) {
                System.out.println("確認した traceId: " + checked);
            }
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
