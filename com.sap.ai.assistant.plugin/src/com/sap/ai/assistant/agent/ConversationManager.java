package com.sap.ai.assistant.agent;

import java.util.concurrent.ConcurrentHashMap;

import com.sap.ai.assistant.model.ChatConversation;

/**
 * Thread-safe manager for chat conversations, keyed by SAP system project name.
 * <p>
 * Each SAP system connection maintains its own independent conversation history.
 * This allows the user to switch between systems without losing context.
 * </p>
 * <p>
 * All methods are safe for concurrent access from multiple threads (e.g. the UI
 * thread and background Jobs).
 * </p>
 */
public class ConversationManager {

    private final ConcurrentHashMap<String, ChatConversation> conversations;

    /**
     * Creates a new conversation manager with no active conversations.
     */
    public ConversationManager() {
        this.conversations = new ConcurrentHashMap<>();
    }

    /**
     * Returns the existing conversation for the given system name, or creates a new
     * one with the specified system prompt if none exists.
     *
     * @param systemName   the SAP system project name (used as the key)
     * @param systemPrompt the system prompt to use if a new conversation is created
     * @return the existing or newly created conversation
     */
    public ChatConversation getOrCreate(String systemName, String systemPrompt) {
        return conversations.computeIfAbsent(systemName, key -> new ChatConversation(systemPrompt));
    }

    /**
     * Returns the existing conversation for the given system name, or {@code null}
     * if no conversation exists for that system.
     *
     * @param systemName the SAP system project name
     * @return the conversation, or {@code null} if not found
     */
    public ChatConversation get(String systemName) {
        return conversations.get(systemName);
    }

    /**
     * Clears and removes the conversation for the given system name.
     * <p>
     * If a conversation exists for the system, it is first cleared (all messages
     * removed) and then removed from the manager.
     * </p>
     *
     * @param systemName the SAP system project name
     */
    public void clear(String systemName) {
        ChatConversation conversation = conversations.remove(systemName);
        if (conversation != null) {
            conversation.clear();
        }
    }

    /**
     * Clears and removes all conversations across all systems.
     */
    public void clearAll() {
        for (ChatConversation conversation : conversations.values()) {
            conversation.clear();
        }
        conversations.clear();
    }

    /**
     * Returns {@code true} if a conversation exists for the given system name.
     *
     * @param systemName the SAP system project name
     * @return whether a conversation exists
     */
    public boolean has(String systemName) {
        return conversations.containsKey(systemName);
    }

    /**
     * Returns the number of active conversations.
     *
     * @return the count of tracked conversations
     */
    public int size() {
        return conversations.size();
    }

    @Override
    public String toString() {
        return "ConversationManager{conversations=" + conversations.size()
                + ", systems=" + conversations.keySet() + "}";
    }
}
