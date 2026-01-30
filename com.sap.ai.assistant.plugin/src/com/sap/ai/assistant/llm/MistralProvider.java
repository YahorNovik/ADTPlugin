package com.sap.ai.assistant.llm;

import java.util.List;

import com.sap.ai.assistant.model.ChatMessage;
import com.sap.ai.assistant.model.LlmProviderConfig;
import com.sap.ai.assistant.model.ToolDefinition;

/**
 * LLM provider implementation for the Mistral Chat Completions API.
 *
 * <p>Mistral's API is wire-compatible with OpenAI's Chat Completions API,
 * so this class extends {@link OpenAiProvider} and only overrides the
 * provider identifier. The base URL configured in
 * {@link LlmProviderConfig} (defaulting to {@code https://api.mistral.ai})
 * ensures requests are routed to the Mistral endpoint.</p>
 *
 * <p>Endpoint: {@code POST {baseUrl}/v1/chat/completions}</p>
 * <p>Authentication: {@code Authorization: Bearer {apiKey}}</p>
 */
public class MistralProvider extends OpenAiProvider {

    public MistralProvider(LlmProviderConfig config) {
        super(config);
    }

    @Override
    public String getProviderId() {
        return "mistral";
    }

    @Override
    public ChatMessage sendMessage(List<ChatMessage> messages, String systemPrompt, List<ToolDefinition> tools)
            throws LlmException {

        String url = buildEndpointUrl();
        String requestBody = buildRequestBody(messages, systemPrompt, tools);
        var response = sendRequest(url, requestBody);
        return parseResponse(response.body());
    }
}
