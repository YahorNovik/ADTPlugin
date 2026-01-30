package com.sap.ai.assistant.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.JsonObject;
import com.sap.ai.assistant.llm.LlmException;
import com.sap.ai.assistant.llm.LlmProvider;
import com.sap.ai.assistant.model.ChatConversation;
import com.sap.ai.assistant.model.ChatMessage;
import com.sap.ai.assistant.model.DiffRequest;
import com.sap.ai.assistant.model.ToolCall;
import com.sap.ai.assistant.model.ToolDefinition;
import com.sap.ai.assistant.model.ToolResult;
import com.sap.ai.assistant.sap.AdtRestClient;
import com.sap.ai.assistant.tools.SapTool;
import com.sap.ai.assistant.tools.SapToolRegistry;
import com.sap.ai.assistant.tools.SetSourceTool;
import com.sap.ai.assistant.tools.WriteAndCheckTool;

/**
 * Core agentic loop that orchestrates the interaction between the LLM and SAP tools.
 * <p>
 * The loop repeatedly sends the conversation to the LLM, checks whether the response
 * contains tool calls, executes those tools, feeds the results back into the conversation,
 * and continues until the LLM produces a final text-only response or the maximum number
 * of tool rounds is exceeded.
 * </p>
 * <p>
 * This class is designed to be invoked from a background thread (e.g. an Eclipse Job)
 * and communicates progress through an {@link AgentCallback}.
 * </p>
 */
public class AgentLoop {

    /**
     * Maximum number of tool-call rounds before the loop is forcibly terminated.
     * This prevents runaway loops where the LLM keeps requesting tool calls indefinitely.
     */
    public static final int MAX_TOOL_ROUNDS = 20;

    private final LlmProvider llmProvider;
    private final SapToolRegistry toolRegistry;
    private final AdtRestClient restClient;

    /**
     * Creates a new agent loop.
     *
     * @param llmProvider  the LLM provider to use for generating responses
     * @param toolRegistry the registry of available SAP tools
     * @param restClient   the ADT REST client (nullable; needed for diff preview)
     */
    public AgentLoop(LlmProvider llmProvider, SapToolRegistry toolRegistry, AdtRestClient restClient) {
        this.llmProvider = llmProvider;
        this.toolRegistry = toolRegistry;
        this.restClient = restClient;
    }

    /**
     * Creates a new agent loop without a REST client (no diff preview support).
     */
    public AgentLoop(LlmProvider llmProvider, SapToolRegistry toolRegistry) {
        this(llmProvider, toolRegistry, null);
    }

