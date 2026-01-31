package com.sap.ai.assistant.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonObject;
import com.sap.ai.assistant.llm.AbstractLlmProvider;
import com.sap.ai.assistant.llm.LlmException;
import com.sap.ai.assistant.llm.LlmProvider;
import com.sap.ai.assistant.model.ChatConversation;
import com.sap.ai.assistant.model.ChatMessage;
import com.sap.ai.assistant.model.DiffRequest;
import com.sap.ai.assistant.model.LlmUsage;
import com.sap.ai.assistant.model.RequestLogEntry;
import com.sap.ai.assistant.model.ToolCall;
import com.sap.ai.assistant.model.ToolDefinition;
import com.sap.ai.assistant.model.ToolResult;
import com.sap.ai.assistant.model.LlmProviderConfig;
import com.sap.ai.assistant.sap.AdtRestClient;
import com.sap.ai.assistant.tools.AbstractSapTool;
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

    /**
     * Maximum cumulative input tokens across all rounds of a single agent run.
     * When exceeded the loop stops and returns a budget-exceeded message to the user.
     */
    public static final int MAX_INPUT_TOKENS = 50_000;

    /** Pattern to extract the human-readable message from SAP ADT XML exceptions. */
    private static final Pattern SAP_XML_MESSAGE_PATTERN =
            Pattern.compile("<message[^>]*>([^<]+)</message>");

    private final LlmProvider llmProvider;
    private final SapToolRegistry toolRegistry;
    private final AdtRestClient restClient;
    private final LlmProviderConfig config;

    /**
     * Creates a new agent loop.
     *
     * @param llmProvider  the LLM provider to use for generating responses
     * @param toolRegistry the registry of available SAP tools
     * @param restClient   the ADT REST client (nullable; needed for diff preview)
     * @param config       the LLM configuration (for logging model name)
     */
    public AgentLoop(LlmProvider llmProvider, SapToolRegistry toolRegistry,
                     AdtRestClient restClient, LlmProviderConfig config) {
        this.llmProvider = llmProvider;
        this.toolRegistry = toolRegistry;
        this.restClient = restClient;
        this.config = config;
    }

    /**
     * Creates a new agent loop.
     */
    public AgentLoop(LlmProvider llmProvider, SapToolRegistry toolRegistry, AdtRestClient restClient) {
        this(llmProvider, toolRegistry, restClient, null);
    }

    /**
     * Creates a new agent loop without a REST client (no diff preview support).
     */
    public AgentLoop(LlmProvider llmProvider, SapToolRegistry toolRegistry) {
        this(llmProvider, toolRegistry, null, null);
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

            int cumulativeInputTokens = 0;

            for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
                // Check for thread interruption (supports Eclipse Job cancellation)
                if (Thread.currentThread().isInterrupted()) {
                    callback.onError(new InterruptedException("Agent loop was cancelled"));
                    return;
                }

                // Trim conversation to prevent token snowball on long interactions
                conversation.trimMessages();

                // 1. Send conversation to LLM
                ChatMessage response;
                long requestStartMs = System.currentTimeMillis();
                int msgCountBefore = conversation.getMessages().size();
                try {
                    response = llmProvider.sendMessage(
                            conversation.getMessages(),
                            conversation.getSystemPrompt(),
                            toolDefinitions);
                } catch (LlmException e) {
                    // Log the failed request
                    long durationMs = System.currentTimeMillis() - requestStartMs;
                    emitLogEntry(callback, round, msgCountBefore, durationMs,
                            null, e.getMessage(), null, conversation);
                    callback.onError(e);
                    return;
                }

                // Track cumulative input tokens for budget enforcement
                LlmUsage usage = response.getUsage();
                if (usage != null) {
                    cumulativeInputTokens += usage.getInputTokens();
                }

                // 2. If no tool calls, this is the final response
                if (!response.hasToolCalls()) {
                    // Emit log entry (no tool details for final text response)
                    emitLogEntry(callback, round, msgCountBefore,
                            getRequestDurationMs(requestStartMs), response, null,
                            null, conversation);

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

                // 4. Execute each tool call, capturing details for logging
                List<ToolResult> results = new ArrayList<>();
                List<RequestLogEntry.ToolCallDetail> toolDetails = new ArrayList<>();

                for (ToolCall toolCall : response.getToolCalls()) {
                    callback.onToolCallStart(toolCall);

                    ToolResult result = executeTool(toolCall, callback);

                    // Compact verbose SAP XML error messages before they enter the conversation
                    if (result.isError()) {
                        result = compactErrorResult(result);
                    }

                    results.add(result);

                    // Capture tool I/O for the dev log
                    toolDetails.add(new RequestLogEntry.ToolCallDetail(
                            toolCall.getName(),
                            toolCall.getArguments() != null ? toolCall.getArguments().toString() : "",
                            result.getContent(),
                            result.isError()));

                    callback.onToolCallEnd(result);
                }

                // Emit log entry with tool call details
                emitLogEntry(callback, round, msgCountBefore,
                        getRequestDurationMs(requestStartMs), response, null,
                        toolDetails, conversation);

                // 5. Build tool results message and add to conversation
                ChatMessage toolResultsMessage = ChatMessage.toolResults(results);
                conversation.addAssistantMessage(toolResultsMessage);

                // 6. Check token budget
                if (cumulativeInputTokens > MAX_INPUT_TOKENS) {
                    System.err.println("AgentLoop: token budget exceeded ("
                            + cumulativeInputTokens + " > " + MAX_INPUT_TOKENS + "), stopping.");
                    callback.onError(new Exception(
                            "Token budget exceeded (" + cumulativeInputTokens + " input tokens used). "
                            + "The conversation was getting too long. Please start a new chat "
                            + "or simplify your request."));
                    return;
                }
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

    private String formatSyntaxMessages(com.google.gson.JsonArray messages) {
        if (messages == null || messages.size() == 0) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < messages.size(); i++) {
            JsonObject msg = messages.get(i).getAsJsonObject();
            String severity = msg.has("severity") ? msg.get("severity").getAsString() : "?";
            String text = msg.has("text") ? msg.get("text").getAsString() : "(no message)";
            String line = msg.has("line") ? msg.get("line").getAsString() : "?";
            sb.append("  Line ").append(line).append(" [").append(severity).append("]: ").append(text).append("\n");
        }
        return sb.toString();
    }

    /**
     * Intercepts a write tool call, validates syntax inline (without saving),
     * and only shows a diff preview to the user when the code is error-free.
     * Blocks until the user accepts, rejects, or edits.
     */
    private ToolResult executeWithDiffApproval(ToolCall toolCall, SapTool tool, AgentCallback callback) {
        try {
            JsonObject args = toolCall.getArguments();

            // Extract the proposed new source
            String newSource = args.has("source") ? args.get("source").getAsString() : "";

            // Resolve source URL and object name depending on the tool
            String sourceUrl = resolveSourceUrl(toolCall.getName(), args);
            String objectName = resolveObjectName(toolCall.getName(), args);

            // Safety-net: validate syntax BEFORE showing diff to the user.
            // If the code has errors, return them to the LLM without showing
            // a diff or writing anything. The LLM should fix and retry.
            String syntaxErrors = validateSourceBeforeWrite(sourceUrl, newSource);
            if (syntaxErrors != null) {
                return ToolResult.error(toolCall.getId(),
                        "Syntax errors detected in proposed source code. "
                        + "Fix these errors and call the write tool again:\n" + syntaxErrors);
            }

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
            String raw = args.has("objectSourceUrl") ? args.get("objectSourceUrl").getAsString() : "";
            return AbstractSapTool.ensureSourceUrl(raw);
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
     * Validates proposed source code using {@code sap_syntax_check} with the
     * {@code content} parameter (inline check, no save to repository).
     *
     * @return formatted error string if syntax errors found, or {@code null} if clean
     */
    private String validateSourceBeforeWrite(String sourceUrl, String source) {
        if (toolRegistry == null || source == null || source.isEmpty()) {
            return null;
        }
        SapTool syntaxTool = toolRegistry.get("sap_syntax_check");
        if (syntaxTool == null) {
            return null; // Syntax check not available — skip validation
        }
        try {
            JsonObject checkArgs = new JsonObject();
            checkArgs.addProperty("url", sourceUrl);
            checkArgs.addProperty("content", source); // Inline check — no save
            ToolResult checkResult = syntaxTool.execute(checkArgs);
            if (checkResult == null || checkResult.isError()) {
                return null; // Check failed — don't block the write
            }
            String content = checkResult.getContent();
            if (content == null) {
                return null;
            }
            JsonObject json = com.google.gson.JsonParser.parseString(content).getAsJsonObject();
            if (!json.has("hasErrors") || !json.get("hasErrors").getAsBoolean()) {
                return null; // No errors
            }
            if (json.has("messages")) {
                return formatSyntaxMessages(json.getAsJsonArray("messages"));
            }
            return "Syntax errors detected (no details available).";
        } catch (Exception e) {
            // If syntax check fails for any reason, don't block the write
            System.err.println("AgentLoop: pre-write syntax validation failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Compacts a verbose error result by extracting the human-readable message
     * from SAP ADT XML exception bodies. This reduces the tokens consumed
     * when error messages re-enter the conversation on subsequent rounds.
     */
    private ToolResult compactErrorResult(ToolResult result) {
        String content = result.getContent();
        if (content == null || content.length() < 200) {
            return result; // Already short enough
        }

        // Try to extract SAP XML message
        String sapMessage = extractSapMessage(content);
        if (sapMessage != null) {
            // Preserve the HTTP method/status prefix if present
            // e.g. "Tool execution failed: IOException - HTTP 400 PUT http://... -- <xml>"
            int xmlStart = content.indexOf("<?xml");
            if (xmlStart < 0) xmlStart = content.indexOf("<exc:");
            String prefix = (xmlStart > 0) ? content.substring(0, xmlStart).trim() : "";
            String compact = prefix.isEmpty()
                    ? sapMessage
                    : prefix + " -- " + sapMessage;
            return ToolResult.error(result.getToolCallId(), compact);
        }

        // No SAP XML found; just truncate if very long
        if (content.length() > 500) {
            return ToolResult.error(result.getToolCallId(),
                    content.substring(0, 500) + "... (truncated)");
        }
        return result;
    }

    /**
     * Extracts the human-readable message from a SAP ADT XML exception body.
     * Returns {@code null} if no SAP message element is found.
     */
    private static String extractSapMessage(String text) {
        if (text == null) return null;
        Matcher m = SAP_XML_MESSAGE_PATTERN.matcher(text);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    private long getRequestDurationMs(long fallbackStartMs) {
        if (llmProvider instanceof AbstractLlmProvider) {
            return ((AbstractLlmProvider) llmProvider).getLastRequestDurationMs();
        }
        return System.currentTimeMillis() - fallbackStartMs;
    }

    private void emitLogEntry(AgentCallback callback, int round, int msgCount,
                              long durationMs, ChatMessage response, String error,
                              List<RequestLogEntry.ToolCallDetail> toolDetails,
                              ChatConversation conversation) {
        String[] toolNames = null;
        int toolCallCount = 0;
        if (response != null && response.hasToolCalls()) {
            toolCallCount = response.getToolCalls().size();
            toolNames = response.getToolCalls().stream()
                    .map(ToolCall::getName).toArray(String[]::new);
        }
        String llmText = (response != null) ? response.getTextContent() : null;
        String sysPrompt = (conversation != null) ? conversation.getSystemPrompt() : null;
        String convSnapshot = (conversation != null)
                ? formatConversationSnapshot(conversation.getMessages())
                : null;

        RequestLogEntry entry = new RequestLogEntry(
                round + 1,
                llmProvider.getProviderId(),
                config != null ? config.getModel() : "unknown",
                msgCount,
                durationMs,
                response != null ? response.getUsage() : null,
                toolCallCount,
                toolNames,
                error,
                llmText,
                toolDetails,
                sysPrompt,
                convSnapshot);
        callback.onRequestComplete(entry);
    }

    /**
     * Formats conversation messages into a readable snapshot for the dev log.
     * Tool call arguments and results are truncated to keep the snapshot
     * navigable; full details are available in the per-round tool details section.
     */
    private String formatConversationSnapshot(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            sb.append("[").append(i + 1).append("] ").append(msg.getRole().name()).append(": ");

            // Text content
            if (msg.getTextContent() != null && !msg.getTextContent().isEmpty()) {
                sb.append(msg.getTextContent());
            }

            // Tool calls (assistant requesting tools)
            if (msg.hasToolCalls()) {
                if (msg.getTextContent() != null && !msg.getTextContent().isEmpty()) {
                    sb.append("\n    ");
                }
                sb.append("-> tool_calls: ");
                for (int j = 0; j < msg.getToolCalls().size(); j++) {
                    if (j > 0) sb.append(", ");
                    ToolCall tc = msg.getToolCalls().get(j);
                    sb.append(tc.getName());
                    if (tc.getArguments() != null) {
                        String args = tc.getArguments().toString();
                        sb.append("(").append(truncate(args, 200)).append(")");
                    }
                }
            }

            // Tool results
            if (!msg.getToolResults().isEmpty()) {
                for (ToolResult tr : msg.getToolResults()) {
                    sb.append("\n    ");
                    if (tr.isError()) sb.append("[ERROR] ");
                    sb.append(truncate(tr.getContent(), 300));
                }
            }

            sb.append("\n");
        }
        return sb.toString();
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...(" + s.length() + " chars)";
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
