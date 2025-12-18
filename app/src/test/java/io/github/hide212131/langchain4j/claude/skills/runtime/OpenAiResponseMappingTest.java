package io.github.hide212131.langchain4j.claude.skills.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.openai.core.ObjectMappers;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.completions.CompletionUsage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialTokenUsage;
import dev.langchain4j.model.output.FinishReason;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OpenAiResponseMappingTest {

    @Test
    @DisplayName("OpenAI Official SDK のレスポンス JSON を LangChain4j の AiMessage/FinishReason にマッピングできる")
    void mapChatCompletionResponse() throws Exception {
        // 実際の chat completions API (gpt-4o-mini) のシンプルなレスポンス例を使用する
        String json = """
                {
                  "id": "chatcmpl-abc123",
                  "object": "chat.completion",
                  "created": 1717654321,
                  "model": "gpt-4o-mini-2024-07-18",
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "公式レスポンスのサンプルです。テスト用のテキストを返します。",
                        "refusal": null,
                        "tool_calls": []
                      },
                      "finish_reason": "stop",
                      "logprobs": null
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 12,
                    "completion_tokens": 18,
                    "total_tokens": 30
                  }
                }
                """;

        JsonMapper mapper = ObjectMappers.jsonMapper();
        ChatCompletion completion = mapper.readValue(json, ChatCompletion.class);

        Class<?> helper = Class.forName("dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialHelper");
        Method aiMessageFrom = helper.getDeclaredMethod("aiMessageFrom", ChatCompletion.class);
        aiMessageFrom.setAccessible(true);
        Method finishReasonFrom =
                helper.getDeclaredMethod("finishReasonFrom", ChatCompletion.Choice.FinishReason.class);
        finishReasonFrom.setAccessible(true);
        Method tokenUsageFrom = helper.getDeclaredMethod("tokenUsageFrom", CompletionUsage.class);
        tokenUsageFrom.setAccessible(true);

        AiMessage message = (AiMessage) aiMessageFrom.invoke(null, completion);
        FinishReason reason =
                (FinishReason) finishReasonFrom.invoke(null, completion.choices().get(0).finishReason());
        OpenAiOfficialTokenUsage usage =
                (OpenAiOfficialTokenUsage) tokenUsageFrom.invoke(null, completion.usage().orElseThrow());

        assertThat(message.text())
                .contains("公式レスポンスのサンプル")
                .contains("テスト用のテキスト");
        assertThat(reason).isEqualTo(FinishReason.STOP);
        assertThat(usage.inputTokenCount()).isEqualTo(12);
        assertThat(usage.outputTokenCount()).isEqualTo(18);
        assertThat(usage.totalTokenCount()).isEqualTo(30);
    }
}
