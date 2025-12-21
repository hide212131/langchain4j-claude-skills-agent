package io.github.hide212131.langchain4j.claude.skills.runtime.langfuse;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** LangFuse から直近トレースを取得し、gen_ai 指標を集計する。 */
@SuppressWarnings({ "PMD.SystemPrintln", "PMD.CognitiveComplexity", "PMD.NPathComplexity" })
public final class LangfuseReportMain {

    private LangfuseReportMain() {
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
        List<JsonNode> traces = new ArrayList<>();
        if (parsed.traceId() != null) {
            traces.add(client.getTrace(parsed.traceId()));
        } else {
            JsonNode list = client.listTraces(parsed.limit());
            JsonNode data = list.get("data");
            if (data != null && data.isArray()) {
                for (JsonNode item : data) {
                    JsonNode idNode = item.get("id");
                    if (idNode != null && idNode.isTextual()) {
                        traces.add(client.getTrace(idNode.asText()));
                    }
                }
            }
        }

        long totalInput = 0;
        long totalOutput = 0;
        List<Long> latencies = new ArrayList<>();
        int spanCount = 0;

        for (JsonNode trace : traces) {
            List<LangfuseJsonScan.FoundValue> inputTokens = LangfuseJsonScan.findValues(trace,
                    "gen_ai.usage.input_tokens");
            List<LangfuseJsonScan.FoundValue> outputTokens = LangfuseJsonScan.findValues(trace,
                    "gen_ai.usage.output_tokens");
            List<LangfuseJsonScan.FoundValue> latencyMs = LangfuseJsonScan.findValues(trace,
                    "visibility.metrics.latency_ms");
            for (LangfuseJsonScan.FoundValue value : inputTokens) {
                if (value.value().canConvertToLong()) {
                    totalInput += value.value().asLong();
                    spanCount++;
                }
            }
            for (LangfuseJsonScan.FoundValue value : outputTokens) {
                if (value.value().canConvertToLong()) {
                    totalOutput += value.value().asLong();
                }
            }
            for (LangfuseJsonScan.FoundValue value : latencyMs) {
                if (value.value().canConvertToLong()) {
                    latencies.add(value.value().asLong());
                }
            }
        }

        latencies.sort(Comparator.naturalOrder());
        Long p95 = percentile(latencies, 95);
        System.out.println("=== LangFuse Report ===");
        System.out.println("traces=" + traces.size());
        System.out.println("spans_with_usage=" + spanCount);
        System.out.println("total_input_tokens=" + totalInput);
        System.out.println("total_output_tokens=" + totalOutput);
        if (p95 != null) {
            System.out.println("p95_latency_ms=" + p95);
        } else {
            System.out.println("p95_latency_ms=(not available)");
        }
    }

    record Arguments(String traceId, int limit) {

        static Arguments parse(String[] args) {
            int limit = 20;
            String traceId = null;
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if ("--limit".equals(arg) && i + 1 < args.length) {
                    limit = Integer.parseInt(args[i + 1]);
                    i++;
                } else if ("--trace-id".equals(arg) && i + 1 < args.length) {
                    traceId = args[i + 1];
                    i++;
                }
            }
            return new Arguments(traceId, limit);
        }
    }

    private static Long percentile(List<Long> sorted, int p) {
        if (sorted == null || sorted.isEmpty()) {
            return null;
        }
        int idx = (int) Math.ceil((p / 100.0) * sorted.size()) - 1;
        if (idx < 0) {
            idx = 0;
        }
        if (idx >= sorted.size()) {
            idx = sorted.size() - 1;
        }
        return sorted.get(idx);
    }
}
