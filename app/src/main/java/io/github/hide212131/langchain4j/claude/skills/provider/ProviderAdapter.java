package io.github.hide212131.langchain4j.claude.skills.provider;

import java.util.List;
import java.util.Objects;

public final class ProviderAdapter {

    private final LlmClient client;

    public ProviderAdapter(LlmClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    public LlmResult complete(ChatInteraction interaction) {
        Objects.requireNonNull(interaction, "interaction");

        List<ToolSpecification> toolSpecs = interaction.tools().stream()
                .map(tool -> new ToolSpecification(tool.name(), tool.description(), tool.parameters()))
                .toList();
        LlmRequest request = new LlmRequest(interaction.messages(), toolSpecs);
        LlmResponse response = client.complete(request);
        return new LlmResult(response.messages(), response.toolCalls(), response.tokensIn(), response.tokensOut());
    }
}
