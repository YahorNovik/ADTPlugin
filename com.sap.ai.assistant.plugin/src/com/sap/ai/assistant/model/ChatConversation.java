package com.sap.ai.assistant.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Maintains the state of a chat conversation, including the ordered list of
 * messages and an optional system prompt.
 * <p>
 * Includes a sliding-window mechanism ({@link #trimMessages(int)}) to keep
 * token usage manageable during multi-round agentic interactions. When the
 * number of messages exceeds the limit, older middle messages are dropped
 * while preserving the first user message (original intent) and the most
 * recent messages (immediate context).
 * </p>
 */
public class ChatConversation {

    /**
     * Default maximum number of messages to retain in the conversation.
     * This keeps roughly the last 5 user/assistant/tool exchanges.
     */
    public static final int DEFAULT_MAX_MESSAGES = 8;

    private final List<ChatMessage> messages;
    private String systemPrompt;

    /**
     * Creates a new empty conversation with no system prompt.
     */
    public ChatConversation() {
        this.messages = new ArrayList<>();
        this.systemPrompt = null;
    }

    /**
     * Creates a new empty conversation with the given system prompt.
     *
     * @param systemPrompt the system-level instruction for the LLM
     */
    public ChatConversation(String systemPrompt) {
        this.messages = new ArrayList<>();
        this.systemPrompt = systemPrompt;
    }

    // -- Mutation methods --------------------------------------------------------

    /**
     * Adds a user message with the given text to the conversation.
     *
     * @param text the user's message text
     */
    public void addUserMessage(String text) {
        messages.add(ChatMessage.user(text));
    }

    /**
     * Adds a pre-constructed message (typically an assistant or tool message)
     * to the conversation.
     *
     * @param message the message to append
     */
    public void addAssistantMessage(ChatMessage message) {
        if (message != null) {
            messages.add(message);
        }
    }

    /**
     * Removes all messages from the conversation. The system prompt is retained.
     */
    public void clear() {
        messages.clear();
    }

    /**
     * Trims the conversation to at most {@code maxMessages} entries by removing
     * older messages from the middle, preserving the first user message and
     * the most recent messages.
     * <p>
     * This implements a sliding window that keeps the original user intent
     * (first message) visible to the LLM while retaining the most recent
     * context for the current tool-call round.
     * </p>
     * <p>
     * When messages are dropped, a synthetic user message is inserted after
     * the first message indicating that earlier exchanges were omitted.
     * </p>
     *
     * @param maxMessages the maximum number of messages to retain (minimum 4)
     */
    public void trimMessages(int maxMessages) {
        if (maxMessages < 4) {
            maxMessages = 4;
        }
        if (messages.size() <= maxMessages) {
            return;
        }

        // Keep: [0] = first user message, then the last (maxMessages - 2) messages,
        // plus a synthetic "trimmed" marker at position [1].
        ChatMessage firstMessage = messages.get(0);

        // How many recent messages to keep (leaving room for first + marker)
        int keepRecent = maxMessages - 2;
        int startOfRecent = messages.size() - keepRecent;

        List<ChatMessage> recent = new ArrayList<>(messages.subList(startOfRecent, messages.size()));

        int droppedCount = startOfRecent - 1; // messages between first and the kept tail

        messages.clear();
        messages.add(firstMessage);
        messages.add(ChatMessage.user(
                "[System note: " + droppedCount + " earlier messages were omitted to save context space. "
                + "The original request and recent messages are preserved.]"));
        messages.addAll(recent);
    }

    /**
     * Trims the conversation using the default maximum message count.
     *
     * @see #trimMessages(int)
     */
    public void trimMessages() {
        trimMessages(DEFAULT_MAX_MESSAGES);
        truncateOldToolResults();
    }

    /**
     * Default maximum length for tool result content before truncation.
     * Keeps enough context for the LLM to understand what was returned.
     */
    public static final int DEFAULT_TOOL_RESULT_MAX_LEN = 2000;

    /**
     * Truncates tool result content in older messages to reduce token usage.
     * The most recent tool results message is kept full; older ones are truncated.
     *
     * @param maxLen maximum length for each tool result content
     */
    public void truncateOldToolResults(int maxLen) {
        if (messages.size() < 2) {
            return;
        }

        // Find the index of the last TOOL message (most recent tool results)
        int lastToolIndex = -1;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).getRole() == ChatMessage.Role.TOOL) {
                lastToolIndex = i;
                break;
            }
        }

        // Truncate all TOOL messages except the most recent one
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            if (msg.getRole() == ChatMessage.Role.TOOL && i != lastToolIndex) {
                ChatMessage truncated = msg.withTruncatedToolResults(maxLen);
                if (truncated != msg) {
                    messages.set(i, truncated);
                }
            }
        }
    }

    /**
     * Truncates old tool results using the default maximum length.
     *
     * @see #truncateOldToolResults(int)
     */
    public void truncateOldToolResults() {
        truncateOldToolResults(DEFAULT_TOOL_RESULT_MAX_LEN);
    }

    // -- Getters / Setters -------------------------------------------------------

    /**
     * Returns an unmodifiable view of the messages in this conversation.
     *
     * @return the list of messages
     */
    public List<ChatMessage> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    /**
     * Returns the number of messages in the conversation.
     *
     * @return message count
     */
    public int size() {
        return messages.size();
    }

    @Override
    public String toString() {
        return "ChatConversation{messages=" + messages.size()
                + ", systemPrompt=" + (systemPrompt != null ? "set" : "null")
                + "}";
    }
}
