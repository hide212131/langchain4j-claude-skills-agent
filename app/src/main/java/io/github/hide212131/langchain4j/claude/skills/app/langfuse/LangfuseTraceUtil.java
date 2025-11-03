package io.github.hide212131.langchain4j.claude.skills.app.langfuse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

/**
 * LangFuseのAPIを使ってトレースデータからプロンプト情報を取得するユーティリティクラス。
 */
public class LangfuseTraceUtil {

    private static final int LATEST_TRACE_SCAN_LIMIT = 20;

    private final String baseUrl;
    private final String publicKey;
    private final String secretKey;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String projectId;
    private final String authorizationHeader;

    public LangfuseTraceUtil(String baseUrl, String publicKey, String secretKey) {
        this(baseUrl, publicKey, secretKey, null);
    }

    public LangfuseTraceUtil(String baseUrl, String publicKey, String secretKey, String projectId) {
        this.baseUrl = baseUrl;
        this.publicKey = publicKey;
        this.secretKey = secretKey;
        this.projectId = projectId;
        this.objectMapper = new ObjectMapper();
    this.authorizationHeader = buildBasicAuthHeader(publicKey, secretKey);

        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(
                AuthScope.ANY,
                new UsernamePasswordCredentials(publicKey, secretKey));

        this.httpClient = HttpClientBuilder.create()
                .setDefaultCredentialsProvider(credentialsProvider)
                .build();
    }

    /**
     * 環境変数から設定を読み込んでインスタンスを作成。
     */
    public static LangfuseTraceUtil fromEnvironment() {
        String baseUrl = firstNonBlank(System.getenv("LANGFUSE_BASE_URL"), System.getenv("LANGFUSE_BASEURL"));
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:3000";
        }

        String publicKey = System.getenv("LANGFUSE_PUBLIC_KEY");
        String secretKey = System.getenv("LANGFUSE_SECRET_KEY");

        if (publicKey == null || secretKey == null) {
            throw new IllegalArgumentException(
                    "LANGFUSE_PUBLIC_KEY and LANGFUSE_SECRET_KEY must be set");
        }

        String projectId = System.getenv("LANGFUSE_PROJECT_ID");

