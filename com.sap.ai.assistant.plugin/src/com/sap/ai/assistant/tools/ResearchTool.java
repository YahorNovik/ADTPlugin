package com.sap.ai.assistant.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.ai.assistant.agent.AgentCallback;
import com.sap.ai.assistant.agent.AgentLoop;
import com.sap.ai.assistant.llm.LlmProvider;
import com.sap.ai.assistant.model.ChatConversation;
import com.sap.ai.assistant.model.ChatMessage;
import com.sap.ai.assistant.model.DiffRequest;
import com.sap.ai.assistant.model.RequestLogEntry;
import com.sap.ai.assistant.model.ToolCall;
import com.sap.ai.assistant.model.ToolDefinition;
import com.sap.ai.assistant.model.ToolResult;

/**
 * A tool that delegates research queries to a specialized sub-agent.
 * <p>
 * The sub-agent runs its own {@link AgentLoop} with a focused system prompt
 * and a restricted set of tools (SAP read-only tools + MCP documentation tools).
 * It uses potentially a cheaper/faster LLM model for cost efficiency.
 * </p>
 * <p>
 * The main agent invokes this tool when it needs to look up SAP documentation,
 * ABAP keyword reference, or read existing code to understand how something works.
 * </p>
 */
public class ResearchTool implements SapTool {

    public static final String NAME = "research";

    private static final int DEFAULT_MAX_ROUNDS = 10;
    private static final int DEFAULT_MAX_INPUT_TOKENS = 20_000;

    private static final String SYSTEM_PROMPT =
            "You are a research assistant for SAP ABAP development. Your job is to find "
            + "information using the available documentation and code-reading tools, then "
            + "provide a clear, concise answer.\n\n"
            + "## Available Tools\n\n"
            + "**SAP Read Tools:**\n"
            + "- `sap_search_object` — search for ABAP objects by name pattern and type\n"
            + "- `sap_get_source` — read source code of any ABAP object (see URL patterns below)\n"
            + "- `sap_object_structure` — get the structure/hierarchy of an ABAP object\n"
            + "- `sap_node_contents` — list children of a package or namespace\n"
            + "- `sap_find_definition` — navigate to where a symbol is defined\n"
            + "- `sap_usage_references` — find all places where a symbol is used\n"
            + "- `sap_type_info` — get DDIC type info (data element, domain, table type) by name\n\n"
            + "**MCP Documentation Tools** (prefixed with `mcp_`):\n"
            + "These tools search SAP documentation, ABAP keyword reference, and SAP Help Portal.\n\n"
            + "## ADT URL Patterns for sap_get_source\n\n"
            + "Use these URLs with `sap_get_source` to read DDIC object definitions:\n"
            + "- Table fields: `/sap/bc/adt/ddic/tables/{table}/source/main` (e.g. `.../tables/mara/source/main`)\n"
            + "- Structure: `/sap/bc/adt/ddic/structures/{struct}/source/main`\n"
            + "- Data element: `/sap/bc/adt/ddic/dataelements/{dtel}/source/main`\n"
            + "- Domain: `/sap/bc/adt/ddic/domains/{domain}/source/main`\n"
            + "- Class: `/sap/bc/adt/oo/classes/{class}/source/main`\n"
            + "- Interface: `/sap/bc/adt/oo/interfaces/{intf}/source/main`\n"
            + "- Program: `/sap/bc/adt/programs/programs/{prog}/source/main`\n"
            + "- Function module: `/sap/bc/adt/functions/groups/{group}/fmodules/{fm}/source/main`\n\n"
            + "**IMPORTANT:** Object names in URLs must be lowercase.\n\n"
            + "## Guidelines\n\n"
            + "- To look up a table's fields, use `sap_get_source` with the table's source URL.\n"
            + "- To look up a data element or domain, use `sap_type_info` with its name.\n"
            + "- Always cite which tool/source provided the information.\n"
            + "- If the first search doesn't find what you need, try different search terms or URL patterns.\n"
            + "- Summarize findings concisely so the calling agent can act on them.\n";

    private final LlmProvider llmProvider;
    private final SapToolRegistry toolRegistry;
    private final int maxRounds;
    private final int maxInputTokens;
    private final ToolDefinition definition;

