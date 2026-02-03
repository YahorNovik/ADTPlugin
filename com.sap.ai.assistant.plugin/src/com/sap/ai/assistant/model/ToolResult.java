package com.sap.ai.assistant.model;

/**
 * Represents the result of executing a tool call. A result is either a
 * successful content string or an error message.
 */
public class ToolResult {

    private final String toolCallId;
    private final String content;
    private final boolean isError;

    /**
     * Creates a new tool result.
     *
     * @param toolCallId the identifier of the tool call this result corresponds to
     * @param content    the result content or error message
     * @param isError    {@code true} if this result represents an error
     */
    public ToolResult(String toolCallId, String content, boolean isError) {
        this.toolCallId = toolCallId;
        this.content = content;
        this.isError = isError;
    }

    // -- Static factory methods --------------------------------------------------

    /**
     * Creates a successful tool result.
     *
     * @param id      the tool call identifier
     * @param content the successful result content
     * @return a new ToolResult marked as success
     */
    public static ToolResult success(String id, String content) {
        return new ToolResult(id, content, false);
    }

    /**
     * Creates an error tool result.
     *
     * @param id      the tool call identifier
     * @param message the error message
     * @return a new ToolResult marked as error
     */
    public static ToolResult error(String id, String message) {
        return new ToolResult(id, message, true);
    }

    // -- Getters -----------------------------------------------------------------

    public String getToolCallId() {
        return toolCallId;
    }

    public String getContent() {
        return content;
    }

    public boolean isError() {
        return isError;
    }

    /**
     * Creates a truncated copy of this tool result if the content exceeds maxLen.
     *
     * @param maxLen maximum content length
     * @return a new truncated ToolResult, or this instance if already short enough
     */
    public ToolResult truncated(int maxLen) {
        if (content == null || content.length() <= maxLen) {
            return this;
        }
        String truncatedContent = content.substring(0, maxLen)
                + "\n...[truncated from " + content.length() + " chars]";
        return new ToolResult(toolCallId, truncatedContent, isError);
    }

    @Override
    public String toString() {
        return "ToolResult{toolCallId='" + toolCallId + "', isError=" + isError
                + ", content='" + (content != null ? content.substring(0, Math.min(content.length(), 80)) : "null") + "'}";
    }
}
