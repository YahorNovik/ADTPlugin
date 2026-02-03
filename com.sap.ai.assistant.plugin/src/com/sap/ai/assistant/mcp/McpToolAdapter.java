package com.sap.ai.assistant.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sap.ai.assistant.model.ToolDefinition;
import com.sap.ai.assistant.model.ToolResult;
import com.sap.ai.assistant.tools.SapTool;

/**
 * Adapts an MCP server tool into the {@link SapTool} interface so it can
 * be used alongside native ADT tools in the agent loop.
 * <p>
 * Tool names are prefixed with {@code mcp_} to avoid collisions with
 * native SAP ADT tools.
 * </p>
 */
public class McpToolAdapter implements SapTool {

    private static final String PREFIX = "mcp_";

    private final McpClient client;
    private final String mcpToolName;
    private final ToolDefinition definition;

    /**
     * Creates an adapter for a single MCP tool.
     *
     * @param client      the connected MCP client
     * @param mcpToolName the original tool name on the MCP server
     * @param definition  the converted tool definition (with prefixed name)
     */
    public McpToolAdapter(McpClient client, String mcpToolName, ToolDefinition definition) {
        this.client = client;
        this.mcpToolName = mcpToolName;
        this.definition = definition;
    }

    @Override
    public String getName() {
        return definition.getName();
    }

    @Override
    public ToolDefinition getDefinition() {
        return definition;
    }

    /** Maximum number of search results to return (to reduce token usage). */
    private static final int MAX_SEARCH_RESULTS = 10;

    @Override
    public ToolResult execute(JsonObject arguments) throws Exception {
        try {
            String result = client.callTool(mcpToolName, arguments);
            // Truncate search results to reduce token usage
            result = truncateSearchResults(result);
            return ToolResult.success(null, result);
        } catch (McpException e) {
            return ToolResult.error(null, "MCP tool error: " + e.getMessage());
        }
    }

    /**
     * Truncates search results to MAX_SEARCH_RESULTS to reduce token usage.
     * If the result is a JSON object with a "results" array, only keep the first N items.
     */
    private String truncateSearchResults(String result) {
        if (result == null || result.isEmpty()) {
            return result;
        }
        try {
            JsonElement parsed = JsonParser.parseString(result);
            if (!parsed.isJsonObject()) {
                return result;
            }
            JsonObject obj = parsed.getAsJsonObject();
            if (!obj.has("results") || !obj.get("results").isJsonArray()) {
                return result;
            }
            JsonArray results = obj.getAsJsonArray("results");
            if (results.size() <= MAX_SEARCH_RESULTS) {
                return result;
            }
            // Truncate to MAX_SEARCH_RESULTS
            JsonArray truncated = new JsonArray();
            for (int i = 0; i < MAX_SEARCH_RESULTS && i < results.size(); i++) {
                truncated.add(results.get(i));
            }
            obj.add("results", truncated);
            obj.addProperty("truncated", true);
            obj.addProperty("totalResults", results.size());
            return obj.toString();
        } catch (Exception e) {
            // If parsing fails, return original result
            return result;
        }
    }

    /**
     * Returns the prefixed tool name for an MCP tool.
     */
    public static String prefixedName(String mcpToolName) {
        return PREFIX + mcpToolName;
    }
}
