package com.sap.ai.assistant.agent;

import java.util.ArrayList;
import java.util.List;

import com.sap.ai.assistant.llm.LlmException;
import com.sap.ai.assistant.llm.LlmProvider;
import com.sap.ai.assistant.model.ChatConversation;
import com.sap.ai.assistant.model.ChatMessage;
import com.sap.ai.assistant.model.ToolCall;
import com.sap.ai.assistant.model.ToolDefinition;
import com.sap.ai.assistant.model.ToolResult;
import com.sap.ai.assistant.tools.SapTool;
import com.sap.ai.assistant.tools.SapToolRegistry;

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

    /**
     * Creates a new agent loop.
     *
     * @param llmProvider  the LLM provider to use for generating responses
     * @param toolRegistry the registry of available SAP tools
     */
    public AgentLoop(LlmProvider llmProvider, SapToolRegistry toolRegistry) {
        this.llmProvider = llmProvider;
        this.toolRegistry = toolRegistry;
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
            List<ToolDefinition> toolDefinitions = toolRegistry.getAllDefinitions();

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

                    ToolResult result = executeTool(toolCall);
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
     * invoking it with the provided arguments.
     *
     * @param toolCall the tool call to execute
     * @return the tool result (always non-null)
     */
    private ToolResult executeTool(ToolCall toolCall) {
        String toolName = toolCall.getName();
        SapTool tool = toolRegistry.get(toolName);

        if (tool == null) {
            return ToolResult.error(toolCall.getId(),
                    "Unknown tool: '" + toolName + "'. "
                    + "Please use one of the available tools.");
        }

        try {
            ToolResult result = tool.execute(toolCall.getArguments());
            // Ensure the result carries the correct toolCallId
            if (result.getToolCallId() == null || result.getToolCallId().isEmpty()) {
                // Re-wrap with the proper toolCallId
                if (result.isError()) {
                    return ToolResult.error(toolCall.getId(), result.getContent());
                } else {
                    return ToolResult.success(toolCall.getId(), result.getContent());
                }
            }
            return result;
        } catch (Exception e) {
            return ToolResult.error(toolCall.getId(),
                    "Tool execution failed: " + e.getClass().getSimpleName()
                    + " - " + e.getMessage());
        }
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
