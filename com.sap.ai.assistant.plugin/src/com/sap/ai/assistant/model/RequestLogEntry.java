package com.sap.ai.assistant.model;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Represents a single LLM API request/response for developer logging.
 * Includes system prompt, conversation context, tool call I/O, and LLM text content.
 */
public class RequestLogEntry {

    private final long timestamp;
    private final int roundNumber;
    private final String provider;
    private final String model;
    private final int conversationMessageCount;
    private final long durationMs;
    private final LlmUsage usage;
    private final int toolCallCount;
    private final String[] toolNames;
    private final String error;
    private final String llmTextContent;
    private final List<ToolCallDetail> toolCallDetails;
    private final String systemPrompt;
    private final String conversationSnapshot;

    /**
     * A single tool call with its input arguments and output result.
     */
    public static class ToolCallDetail {
        private final String toolName;
        private final String arguments;
        private final String result;
        private final boolean isError;

        public ToolCallDetail(String toolName, String arguments, String result, boolean isError) {
            this.toolName = toolName;
            this.arguments = arguments;
            this.result = result;
            this.isError = isError;
        }

        public String getToolName() { return toolName; }
        public String getArguments() { return arguments; }
        public String getResult() { return result; }
        public boolean isError() { return isError; }
    }

    /** Backward-compatible constructor (no tool details, no prompts). */
    public RequestLogEntry(int roundNumber, String provider, String model,
                           int conversationMessageCount, long durationMs,
                           LlmUsage usage, int toolCallCount, String[] toolNames,
                           String error) {
        this(roundNumber, provider, model, conversationMessageCount, durationMs,
             usage, toolCallCount, toolNames, error, null, null, null, null);
    }

    /** Constructor with tool details but no prompts. */
    public RequestLogEntry(int roundNumber, String provider, String model,
                           int conversationMessageCount, long durationMs,
                           LlmUsage usage, int toolCallCount, String[] toolNames,
                           String error, String llmTextContent,
                           List<ToolCallDetail> toolCallDetails) {
        this(roundNumber, provider, model, conversationMessageCount, durationMs,
             usage, toolCallCount, toolNames, error, llmTextContent, toolCallDetails,
             null, null);
    }

    /** Full constructor with all fields. */
    public RequestLogEntry(int roundNumber, String provider, String model,
                           int conversationMessageCount, long durationMs,
                           LlmUsage usage, int toolCallCount, String[] toolNames,
                           String error, String llmTextContent,
                           List<ToolCallDetail> toolCallDetails,
                           String systemPrompt, String conversationSnapshot) {
        this.timestamp = System.currentTimeMillis();
        this.roundNumber = roundNumber;
        this.provider = provider;
        this.model = model;
        this.conversationMessageCount = conversationMessageCount;
        this.durationMs = durationMs;
        this.usage = usage;
        this.toolCallCount = toolCallCount;
        this.toolNames = toolNames;
        this.error = error;
        this.llmTextContent = llmTextContent;
        this.toolCallDetails = toolCallDetails;
        this.systemPrompt = systemPrompt;
        this.conversationSnapshot = conversationSnapshot;
    }

    public long getTimestamp() { return timestamp; }
    public int getRoundNumber() { return roundNumber; }
    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public int getConversationMessageCount() { return conversationMessageCount; }
    public long getDurationMs() { return durationMs; }
    public LlmUsage getUsage() { return usage; }
    public int getToolCallCount() { return toolCallCount; }
    public String[] getToolNames() { return toolNames; }
    public String getError() { return error; }
    public boolean isError() { return error != null; }
    public String getLlmTextContent() { return llmTextContent; }
    public List<ToolCallDetail> getToolCallDetails() {
        return toolCallDetails != null ? Collections.unmodifiableList(toolCallDetails) : Collections.emptyList();
    }
    public String getSystemPrompt() { return systemPrompt; }
    public String getConversationSnapshot() { return conversationSnapshot; }

    public String getFormattedTime() {
        return new SimpleDateFormat("HH:mm:ss").format(new Date(timestamp));
    }

    public String getFormattedDuration() {
        if (durationMs < 1000) return durationMs + "ms";
        return String.format("%.1fs", durationMs / 1000.0);
    }

    public String getToolNamesString() {
        if (toolNames == null || toolNames.length == 0) return "";
        return String.join(", ", toolNames);
    }

    /**
     * Compact one-line summary for table display.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(getFormattedTime()).append("] ");
        sb.append("Round ").append(roundNumber).append(" | ");
        sb.append(provider).append("/").append(model).append(" | ");
        sb.append(getFormattedDuration()).append(" | ");
        sb.append(conversationMessageCount).append(" msgs | ");
        if (usage != null) {
            sb.append(usage.getInputTokens()).append(" in / ");
            sb.append(usage.getOutputTokens()).append(" out | ");
        }
        if (toolCallCount > 0) {
            sb.append(toolCallCount).append(" tools: ").append(getToolNamesString()).append(" | ");
        }
        if (error != null) {
            sb.append("ERROR: ").append(error);
        }
        return sb.toString();
    }

    /**
     * Multi-line detailed format showing system prompt, conversation,
     * tool I/O, and LLM text. Used for the detail pane and clipboard copy.
     */
    public String toDetailString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Round ").append(roundNumber).append(" ===  [");
        sb.append(getFormattedTime()).append("]  ");
        sb.append(provider).append("/").append(model);
        sb.append("  ").append(getFormattedDuration()).append("\n");

        if (usage != null) {
            sb.append("Tokens: ").append(usage.getInputTokens()).append(" in / ");
            sb.append(usage.getOutputTokens()).append(" out");
            if (usage.getCacheCreationTokens() > 0 || usage.getCacheReadTokens() > 0) {
                sb.append(" (cache: ").append(usage.getCacheCreationTokens()).append(" created, ");
                sb.append(usage.getCacheReadTokens()).append(" read)");
            }
            sb.append("\n");
        }
        sb.append("Context: ").append(conversationMessageCount).append(" messages\n");

        // System prompt
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            sb.append("\n--- System Prompt (").append(systemPrompt.length()).append(" chars) ---\n");
            sb.append(systemPrompt).append("\n");
        }

        // Conversation messages sent to LLM
        if (conversationSnapshot != null && !conversationSnapshot.isEmpty()) {
            sb.append("\n--- Conversation Sent to LLM ---\n");
            sb.append(conversationSnapshot);
        }

        // LLM response text
        if (llmTextContent != null && !llmTextContent.isEmpty()) {
            sb.append("\n--- LLM Response ---\n");
            sb.append(llmTextContent).append("\n");
        }

        // Tool call details
        if (toolCallDetails != null && !toolCallDetails.isEmpty()) {
            for (ToolCallDetail detail : toolCallDetails) {
                sb.append("\n--- Tool: ").append(detail.getToolName()).append(" ---\n");
                if (detail.getArguments() != null && !detail.getArguments().isEmpty()) {
                    sb.append("Input:  ").append(detail.getArguments()).append("\n");
                }
                if (detail.isError()) {
                    sb.append("ERROR:  ").append(detail.getResult()).append("\n");
                } else if (detail.getResult() != null && !detail.getResult().isEmpty()) {
                    sb.append("Output: ").append(detail.getResult()).append("\n");
                }
            }
        } else if (toolCallCount > 0) {
            sb.append("\nTools called: ").append(getToolNamesString()).append("\n");
        }

        if (error != null) {
            sb.append("\n*** ERROR: ").append(error).append(" ***\n");
        }

        return sb.toString();
    }
}
