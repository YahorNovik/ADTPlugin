package com.sap.ai.assistant.llm;

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
 * LLM provider implementation for the OpenAI Chat Completions API.
 *
 * <p>Endpoint: {@code POST {baseUrl}/v1/chat/completions}</p>
 * <p>Authentication: {@code Authorization: Bearer {apiKey}}</p>
 */
public class OpenAiProvider extends AbstractLlmProvider {

    public OpenAiProvider(LlmProviderConfig config) {
        super(config);
    }

    // -- LlmProvider --------------------------------------------------------

    @Override
    public String getProviderId() {
        return "openai";
    }

    @Override
    public ChatMessage sendMessage(List<ChatMessage> messages, String systemPrompt, List<ToolDefinition> tools)
            throws LlmException {

        String url = buildEndpointUrl();
        String requestBody = buildRequestBody(messages, systemPrompt, tools);
        HttpResponse<String> response = sendRequest(url, requestBody);
        return parseResponse(response.body());
    }

    // -- Auth ---------------------------------------------------------------

    @Override
    protected String getAuthHeaderName() {
        return "Authorization";
    }

    @Override
    protected String getAuthHeaderValue() {
        return "Bearer " + config.getApiKey();
    }

    // -- URL ----------------------------------------------------------------

    /**
     * Builds the endpoint URL. Subclasses (e.g. Mistral) may override this.
     *
     * @return the fully qualified endpoint URL
     */
    protected String buildEndpointUrl() {
        return config.getBaseUrl() + "/v1/chat/completions";
    }

    // -- Request building ---------------------------------------------------

    /**
     * Builds the JSON request body. This method is {@code protected} so that
     * subclasses sharing the same wire format (e.g. Mistral) can reuse it.
     */
    protected String buildRequestBody(List<ChatMessage> messages, String systemPrompt, List<ToolDefinition> tools) {
        JsonObject body = new JsonObject();
        body.addProperty("model", config.getModel());
        body.addProperty("max_tokens", config.getMaxTokens() > 0 ? config.getMaxTokens() : 8192);

        // Tools
        if (tools != null && !tools.isEmpty()) {
            JsonArray toolsArr = new JsonArray();
            for (ToolDefinition tool : tools) {
                toolsArr.add(ToolSchemaConverter.toOpenAiTool(tool));
            }
            body.add("tools", toolsArr);
        }

        // Messages
        body.add("messages", buildMessages(messages, systemPrompt));

        return body.toString();
    }

    /**
     * Converts the conversation history into the OpenAI messages format.
     *
     * <ul>
     *   <li>System prompt goes as the first message with role "system".</li>
     *   <li>USER messages: {@code {"role":"user","content":"..."}}</li>
     *   <li>ASSISTANT messages: {@code {"role":"assistant","content":"...","tool_calls":[...]}}</li>
     *   <li>TOOL messages: one {@code {"role":"tool","tool_call_id":"...","content":"..."}} per result</li>
     * </ul>
     */
    protected JsonArray buildMessages(List<ChatMessage> messages, String systemPrompt) {
        JsonArray arr = new JsonArray();

        // System message first
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            JsonObject sysMsg = new JsonObject();
            sysMsg.addProperty("role", "system");
            sysMsg.addProperty("content", systemPrompt);
            arr.add(sysMsg);
        }

        for (ChatMessage msg : messages) {
            switch (msg.getRole()) {
                case USER:
                    arr.add(buildUserMessage(msg));
                    break;
                case ASSISTANT:
                    arr.add(buildAssistantMessage(msg));
                    break;
                case TOOL:
                    // Each tool result is a separate message in OpenAI format
                    for (ToolResult tr : msg.getToolResults()) {
                        arr.add(buildToolResultMessage(tr));
                    }
                    break;
            }
        }
        return arr;
    }

    private JsonObject buildUserMessage(ChatMessage msg) {
        JsonObject obj = new JsonObject();
        obj.addProperty("role", "user");
        obj.addProperty("content", msg.getTextContent() != null ? msg.getTextContent() : "");
        return obj;
    }

    private JsonObject buildAssistantMessage(ChatMessage msg) {
        JsonObject obj = new JsonObject();
        obj.addProperty("role", "assistant");

        if (msg.getTextContent() != null) {
            obj.addProperty("content", msg.getTextContent());
        } else {
            obj.add("content", null);
        }

        // Tool calls
        if (msg.hasToolCalls()) {
            JsonArray tcArr = new JsonArray();
            for (ToolCall tc : msg.getToolCalls()) {
                JsonObject tcObj = new JsonObject();
                tcObj.addProperty("id", tc.getId());
                tcObj.addProperty("type", "function");

                JsonObject fnObj = new JsonObject();
                fnObj.addProperty("name", tc.getName());
                fnObj.addProperty("arguments", tc.getArguments() != null ? tc.getArguments().toString() : "{}");
                tcObj.add("function", fnObj);

                tcArr.add(tcObj);
            }
            obj.add("tool_calls", tcArr);
        }

        return obj;
    }

    private JsonObject buildToolResultMessage(ToolResult tr) {
        JsonObject obj = new JsonObject();
        obj.addProperty("role", "tool");
        obj.addProperty("tool_call_id", tr.getToolCallId());
        obj.addProperty("content", tr.getContent() != null ? tr.getContent() : "");
        return obj;
    }

    // -- Response parsing ---------------------------------------------------

    /**
     * Parses the OpenAI Chat Completions response and extracts the assistant
     * message. This method is {@code protected} so that subclasses (e.g. Mistral)
     * sharing the same response format can reuse it.
     */
    protected ChatMessage parseResponse(String responseBody) throws LlmException {
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();

            JsonArray choices = json.getAsJsonArray("choices");
            if (choices == null || choices.size() == 0) {
                throw new LlmException("No choices returned in " + getProviderId() + " response");
            }

            JsonObject choice = choices.get(0).getAsJsonObject();
            JsonObject message = choice.getAsJsonObject("message");

            // Text content
            String textContent = null;
            if (message.has("content") && !message.get("content").isJsonNull()) {
                textContent = message.get("content").getAsString();
            }

            // Tool calls
            List<ToolCall> toolCalls = new ArrayList<>();
            if (message.has("tool_calls") && message.get("tool_calls").isJsonArray()) {
                JsonArray tcArr = message.getAsJsonArray("tool_calls");
                for (JsonElement el : tcArr) {
                    JsonObject tcObj = el.getAsJsonObject();
                    String id = tcObj.get("id").getAsString();

                    JsonObject fnObj = tcObj.getAsJsonObject("function");
                    String name = fnObj.get("name").getAsString();

                    // Arguments come as a JSON string that needs parsing
                    String argsStr = fnObj.get("arguments").getAsString();
                    JsonObject arguments;
                    try {
                        arguments = JsonParser.parseString(argsStr).getAsJsonObject();
                    } catch (Exception e) {
                        // If the arguments can't be parsed, wrap them
                        arguments = new JsonObject();
                        arguments.addProperty("_raw", argsStr);
                    }

                    toolCalls.add(new ToolCall(id, name, arguments));
                }
            }

            ChatMessage msg = new ChatMessage(ChatMessage.Role.ASSISTANT, textContent, toolCalls, null);

            // Extract token usage
            if (json.has("usage") && json.get("usage").isJsonObject()) {
                msg.setUsage(LlmUsage.fromOpenAiJson(json.getAsJsonObject("usage")));
            }

            return msg;

        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("Failed to parse " + getProviderId() + " response: " + e.getMessage(), e);
        }
    }
}
