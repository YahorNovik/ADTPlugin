package com.sap.ai.assistant.agent;

import com.sap.ai.assistant.model.ChatMessage;
import com.sap.ai.assistant.model.DiffRequest;
import com.sap.ai.assistant.model.ToolCall;
import com.sap.ai.assistant.model.ToolResult;

/**
 * Callback interface for observing the progress of an {@link AgentLoop} execution.
 * <p>
 * Implementations receive notifications as the agent produces text, invokes tools,
 * completes its response, or encounters an error. All callback methods are invoked
 * on the same thread that runs the agent loop (typically an Eclipse background Job),
 * so UI updates must be dispatched to the display thread by the implementation.
 * </p>
 */
public interface AgentCallback {

    /**
     * Called when the LLM produces a text token (or text fragment).
     * <p>
     * Note: depending on the LLM provider, this may be called once with the full
     * text or incrementally with streaming tokens if streaming is supported.
     * </p>
     *
     * @param token the text token or fragment
     */
    void onTextToken(String token);

    /**
     * Called when the agent begins executing a tool call requested by the LLM.
     *
     * @param toolCall the tool call about to be executed
     */
    void onToolCallStart(ToolCall toolCall);

    /**
     * Called when a tool call execution has completed.
     *
     * @param result the result of the tool execution
     */
    void onToolCallEnd(ToolResult result);

    /**
     * Called when the agent loop has finished and the LLM has produced a final
     * response with no further tool calls.
     *
     * @param finalMessage the final assistant message
     */
    void onComplete(ChatMessage finalMessage);

    /**
     * Called when a tool proposes a source code change that requires user
     * approval before being applied. The implementation MUST eventually call
     * {@link DiffRequest#setDecision} to unblock the agent loop.
     * <p>
     * This method is called on the agent loop thread. Implementations should
     * dispatch UI work via {@code Display.asyncExec()} and let this method
     * return immediately -- the agent loop will block on
     * {@link DiffRequest#awaitDecision()}.
     * </p>
     *
     * @param diffRequest the proposed change details
     */
    void onDiffApprovalNeeded(DiffRequest diffRequest);

    /**
     * Called when the agent loop encounters an unrecoverable error.
     *
     * @param error the exception that caused the failure
     */
    void onError(Exception error);
}