    /**
     * Creates a new research tool.
     *
     * @param llmProvider  the LLM provider for the sub-agent (can be a cheaper model)
     * @param toolRegistry the tool registry containing read-only SAP tools and MCP tools
     */
    public ResearchTool(LlmProvider llmProvider, SapToolRegistry toolRegistry) {
        this(llmProvider, toolRegistry, DEFAULT_MAX_ROUNDS, DEFAULT_MAX_INPUT_TOKENS);
    }

    /**
     * Creates a new research tool with custom limits.
     *
     * @param llmProvider    the LLM provider for the sub-agent
     * @param toolRegistry   the tool registry for the sub-agent
     * @param maxRounds      maximum tool-call rounds for the sub-agent
     * @param maxInputTokens maximum cumulative input tokens for the sub-agent
     */
    public ResearchTool(LlmProvider llmProvider, SapToolRegistry toolRegistry,
                        int maxRounds, int maxInputTokens) {
        this.llmProvider = llmProvider;
        this.toolRegistry = toolRegistry;
        this.maxRounds = maxRounds;
        this.maxInputTokens = maxInputTokens;
        this.definition = buildDefinition();
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ToolDefinition getDefinition() {
        return definition;
    }

    @Override
    public ToolResult execute(JsonObject arguments) throws Exception {
        String query = null;
        if (arguments != null && arguments.has("query")) {
            query = arguments.get("query").getAsString();
        }
        if (query == null || query.trim().isEmpty()) {
            return ToolResult.error(null, "Missing required parameter 'query'.");
        }

        // Build a mini conversation for the sub-agent
        ChatConversation conversation = new ChatConversation(SYSTEM_PROMPT);
        conversation.addUserMessage(query);

        // Run the sub-agent loop
        AgentLoop subLoop = new AgentLoop(
                llmProvider, toolRegistry, null, null,
                maxRounds, maxInputTokens);

        CollectingCallback callback = new CollectingCallback();
        subLoop.run(conversation, callback);

        if (callback.error != null) {
            return ToolResult.error(null, "Research failed: " + callback.error);
        }
        if (callback.result != null && !callback.result.isEmpty()) {
            return ToolResult.success(null, callback.result);
        }
        return ToolResult.error(null, "Research sub-agent produced no response.");
    }

    // ------------------------------------------------------------------
    // Tool definition
    // ------------------------------------------------------------------

    private static ToolDefinition buildDefinition() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();
        JsonObject queryProp = new JsonObject();
        queryProp.addProperty("type", "string");
        queryProp.addProperty("description",
                "The research question to investigate. Be specific about what you need to find.");
        properties.add("query", queryProp);
        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("query");
        schema.add("required", required);

        return new ToolDefinition(
                NAME,
                "Delegate a research query to a specialized sub-agent that can search SAP "
                + "documentation, ABAP keyword reference, SAP Help Portal, and read SAP object "
                + "source code. Use this when you need to look up API documentation, ABAP syntax, "
                + "SAP class/interface details, or understand how existing code works.",
                schema);
    }

    // ------------------------------------------------------------------
    // Collecting callback (inner class)
    // ------------------------------------------------------------------

    /**
     * A simple callback that captures the sub-agent's final text response or error.
     * All other events (tool calls, diff approval) are no-ops.
     */
    private static class CollectingCallback implements AgentCallback {

        String result;
        String error;

        @Override
        public void onTextToken(String token) {
            // Accumulate text tokens
            if (result == null) {
                result = token;
            } else {
                result += token;
            }
        }

        @Override
        public void onToolCallStart(ToolCall toolCall) {
            // No-op for sub-agent
        }

        @Override
        public void onToolCallEnd(ToolResult toolResult) {
            // No-op for sub-agent
        }

        @Override
        public void onDiffApprovalNeeded(DiffRequest diffRequest) {
            // Sub-agent should never request diff approval (no write tools)
            // Auto-reject to prevent blocking
            diffRequest.setDecision(DiffRequest.Decision.REJECTED);
        }

        @Override
        public void onRequestComplete(RequestLogEntry entry) {
            // No-op for sub-agent
        }

        @Override
        public void onComplete(ChatMessage finalMessage) {
            if (finalMessage != null && finalMessage.getTextContent() != null) {
                result = finalMessage.getTextContent();
            }
        }

        @Override
        public void onError(Exception e) {
            error = e.getMessage();
        }
    }
}
