package com.sap.ai.assistant.llm;

import java.util.List;

import com.sap.ai.assistant.model.ChatMessage;
import com.sap.ai.assistant.model.ToolDefinition;

/**
 * Abstraction for interacting with a Large Language Model provider.
 * Each implementation handles the HTTP protocol and message serialisation
 * specific to one provider (Anthropic, OpenAI, Google Gemini, Mistral, etc.).
 */
public interface LlmProvider {

    /**
     * Sends a list of conversation messages to the LLM and returns the
     * assistant's reply.  The reply may contain text, tool calls, or both.
     *
     * @param messages     the conversation history
     * @param systemPrompt the system-level instruction (may be {@code null})
     * @param tools        tool definitions the model may invoke (may be {@code null} or empty)
     * @return the assistant's response as a {@link ChatMessage}
     * @throws LlmException if a network or provider error occurs
     */
    ChatMessage sendMessage(List<ChatMessage> messages, String systemPrompt, List<ToolDefinition> tools)
            throws LlmException;

    /**
     * Returns a human-readable identifier for this provider (e.g. "anthropic", "openai").
     *
     * @return the provider identifier
     */
    String getProviderId();
}
