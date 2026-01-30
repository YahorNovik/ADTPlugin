package com.sap.ai.assistant.mcp;

import com.google.gson.JsonObject;
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

    @Override
    public ToolResult execute(JsonObject arguments) throws Exception {
        try {
            String result = client.callTool(mcpToolName, arguments);
            return ToolResult.success(null, result);
        } catch (McpException e) {
            return ToolResult.error(null, "MCP tool error: " + e.getMessage());
        }
    }

    /**
     * Returns the prefixed tool name for an MCP tool.
     */
    public static String prefixedName(String mcpToolName) {
        return PREFIX + mcpToolName;
    }
}
