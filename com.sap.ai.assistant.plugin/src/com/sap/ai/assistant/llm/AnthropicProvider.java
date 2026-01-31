package com.sap.ai.assistant.llm;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sap.ai.assistant.model.ChatMessage;
import com.sap.ai.assistant.model.LlmProviderConfig;
import com.sap.ai.assistant.model.LlmUsage;
import com.sap.ai.assistant.model.ToolCall;
import com.sap.ai.assistant.model.ToolDefinition;
import com.sap.ai.assistant.model.ToolResult;

/**
 * LLM provider implementation for the Anthropic Claude Messages API.
 *
 * <p>Endpoint: {@code POST {baseUrl}/v1/messages}</p>
 * <p>Authentication: {@code x-api-key} header, plus {@code anthropic-version} header.</p>
 */
public class AnthropicProvider extends AbstractLlmProvider {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    public AnthropicProvider(LlmProviderConfig config) {
        super(config);
    }

    // -- LlmProvider --------------------------------------------------------

    @Override
    public String getProviderId() {
        return "anthropic";
    }

    @Override
    public ChatMessage sendMessage(List<ChatMessage> messages, String systemPrompt, List<ToolDefinition> tools)
            throws LlmException {

        String url = config.getBaseUrl() + "/v1/messages";
        String requestBody = buildRequestBody(messages, systemPrompt, tools);
        HttpResponse<String> response = sendRequest(url, requestBody);
        return parseResponse(response.body());
    }

    // -- Auth headers -------------------------------------------------------

    @Override
    protected void addAuthHeaders(HttpRequest.Builder builder) {
        builder.header("x-api-key", config.getApiKey());
        builder.header("anthropic-version", ANTHROPIC_VERSION);
    }

    @Override
    protected String getAuthHeaderName() {
        // Not used — we override addAuthHeaders directly
        return null;
    }

    @Override
    protected String getAuthHeaderValue() {
        return null;
    }

    // -- Request building ---------------------------------------------------

    private String buildRequestBody(List<ChatMessage> messages, String systemPrompt, List<ToolDefinition> tools) {
        JsonObject body = new JsonObject();
        body.addProperty("model", config.getModel());
        body.addProperty("max_tokens", config.getMaxTokens() > 0 ? config.getMaxTokens() : 8192);

        // System prompt
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            body.addProperty("system", systemPrompt);
        }

        // Tools
        if (tools != null && !tools.isEmpty()) {
            JsonArray toolsArr = new JsonArray();
            for (ToolDefinition tool : tools) {
                toolsArr.add(ToolSchemaConverter.toAnthropicTool(tool));
            }
            body.add("tools", toolsArr);
        }

        // Messages
        body.add("messages", buildMessages(messages));

        return body.toString();
    }

    /**
     * Converts the conversation history into Anthropic's messages format.
     *
     * <ul>
     *   <li>USER messages become {@code {"role":"user","content":[{"type":"text","text":"..."}]}}</li>
     *   <li>ASSISTANT messages become {@code {"role":"assistant","content":[...]}} with
     *       text and/or tool_use blocks</li>
     *   <li>TOOL messages become {@code {"role":"user","content":[{"type":"tool_result",...}]}}</li>
     * </ul>
     */
    private JsonArray buildMessages(List<ChatMessage> messages) {
        JsonArray arr = new JsonArray();
        for (ChatMessage msg : messages) {
            switch (msg.getRole()) {
                case USER:
                    arr.add(buildUserMessage(msg));
                    break;
                case ASSISTANT:
                    arr.add(buildAssistantMessage(msg));
                    break;
                case TOOL:
                    arr.add(buildToolResultMessage(msg));
                    break;
            }
        }
        return arr;
    }

    private JsonObject buildUserMessage(ChatMessage msg) {
        JsonObject obj = new JsonObject();
        obj.addProperty("role", "user");

        JsonArray content = new JsonArray();
        if (msg.getTextContent() != null && !msg.getTextContent().isEmpty()) {
            JsonObject textBlock = new JsonObject();
            textBlock.addProperty("type", "text");
            textBlock.addProperty("text", msg.getTextContent());
            content.add(textBlock);
        }
        obj.add("content", content);
        return obj;
    }

    private JsonObject buildAssistantMessage(ChatMessage msg) {
        JsonObject obj = new JsonObject();
        obj.addProperty("role", "assistant");

        JsonArray content = new JsonArray();
        if (msg.getTextContent() != null && !msg.getTextContent().isEmpty()) {
            JsonObject textBlock = new JsonObject();
            textBlock.addProperty("type", "text");
            textBlock.addProperty("text", msg.getTextContent());
            content.add(textBlock);
        }

        // Tool use blocks
        if (msg.hasToolCalls()) {
            for (ToolCall tc : msg.getToolCalls()) {
                JsonObject toolUse = new JsonObject();
                toolUse.addProperty("type", "tool_use");
                toolUse.addProperty("id", tc.getId());
                toolUse.addProperty("name", tc.getName());
                toolUse.add("input", tc.getArguments() != null ? tc.getArguments() : new JsonObject());
                content.add(toolUse);
            }
        }

        obj.add("content", content);
        return obj;
    }

    /**
     * Tool results are sent as a user message with content blocks of type "tool_result".
     */
    private JsonObject buildToolResultMessage(ChatMessage msg) {
        JsonObject obj = new JsonObject();
        obj.addProperty("role", "user");

        JsonArray content = new JsonArray();
        for (ToolResult tr : msg.getToolResults()) {
            JsonObject block = new JsonObject();
            block.addProperty("type", "tool_result");
            block.addProperty("tool_use_id", tr.getToolCallId());
            block.addProperty("content", tr.getContent());
            if (tr.isError()) {
                block.addProperty("is_error", true);
            }
            content.add(block);
        }

        obj.add("content", content);
        return obj;
    }

    // -- Response parsing ---------------------------------------------------

    private ChatMessage parseResponse(String responseBody) throws LlmException {
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();

            StringBuilder textContent = new StringBuilder();
            List<ToolCall> toolCalls = new ArrayList<>();

            JsonArray contentArray = json.getAsJsonArray("content");
            if (contentArray != null) {
                for (JsonElement el : contentArray) {
                    JsonObject block = el.getAsJsonObject();
                    String type = block.get("type").getAsString();

                    if ("text".equals(type)) {
                        if (textContent.length() > 0) {
                            textContent.append("\n");
                        }
                        textContent.append(block.get("text").getAsString());
                    } else if ("tool_use".equals(type)) {
                        String id = block.get("id").getAsString();
                        String name = block.get("name").getAsString();
                        JsonObject input = block.has("input") && block.get("input").isJsonObject()
                                ? block.getAsJsonObject("input")
                                : new JsonObject();
                        toolCalls.add(new ToolCall(id, name, input));
                    }
                }
            }

            String text = textContent.length() > 0 ? textContent.toString() : null;
            ChatMessage msg = new ChatMessage(ChatMessage.Role.ASSISTANT, text, toolCalls, null);

            // Extract token usage
            if (json.has("usage") && json.get("usage").isJsonObject()) {
                msg.setUsage(LlmUsage.fromAnthropicJson(json.getAsJsonObject("usage")));
            }

            return msg;

        } catch (Exception e) {
            throw new LlmException("Failed to parse Anthropic response: " + e.getMessage(), e);
        }
    }
}
