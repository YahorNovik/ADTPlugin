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
    private static final int DEFAULT_MAX_INPUT_TOKENS = 50_000;

    public static final String SYSTEM_PROMPT =
            "You are a research assistant for SAP ABAP development. Your job is to find "
            + "information using the available tools, then provide a clear, concise answer.\n\n"
            + "## ADT URL Patterns for sap_get_source\n\n"
            + "Use `sap_get_source` to read source code and field definitions. "
            + "Object names in URLs MUST be lowercase:\n"
            + "- **Table fields**: `/sap/bc/adt/ddic/tables/{table}/source/main` (e.g. `.../tables/mara/source/main`)\n"
            + "- **Structure fields**: `/sap/bc/adt/ddic/structures/{struct}/source/main`\n"
            + "- **CDS view (DDL)**: `/sap/bc/adt/ddic/ddl/sources/{view}/source/main`\n"
            + "- **Data element**: `/sap/bc/adt/ddic/dataelements/{dtel}/source/main`\n"
            + "- **Domain**: `/sap/bc/adt/ddic/domains/{domain}/source/main`\n"
            + "- **Class**: `/sap/bc/adt/oo/classes/{class}/source/main`\n"
            + "- **Interface**: `/sap/bc/adt/oo/interfaces/{intf}/source/main`\n"
            + "- **Program**: `/sap/bc/adt/programs/programs/{prog}/source/main`\n"
            + "- **Function module**: `/sap/bc/adt/functions/groups/{group}/fmodules/{fm}/source/main`\n\n"
            + "## Reading Data vs Structure\n\n"
            + "These two tools serve DIFFERENT purposes — do NOT confuse them:\n\n"
            + "| Need | Tool | Example |\n"
            + "|------|------|---------|\n"
            + "| **Table/structure field definitions** | `sap_get_source` | `.../tables/mara/source/main` → returns field names, types, lengths |\n"
            + "| **Actual data rows from a table** | `sap_sql_query` | `SELECT matnr, mtart FROM mara UP TO 10 ROWS` → returns row values |\n"
            + "| **Data element/domain details** | `sap_type_info` | name='MATNR' → returns domain, data type, length, labels |\n\n"
            + "`sap_sql_query` executes real ABAP SQL against the database and returns DATA ROWS. "
            + "It does NOT return table structure or field definitions. "
            + "Example: `SELECT carrid, connid, fldate FROM sflight UP TO 5 ROWS`.\n\n"
            + "## SAP Documentation (MCP Tools)\n\n"
            + "Use these tools to look up SAP documentation:\n"
            + "- `mcp_sap_help_search` — search SAP Help Portal (ABAP keyword reference, "
            + "transactions, BAdIs, configuration, enhancement spots)\n"
            + "- `mcp_sap_help_get` — fetch full SAP Help page content after finding a result\n"
            + "- `mcp_sap_community_search` — search SAP Community for blog posts, "
            + "real-world examples, and solutions\n"
            + "- `mcp_sap_docs_search` — search SAPUI5, CAP, and OpenUI5 documentation\n"
            + "- `mcp_sap_docs_get` — fetch full documentation page content\n\n"
            + "**Workflow**: Search first → then fetch full content for relevant results.\n\n"
            + "## Guidelines\n\n"
            + "- Always cite which tool/source provided the information.\n"
            + "- If the first search doesn't find what you need, try different search terms.\n"
            + "- Summarize findings concisely so the calling agent can act on them.\n";

    private final LlmProvider llmProvider;
    private final SapToolRegistry toolRegistry;
    private final int maxRounds;
    private final int maxInputTokens;
    private final ToolDefinition definition;
    private AgentCallback parentCallback;

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

    /**
     * Sets the parent callback so sub-agent log entries can be forwarded
     * to the main agent's DevLog.
     */
    public void setParentCallback(AgentCallback callback) {
        this.parentCallback = callback;
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

        CollectingCallback callback = new CollectingCallback(parentCallback);
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

        private final AgentCallback parent;
        String result;
        String error;

        CollectingCallback(AgentCallback parent) {
            this.parent = parent;
        }

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
            // Forward to parent callback for DevLog visibility
            if (parent != null) {
                parent.onRequestComplete(entry);
            }
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
