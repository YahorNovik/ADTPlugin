package com.sap.ai.assistant.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Maintains the state of a chat conversation, including the ordered list of
 * messages and an optional system prompt.
 */
public class ChatConversation {

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