    /**
     * Runs the agentic loop on the given conversation.
     * <p>
     * This method is <b>blocking</b> and should be called from a background thread
     * (e.g. an Eclipse {@code Job}). Progress and results are reported through the
     * {@link AgentCallback}.
     * </p>
     * <p>
     * The loop proceeds as follows:
     * <ol>
     *   <li>Retrieve the conversation messages, system prompt, and tool definitions.</li>
     *   <li>Send the conversation to the LLM.</li>
     *   <li>If the response contains no tool calls, report completion and return.</li>
     *   <li>If the response contains tool calls, execute each tool, collect results,
     *       add them to the conversation, and loop back to step 2.</li>
     *   <li>If the maximum number of rounds is exceeded, report an error and return.</li>
     * </ol>
     * </p>
     *
     * @param conversation the conversation state (modified in place)
     * @param callback     the callback for progress notifications
     */
    public void run(ChatConversation conversation, AgentCallback callback) {
        try {
            List<ToolDefinition> toolDefinitions = (toolRegistry != null)
                    ? toolRegistry.getAllDefinitions()
                    : Collections.emptyList();

            for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
                // Check for thread interruption (supports Eclipse Job cancellation)
                if (Thread.currentThread().isInterrupted()) {
                    callback.onError(new InterruptedException("Agent loop was cancelled"));
                    return;
                }

                // 1. Send conversation to LLM
                ChatMessage response;
                try {
                    response = llmProvider.sendMessage(
                            conversation.getMessages(),
                            conversation.getSystemPrompt(),
                            toolDefinitions);
                } catch (LlmException e) {
                    callback.onError(e);
                    return;
                }

                // 2. If no tool calls, this is the final response
                if (!response.hasToolCalls()) {
                    conversation.addAssistantMessage(response);

                    // Notify with the text content if present
                    if (response.getTextContent() != null && !response.getTextContent().isEmpty()) {
                        callback.onTextToken(response.getTextContent());
                    }

                    callback.onComplete(response);
                    return;
                }

                // 3. Response has tool calls -- add assistant message to conversation
                conversation.addAssistantMessage(response);

                // Emit any text the assistant included alongside tool calls
                if (response.getTextContent() != null && !response.getTextContent().isEmpty()) {
                    callback.onTextToken(response.getTextContent());
                }

                // 4. Execute each tool call
                List<ToolResult> results = new ArrayList<>();
                for (ToolCall toolCall : response.getToolCalls()) {
                    callback.onToolCallStart(toolCall);

                    ToolResult result = executeTool(toolCall, callback);
                    results.add(result);

                    callback.onToolCallEnd(result);
                }

                // 5. Build tool results message and add to conversation
                ChatMessage toolResultsMessage = ChatMessage.toolResults(results);
                conversation.addAssistantMessage(toolResultsMessage);
            }

            // Maximum rounds exceeded
            callback.onError(new Exception(
                    "Maximum tool rounds exceeded (" + MAX_TOOL_ROUNDS + "). "
                    + "The agent was unable to produce a final response within the allowed "
                    + "number of iterations. This may indicate a loop in the tool usage pattern."));

        } catch (Exception e) {
            callback.onError(e);
        }
    }

    /**
     * Executes a single tool call by looking up the tool in the registry and
     * invoking it with the provided arguments. Write tools are intercepted
     * for diff approval when a REST client is available.
     *
     * @param toolCall the tool call to execute
     * @param callback the callback for diff approval notifications
     * @return the tool result (always non-null)
     */
    private ToolResult executeTool(ToolCall toolCall, AgentCallback callback) {
        String toolName = toolCall.getName();

        if (toolRegistry == null) {
            return ToolResult.error(toolCall.getId(),
                    "No tools available. Connect to a SAP system or configure an MCP server "
                    + "in Preferences. Cannot execute tool: '" + toolName + "'.");
        }

        SapTool tool = toolRegistry.get(toolName);

        if (tool == null) {
            return ToolResult.error(toolCall.getId(),
                    "Unknown tool: '" + toolName + "'. "
                    + "Please use one of the available tools.");
        }

        // Intercept write tools for diff approval
        if (restClient != null && isWriteTool(toolName)) {
            return executeWithDiffApproval(toolCall, tool, callback);
        }

        return executeToolDirectly(toolCall, tool);
    }

    private ToolResult executeToolDirectly(ToolCall toolCall, SapTool tool) {
        try {
            ToolResult result = tool.execute(toolCall.getArguments());
            return ensureToolCallId(toolCall, result);
        } catch (Exception e) {
            return ToolResult.error(toolCall.getId(),
                    "Tool execution failed: " + e.getClass().getSimpleName()
                    + " - " + e.getMessage());
        }
    }

    private ToolResult ensureToolCallId(ToolCall toolCall, ToolResult result) {
        if (result.getToolCallId() == null || result.getToolCallId().isEmpty()) {
            if (result.isError()) {
                return ToolResult.error(toolCall.getId(), result.getContent());
            } else {
                return ToolResult.success(toolCall.getId(), result.getContent());
            }
        }
        return result;
    }

    private boolean isWriteTool(String name) {
        return SetSourceTool.NAME.equals(name) || WriteAndCheckTool.NAME.equals(name);
    }

    /**
     * Intercepts a write tool call, fetches the current source, shows a diff
     * preview to the user, and blocks until the user accepts, rejects, or edits.
     */
    private ToolResult executeWithDiffApproval(ToolCall toolCall, SapTool tool, AgentCallback callback) {
        try {
            JsonObject args = toolCall.getArguments();

            // Extract the proposed new source
            String newSource = args.has("source") ? args.get("source").getAsString() : "";

            // Resolve source URL and object name depending on the tool
            String sourceUrl = resolveSourceUrl(toolCall.getName(), args);
            String objectName = resolveObjectName(toolCall.getName(), args);

            // Fetch current source from SAP (empty string if object is new)
            String oldSource = fetchCurrentSource(sourceUrl);

            // Build diff request and notify UI
            DiffRequest diffRequest = new DiffRequest(
                    toolCall.getId(), toolCall.getName(),
                    objectName, sourceUrl, oldSource, newSource);

            callback.onDiffApprovalNeeded(diffRequest);

            // Block until user decides
            diffRequest.awaitDecision();

            switch (diffRequest.getDecision()) {
                case ACCEPTED:
                    return ensureToolCallId(toolCall, tool.execute(args));

                case EDITED:
                    // Replace source in arguments with the edited version
                    JsonObject modifiedArgs = args.deepCopy();
                    modifiedArgs.addProperty("source", diffRequest.getFinalSource());
                    return ensureToolCallId(toolCall, tool.execute(modifiedArgs));

                case REJECTED:
                    return ToolResult.success(toolCall.getId(),
                            "User rejected the proposed changes. The source was NOT modified. "
                            + "Ask the user what they would like instead.");

                default:
                    return ToolResult.error(toolCall.getId(), "Diff approval was not resolved.");
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.error(toolCall.getId(),
                    "Operation was cancelled by the user.");
        } catch (Exception e) {
            return ToolResult.error(toolCall.getId(),
                    "Diff approval failed: " + e.getClass().getSimpleName()
                    + " - " + e.getMessage());
        }
    }

    private String resolveSourceUrl(String toolName, JsonObject args) {
        if (SetSourceTool.NAME.equals(toolName)) {
            return args.has("objectSourceUrl") ? args.get("objectSourceUrl").getAsString() : "";
        }
        // For WriteAndCheckTool, derive from type and name
        if (args.has("name") && args.has("objtype")) {
            String name = args.get("name").getAsString().toLowerCase();
            String objtype = args.get("objtype").getAsString().toUpperCase();
            if (objtype.startsWith("PROG")) {
                return "/sap/bc/adt/programs/programs/" + name + "/source/main";
            } else if (objtype.startsWith("CLAS")) {
                return "/sap/bc/adt/oo/classes/" + name + "/source/main";
            } else if (objtype.startsWith("INTF")) {
                return "/sap/bc/adt/oo/interfaces/" + name + "/source/main";
            } else if (objtype.startsWith("FUGR")) {
                return "/sap/bc/adt/functions/groups/" + name + "/source/main";
            }
        }
        return "";
    }

    private String resolveObjectName(String toolName, JsonObject args) {
        if (SetSourceTool.NAME.equals(toolName)) {
            String url = args.has("objectSourceUrl") ? args.get("objectSourceUrl").getAsString() : "";
            // Extract name from URL like /sap/bc/adt/programs/programs/ztest/source/main
            String[] parts = url.split("/");
            for (int i = parts.length - 1; i >= 0; i--) {
                if (!"source".equals(parts[i]) && !"main".equals(parts[i]) && !parts[i].isEmpty()) {
                    return parts[i].toUpperCase();
                }
            }
            return url;
        }
        return args.has("name") ? args.get("name").getAsString() : "unknown";
    }

    private String fetchCurrentSource(String sourceUrl) {
        if (restClient == null || sourceUrl == null || sourceUrl.isEmpty()) {
            return "";
        }
        try {
            java.net.http.HttpResponse<String> resp = restClient.get(sourceUrl, "text/plain");
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                return resp.body() != null ? resp.body() : "";
            }
        } catch (Exception e) {
            // Object may not exist yet — treat as empty
        }
        return "";
    }

    /**
     * Returns the LLM provider used by this agent loop.
     *
     * @return the LLM provider
     */
    public LlmProvider getLlmProvider() {
        return llmProvider;
    }

    /**
     * Returns the tool registry used by this agent loop.
     *
     * @return the tool registry
     */
    public SapToolRegistry getToolRegistry() {
        return toolRegistry;
    }
}
