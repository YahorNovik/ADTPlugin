package com.sap.ai.assistant.mcp;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.sap.ai.assistant.model.ToolDefinition;

/**
 * Converts MCP tool definitions (from {@code tools/list}) into
 * {@link ToolDefinition} objects for the LLM tool schema system.
 * <p>
 * The MCP format is:
 * <pre>
 * {"name":"search","description":"...","inputSchema":{"type":"object","properties":{...},"required":[...]}}
 * </pre>
 * This maps directly to {@code ToolDefinition(name, description, parametersSchema)}.
 * </p>
 */
public class McpToolDefinitionParser {

    private McpToolDefinitionParser() {
    }

    /**
     * Parses a list of raw MCP tool JSON objects into {@link ToolDefinition} objects.
     * Tool names are prefixed with {@code mcp_} to avoid collisions.
     *
     * @param mcpTools raw tool definitions from MCP server
     * @return list of converted tool definitions
     */
    public static List<ToolDefinition> parse(List<JsonObject> mcpTools) {
        List<ToolDefinition> definitions = new ArrayList<>();
        for (JsonObject tool : mcpTools) {
            ToolDefinition def = parseSingle(tool);
            if (def != null) {
                definitions.add(def);
            }
        }
        return definitions;
    }

    /**
     * Parses a single MCP tool definition into a {@link ToolDefinition}.
     *
     * @param tool raw MCP tool JSON object
     * @return the converted definition, or {@code null} if the tool is malformed
     */
    public static ToolDefinition parseSingle(JsonObject tool) {
        if (tool == null) return null;

        String name = tool.has("name") ? tool.get("name").getAsString() : null;
        String description = tool.has("description") ? tool.get("description").getAsString() : "";

        if (name == null || name.isEmpty()) {
            return null;
        }

        // MCP uses "inputSchema"; our ToolDefinition uses "parametersSchema"
        JsonObject parametersSchema = null;
        if (tool.has("inputSchema") && tool.get("inputSchema").isJsonObject()) {
            parametersSchema = tool.getAsJsonObject("inputSchema");
        }

        if (parametersSchema == null) {
            // Create a minimal schema if none provided
            parametersSchema = new JsonObject();
            parametersSchema.addProperty("type", "object");
        }

        String prefixedName = McpToolAdapter.prefixedName(name);
        return new ToolDefinition(prefixedName, description, parametersSchema);
    }
}
