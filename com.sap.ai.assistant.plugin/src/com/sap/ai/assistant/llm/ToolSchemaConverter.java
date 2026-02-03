package com.sap.ai.assistant.llm;

import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sap.ai.assistant.model.ToolDefinition;

/**
 * Static utility that converts {@link ToolDefinition} instances into the
 * provider-specific JSON representations expected by each LLM API.
 */
public final class ToolSchemaConverter {

    private ToolSchemaConverter() {
        // Utility class — no instances
    }

    // -- Anthropic ----------------------------------------------------------

    /**
     * Converts a tool definition to the Anthropic Messages API format:
     * <pre>{@code
     * { "name": "...", "description": "...", "input_schema": { ... } }
     * }</pre>
     *
     * @param tool the tool definition
     * @return the Anthropic-formatted JSON object
     */
    public static JsonObject toAnthropicTool(ToolDefinition tool) {
        JsonObject obj = new JsonObject();
        obj.addProperty("name", tool.getName());
        obj.addProperty("description", tool.getDescription());
        obj.add("input_schema", tool.getParametersSchema() != null
                ? tool.getParametersSchema().deepCopy()
                : new JsonObject());
        return obj;
    }

    // -- OpenAI / Mistral ---------------------------------------------------

    /**
     * Converts a tool definition to the OpenAI Chat Completions API format:
     * <pre>{@code
     * { "type": "function", "function": { "name": "...", "description": "...", "parameters": { ... } } }
     * }</pre>
     * This format is also used by Mistral's API.
     *
     * @param tool the tool definition
     * @return the OpenAI-formatted JSON object
     */
    public static JsonObject toOpenAiTool(ToolDefinition tool) {
        JsonObject fn = new JsonObject();
        fn.addProperty("name", tool.getName());
        fn.addProperty("description", tool.getDescription());
        fn.add("parameters", tool.getParametersSchema() != null
                ? tool.getParametersSchema().deepCopy()
                : new JsonObject());

        JsonObject obj = new JsonObject();
        obj.addProperty("type", "function");
        obj.add("function", fn);
        return obj;
    }

    // -- Gemini -------------------------------------------------------------

    /**
     * Converts a tool definition to the Google Gemini API format:
     * <pre>{@code
     * { "name": "...", "description": "...", "parameters": { ... } }
     * }</pre>
     * The parameters schema has all type values converted to UPPERCASE
     * (e.g. "string" becomes "STRING") as required by the Gemini API.
     *
     * @param tool the tool definition
     * @return the Gemini-formatted JSON object
     */
    public static JsonObject toGeminiTool(ToolDefinition tool) {
        JsonObject obj = new JsonObject();
        obj.addProperty("name", tool.getName());
        obj.addProperty("description", tool.getDescription());
        if (tool.getParametersSchema() != null) {
            obj.add("parameters", toGeminiSchema(tool.getParametersSchema().deepCopy()));
        } else {
            obj.add("parameters", new JsonObject());
        }
        return obj;
    }

    /**
     * Recursively converts a JSON Schema object so that all {@code "type"} values
     * use UPPERCASE names as required by the Gemini API (e.g. "string" becomes
     * "STRING", "object" becomes "OBJECT"). Also strips unsupported fields like
     * "examples", "$schema", "additionalProperties", etc.
     *
     * @param schema the original JSON Schema object
     * @return a new JSON object with upper-cased type values and unsupported fields removed
     */
    public static JsonObject toGeminiSchema(JsonObject schema) {
        JsonObject result = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : schema.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();

            // Skip fields not supported by Gemini
            if (isUnsupportedGeminiField(key)) {
                continue;
            }

            if ("type".equals(key) && value.isJsonPrimitive()) {
                // Convert type names to UPPERCASE
                result.addProperty(key, value.getAsString().toUpperCase());
            } else if (value.isJsonObject()) {
                // Recurse into nested objects (e.g. "properties", "items")
                result.add(key, toGeminiSchema(value.getAsJsonObject()));
            } else if (value.isJsonArray()) {
                // Recurse into array elements (e.g. "anyOf", "oneOf")
                JsonArray arr = value.getAsJsonArray();
                JsonArray convertedArr = new JsonArray();
                for (JsonElement el : arr) {
                    if (el.isJsonObject()) {
                        convertedArr.add(toGeminiSchema(el.getAsJsonObject()));
                    } else {
                        convertedArr.add(el);
                    }
                }
                result.add(key, convertedArr);
            } else {
                result.add(key, value);
            }
        }
        return result;
    }

    /**
     * Returns true if the given JSON Schema field is not supported by Gemini's
     * function declaration format.
     */
    private static boolean isUnsupportedGeminiField(String fieldName) {
        switch (fieldName) {
            case "examples":
            case "$schema":
            case "additionalProperties":
            case "default":
            case "title":
            case "$id":
            case "$ref":
            case "definitions":
            case "$defs":
                return true;
            default:
                return false;
        }
    }
}