        return new LangfuseTraceUtil(baseUrl, publicKey, secretKey, projectId);
    }

    /**
     * 指定されたtrace IDから workflow.act の最初の llm.chat の gen_ai.request.prompt を取得。
     */
    public Optional<String> getFirstLlmChatPrompt(String traceId) {
        return getLlmChatPrompt(traceId, 0);
    }

    /**
     * 指定されたtrace IDから workflow.act の指定されたインデックスの llm.chat の gen_ai.request.prompt を取得。
     */
    public Optional<String> getLlmChatPrompt(String traceId, int index) {
        if (index < 0) {
            System.out.println("Index must be zero or positive (requested: " + index + ")");
            return Optional.empty();
        }

        try {
            List<JsonNode> observations = getTraceObservations(traceId);
            if (observations.isEmpty()) {
                System.out.println("No observations found for trace: " + traceId);
                return Optional.empty();
            }

            Optional<String> workflowActId = findWorkflowActObservationId(observations);
            if (workflowActId.isEmpty()) {
                System.out.println("workflow.act span not found");
                return Optional.empty();
            }

            List<JsonNode> llmGenerations = collectWorkflowActLlmChats(observations, workflowActId.get());
            if (llmGenerations.isEmpty()) {
                System.out.println("No llm.chat generations found under workflow.act");
                logAllLlmChatObservations(observations);
                return Optional.empty();
            }

            if (index >= llmGenerations.size()) {
                System.out.println(
                        "Index " + index + " is out of range. Total llm.chat count: " + llmGenerations.size());
                return Optional.empty();
            }

            JsonNode targetLlmChat = llmGenerations.get(index);
            System.out.println("Found llm.chat at index " + index + ": " + targetLlmChat.path("id").asText());

            return extractPromptFromObservation(targetLlmChat);

        } catch (Exception e) {
            System.err.println("Error getting prompt: " + e.getMessage());
            e.printStackTrace();
            return Optional.empty();
        }
    }

    /**
     * workflow.act 配下の llm.chat に含まれる全ての gen_ai.request.prompt を取得。
     */
    public List<String> getAllLlmChatPrompts(String traceId) {
        List<String> prompts = new ArrayList<>();

        try {
            List<JsonNode> observations = getTraceObservations(traceId);
            if (observations.isEmpty()) {
                System.out.println("No observations found for trace: " + traceId);
                return prompts;
            }

            Optional<String> workflowActId = findWorkflowActObservationId(observations);
            if (workflowActId.isEmpty()) {
                System.out.println("workflow.act span not found");
                return prompts;
            }

            List<JsonNode> llmGenerations = collectWorkflowActLlmChats(observations, workflowActId.get());
            if (llmGenerations.isEmpty()) {
                System.out.println("No llm.chat generations found under workflow.act");
                logAllLlmChatObservations(observations);
                return prompts;
            }

            for (JsonNode llmChat : llmGenerations) {
                Optional<String> prompt = extractPromptFromObservation(llmChat);
                prompt.ifPresent(prompts::add);
            }

        } catch (Exception e) {
            System.err.println("Error collecting prompts: " + e.getMessage());
            e.printStackTrace();
        }

        return prompts;
    }

    /**
     * Langfuse APIから最新のトレースIDを取得する。
     */
    public Optional<String> getLatestTraceId() {
        try {
            String url = appendProjectIdParam(
                    baseUrl + "/api/public/traces?limit=" + LATEST_TRACE_SCAN_LIMIT + "&order=desc");
            HttpGet request = new HttpGet(url);
            applyAuthorization(request);

            HttpResponse response = httpClient.execute(request);
            String responseBody = EntityUtils.toString(response.getEntity());

            if (response.getStatusLine().getStatusCode() != 200) {
                System.err.println("Failed to fetch latest trace: " + response.getStatusLine());
                return Optional.empty();
            }

            JsonNode responseJson = objectMapper.readTree(responseBody);
            JsonNode dataArray = responseJson.path("data");
            if (dataArray.isArray() && dataArray.size() > 0) {
                List<String> candidateTraceIds = new ArrayList<>();
                dataArray.forEach(node -> {
                    String id = node.path("id").asText(null);
                    if (id != null && !id.isBlank()) {
                        candidateTraceIds.add(id);
                    }
                });

                for (String candidateId : candidateTraceIds) {
                    if (traceContainsWorkflowPrompt(candidateId)) {
                        return Optional.of(candidateId);
                    }
                }

                if (!candidateTraceIds.isEmpty()) {
                    System.out.println(
                            "Info: no workflow.act llm.chat found in recent traces. Using most recent trace anyway.");
                    return Optional.of(candidateTraceIds.get(0));
                }
            }

            System.out.println("No traces returned from Langfuse API");
            return Optional.empty();

        } catch (Exception e) {
            System.err.println("Error fetching latest trace id: " + e.getMessage());
            e.printStackTrace();
            return Optional.empty();
        }
    }

    private List<JsonNode> getTraceObservations(String traceId) throws IOException {
    String url = appendProjectIdParam(baseUrl + "/api/public/observations?traceId=" + traceId);
    HttpGet request = new HttpGet(url);
    applyAuthorization(request);

        HttpResponse response = httpClient.execute(request);
        String responseBody = EntityUtils.toString(response.getEntity());

        if (response.getStatusLine().getStatusCode() != 200) {
            throw new IOException("Failed to fetch observations: " + response.getStatusLine());
        }

        JsonNode responseJson = objectMapper.readTree(responseBody);
        JsonNode dataArray = responseJson.path("data");

        List<JsonNode> observations = new ArrayList<>();
        if (dataArray.isArray()) {
            dataArray.forEach(observations::add);
        }

        return observations;
    }

    private Optional<JsonNode> getObservationDetail(String observationId) {
        try {
            String url = appendProjectIdParam(baseUrl + "/api/public/observations/" + observationId);
            HttpGet request = new HttpGet(url);
            applyAuthorization(request);

            HttpResponse response = httpClient.execute(request);
            String responseBody = EntityUtils.toString(response.getEntity());

            if (response.getStatusLine().getStatusCode() != 200) {
                System.err.println("Failed to fetch observation detail: " + response.getStatusLine());
                return Optional.empty();
            }

            JsonNode responseJson = objectMapper.readTree(responseBody);
            return Optional.of(responseJson);

        } catch (IOException e) {
            System.err.println("Error fetching observation detail: " + e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<String> findWorkflowActObservationId(List<JsonNode> observations) {
        return observations.stream()
                .filter(obs -> "workflow.act".equals(obs.path("name").asText()))
                .map(obs -> obs.path("id").asText())
                .filter(id -> id != null && !id.isEmpty())
                .findFirst();
    }

    private List<JsonNode> collectWorkflowActLlmChats(List<JsonNode> observations, String workflowActId) {
        return observations.stream()
                .filter(obs -> "GENERATION".equals(obs.path("type").asText()))
                .filter(obs -> "llm.chat".equals(obs.path("name").asText()))
                .filter(obs -> workflowActId.equals(obs.path("parentObservationId").asText()))
                .sorted(Comparator.comparing(obs -> obs.path("startTime").asText()))
                .toList();
    }

    private Optional<String> extractPromptFromObservation(JsonNode llmChatObservation) {
        String observationId = llmChatObservation.path("id").asText();
        if (observationId == null || observationId.isEmpty()) {
            System.out.println("llm.chat observation does not contain an id");
            return Optional.empty();
        }

        Optional<JsonNode> detailedObs = getObservationDetail(observationId);
        if (detailedObs.isEmpty()) {
            System.out.println("Failed to get detailed observation data for id: " + observationId);
            return Optional.empty();
        }

        JsonNode detail = detailedObs.get();
        JsonNode metadata = detail.path("metadata");
        JsonNode attributes = metadata.path("attributes");
        JsonNode modelParameters = detail.path("modelParameters");

        JsonNode promptFromAttributes = attributes.path("gen_ai.request.prompt");
        if (!promptFromAttributes.isMissingNode() && !promptFromAttributes.asText().isEmpty()) {
            return Optional.of(promptFromAttributes.asText());
        }

        JsonNode promptFromModelParams = modelParameters.path("prompt");
        if (!promptFromModelParams.isMissingNode() && !promptFromModelParams.asText().isEmpty()) {
            return Optional.of(promptFromModelParams.asText());
        }

        System.out.println("No prompt data found in llm.chat observation: " + observationId);
        return Optional.empty();
    }

    private void logAllLlmChatObservations(List<JsonNode> observations) {
        System.out.println("All llm.chat observations in trace:");
        for (JsonNode obs : observations) {
            if ("llm.chat".equals(obs.path("name").asText())) {
                System.out.println(
                        "  - ID: "
                                + obs.path("id").asText()
                                + ", Parent: "
                                + obs.path("parentObservationId").asText()
                                + ", Type: "
                                + obs.path("type").asText());
            }
        }
    }

    private String appendProjectIdParam(String url) {
        if (projectId == null || projectId.isBlank()) {
            return url;
        }
        String encodedProjectId = URLEncoder.encode(projectId, StandardCharsets.UTF_8);
        return url + (url.contains("?") ? "&" : "?") + "projectId=" + encodedProjectId;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private String buildBasicAuthHeader(String user, String password) {
        String credentials = user + ":" + password;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    private void applyAuthorization(HttpGet request) {
        request.setHeader("Authorization", authorizationHeader);
        request.setHeader("Accept", "application/json");
    }

    private boolean traceContainsWorkflowPrompt(String traceId) {
        try {
            List<JsonNode> observations = getTraceObservations(traceId);
            Optional<String> workflowActId = findWorkflowActObservationId(observations);
            if (workflowActId.isEmpty()) {
                return false;
            }
            List<JsonNode> llmGenerations = collectWorkflowActLlmChats(observations, workflowActId.get());
            return !llmGenerations.isEmpty();

        } catch (Exception e) {
            System.err.println("Warning: failed to inspect trace " + traceId + ": " + e.getMessage());
            return false;
        }
    }
}
