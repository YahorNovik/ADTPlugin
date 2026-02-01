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
 * LLM provider implementation for the Google Gemini generateContent API.
 *
 * <p>Endpoint: {@code POST {baseUrl}/v1beta/models/{model}:generateContent?key={apiKey}}</p>
 * <p>Authentication: API key passed as a query parameter (no auth header).</p>
 *
 * <p><strong>Important:</strong> Gemini requires UPPERCASE type names in schemas
 * (STRING, OBJECT, INTEGER, BOOLEAN, ARRAY, NUMBER). The
 * {@link ToolSchemaConverter#toGeminiSchema(com.google.gson.JsonObject)} method
 * handles this conversion automatically.</p>
 */
public class GeminiProvider extends AbstractLlmProvider {

    public GeminiProvider(LlmProviderConfig config) {
        super(config);
    }

    // -- LlmProvider --------------------------------------------------------

    @Override
    public String getProviderId() {
        return "gemini";
    }

    @Override
    public ChatMessage sendMessage(List<ChatMessage> messages, String systemPrompt, List<ToolDefinition> tools)
            throws LlmException {

        String url = config.getBaseUrl() + "/v1beta/models/" + config.getModel()
                + ":generateContent?key=" + config.getApiKey();
        String requestBody = buildRequestBody(messages, systemPrompt, tools);
        HttpResponse<String> response = sendRequest(url, requestBody);
        return parseResponse(response.body());
    }

    // -- Auth ---------------------------------------------------------------

    /** Gemini uses an API key in the URL, so no auth header is needed. */
    @Override
    protected void addAuthHeaders(HttpRequest.Builder builder) {
        // No auth header — API key is passed in the URL query parameter
    }

    @Override
    protected String getAuthHeaderName() {
        return null;
    }

    @Override
    protected String getAuthHeaderValue() {
        return null;
    }

    // -- Request building ---------------------------------------------------

    private String buildRequestBody(List<ChatMessage> messages, String systemPrompt, List<ToolDefinition> tools) {
        JsonObject body = new JsonObject();

        // System instruction
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            JsonObject sysInstruction = new JsonObject();
            JsonArray parts = new JsonArray();
            JsonObject textPart = new JsonObject();
            textPart.addProperty("text", systemPrompt);
            parts.add(textPart);
            sysInstruction.add("parts", parts);
            body.add("systemInstruction", sysInstruction);
        }

        // Tools — wrapped in a single tool declaration array
        if (tools != null && !tools.isEmpty()) {
            JsonArray funcDeclarations = new JsonArray();
            for (ToolDefinition tool : tools) {
                funcDeclarations.add(ToolSchemaConverter.toGeminiTool(tool));
            }

            JsonObject toolsWrapper = new JsonObject();
            toolsWrapper.add("functionDeclarations", funcDeclarations);

            JsonArray toolsArr = new JsonArray();
            toolsArr.add(toolsWrapper);
            body.add("tools", toolsArr);
        }

        // Generation config
        if (config.getMaxTokens() > 0) {
            JsonObject genConfig = new JsonObject();
            genConfig.addProperty("maxOutputTokens", config.getMaxTokens());
            body.add("generationConfig", genConfig);
        }

        // Contents (conversation history)
        body.add("contents", buildContents(messages));

        return body.toString();
    }

    /**
     * Converts the conversation history into Gemini's contents format.
     *
     * <ul>
     *   <li>USER messages: {@code {"role":"user","parts":[{"text":"..."}]}}</li>
     *   <li>ASSISTANT messages: {@code {"role":"model","parts":[{"text":"..."},{"functionCall":{...}}]}}</li>
     *   <li>TOOL messages: {@code {"role":"user","parts":[{"functionResponse":{...}}]}}</li>
     * </ul>
     *
     * Note: Gemini uses "model" instead of "assistant" for the assistant role.
     */
    private JsonArray buildContents(List<ChatMessage> messages) {
        JsonArray contents = new JsonArray();
        for (ChatMessage msg : messages) {
            switch (msg.getRole()) {
                case USER:
                    contents.add(buildUserContent(msg));
                    break;
                case ASSISTANT:
                    contents.add(buildModelContent(msg));
                    break;
                case TOOL:
                    contents.add(buildToolResultContent(msg));
                    break;
            }
        }
        return contents;
    }

    private JsonObject buildUserContent(ChatMessage msg) {
        JsonObject obj = new JsonObject();
        obj.addProperty("role", "user");

        JsonArray parts = new JsonArray();
        if (msg.getTextContent() != null && !msg.getTextContent().isEmpty()) {
            JsonObject textPart = new JsonObject();
            textPart.addProperty("text", msg.getTextContent());
            parts.add(textPart);
        }

        obj.add("parts", parts);
        return obj;
    }

    private JsonObject buildModelContent(ChatMessage msg) {
        JsonObject obj = new JsonObject();
        obj.addProperty("role", "model");

        JsonArray parts = new JsonArray();

        if (msg.getTextContent() != null && !msg.getTextContent().isEmpty()) {
            JsonObject textPart = new JsonObject();
            textPart.addProperty("text", msg.getTextContent());
            parts.add(textPart);
        }

        // Function calls
        if (msg.hasToolCalls()) {
            for (ToolCall tc : msg.getToolCalls()) {
                JsonObject fcPart = new JsonObject();
                JsonObject functionCall = new JsonObject();
                functionCall.addProperty("name", tc.getName());
                functionCall.add("args", tc.getArguments() != null ? tc.getArguments() : new JsonObject());
                fcPart.add("functionCall", functionCall);
                parts.add(fcPart);
            }
        }

        obj.add("parts", parts);
        return obj;
    }

    /**
     * Tool results in Gemini are sent as a user-role content with functionResponse parts.
     */
    private JsonObject buildToolResultContent(ChatMessage msg) {
        JsonObject obj = new JsonObject();
        obj.addProperty("role", "user");

        JsonArray parts = new JsonArray();
        for (ToolResult tr : msg.getToolResults()) {
            JsonObject frPart = new JsonObject();
            JsonObject functionResponse = new JsonObject();
            functionResponse.addProperty("name", tr.getToolCallId());

            JsonObject responseObj = new JsonObject();
            responseObj.addProperty("content", tr.getContent());
            if (tr.isError()) {
                responseObj.addProperty("error", true);
            }
            functionResponse.add("response", responseObj);

            frPart.add("functionResponse", functionResponse);
            parts.add(frPart);
        }

        obj.add("parts", parts);
        return obj;
    }

    // -- Response parsing ---------------------------------------------------

    private ChatMessage parseResponse(String responseBody) throws LlmException {
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();

            // Check for top-level error
            if (json.has("error")) {
                JsonObject error = json.getAsJsonObject("error");
                String errorMsg = error.has("message") ? error.get("message").getAsString() : "Unknown error";
                throw new LlmException("Gemini API error: " + errorMsg);
            }

            JsonArray candidates = json.getAsJsonArray("candidates");
            if (candidates == null || candidates.size() == 0) {
                throw new LlmException("No candidates returned in Gemini response");
            }

            JsonObject candidate = candidates.get(0).getAsJsonObject();
            JsonObject content = candidate.getAsJsonObject("content");
            JsonArray parts = content.getAsJsonArray("parts");

            StringBuilder textContent = new StringBuilder();
            List<ToolCall> toolCalls = new ArrayList<>();

            if (parts != null) {
                for (JsonElement partEl : parts) {
                    JsonObject part = partEl.getAsJsonObject();

                    if (part.has("text")) {
                        if (textContent.length() > 0) {
                            textContent.append("\n");
                        }
                        textContent.append(part.get("text").getAsString());
                    }

                    if (part.has("functionCall")) {
                        JsonObject fc = part.getAsJsonObject("functionCall");
                        String name = fc.get("name").getAsString();
                        JsonObject args = fc.has("args") && fc.get("args").isJsonObject()
                                ? fc.getAsJsonObject("args")
                                : new JsonObject();
                        // Gemini doesn't provide tool call IDs, so we generate one
                        String id = "gemini_call_" + name + "_" + System.nanoTime();
                        toolCalls.add(new ToolCall(id, name, args));
                    }
                }
            }

            String text = textContent.length() > 0 ? textContent.toString() : null;
            ChatMessage msg = new ChatMessage(ChatMessage.Role.ASSISTANT, text, toolCalls, null);

            // Extract token usage from usageMetadata
            if (json.has("usageMetadata") && json.get("usageMetadata").isJsonObject()) {
                msg.setUsage(LlmUsage.fromGeminiJson(json.getAsJsonObject("usageMetadata")));
            }

            return msg;

        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("Failed to parse Gemini response: " + e.getMessage(), e);
        }
    }
}
