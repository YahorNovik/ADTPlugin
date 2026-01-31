package com.sap.ai.assistant.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a single message in a chat conversation.
 * Messages can originate from the user, the assistant, or from tool execution results.
 */
public class ChatMessage {

    /**
     * The role of the message sender.
     */
    public enum Role {
        USER,
        ASSISTANT,
        TOOL
    }

    private final Role role;
    private final String textContent;
    private final List<ToolCall> toolCalls;
    private final List<ToolResult> toolResults;
    private LlmUsage usage;

    /**
     * Full constructor.
     *
     * @param role        the role of the message sender
     * @param textContent the text content of the message (may be null)
     * @param toolCalls   tool invocations requested by the assistant (may be null)
     * @param toolResults results of tool executions (may be null)
     */
    public ChatMessage(Role role, String textContent, List<ToolCall> toolCalls, List<ToolResult> toolResults) {
        this.role = role;
        this.textContent = textContent;
        this.toolCalls = toolCalls != null ? new ArrayList<>(toolCalls) : new ArrayList<>();
        this.toolResults = toolResults != null ? new ArrayList<>(toolResults) : new ArrayList<>();
    }

    // -- Static factory methods --------------------------------------------------

    /**
     * Creates a user message with the given text.
     *
     * @param text the user's message text
     * @return a new ChatMessage with role USER
     */
    public static ChatMessage user(String text) {
        return new ChatMessage(Role.USER, text, null, null);
    }

    /**
     * Creates an assistant message with the given text.
     *
     * @param text the assistant's message text
     * @return a new ChatMessage with role ASSISTANT
     */
    public static ChatMessage assistant(String text) {
        return new ChatMessage(Role.ASSISTANT, text, null, null);
    }

    /**
     * Creates a tool-results message containing one or more tool execution results.
     *
     * @param results the list of tool results
     * @return a new ChatMessage with role TOOL
     */
    public static ChatMessage toolResults(List<ToolResult> results) {
        return new ChatMessage(Role.TOOL, null, null, results);
    }

    // -- Instance methods --------------------------------------------------------

    /**
     * Returns {@code true} if this message contains at least one tool call.
     *
     * @return whether tool calls are present
     */
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    /**
     * Appends a tool result to this message.
     *
     * @param result the tool result to add
     */
    public void addToolResult(ToolResult result) {
        if (result != null) {
            this.toolResults.add(result);
        }
    }

    // -- Getters -----------------------------------------------------------------

    public Role getRole() {
        return role;
    }

    public String getTextContent() {
        return textContent;
    }

    public List<ToolCall> getToolCalls() {
        return Collections.unmodifiableList(toolCalls);
    }

    public List<ToolResult> getToolResults() {
        return Collections.unmodifiableList(toolResults);
    }

    public LlmUsage getUsage() {
        return usage;
    }

    public void setUsage(LlmUsage usage) {
        this.usage = usage;
    }

    @Override
    public String toString() {
        return "ChatMessage{role=" + role
                + ", textContent='" + (textContent != null ? textContent.substring(0, Math.min(textContent.length(), 60)) : "null") + "'"
                + ", toolCalls=" + toolCalls.size()
                + ", toolResults=" + toolResults.size()
                + "}";
    }
}
