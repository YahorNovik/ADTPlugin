package com.sap.ai.assistant.model;

import com.google.gson.JsonObject;

/**
 * Token usage data returned by LLM API responses.
 */
public class LlmUsage {

    private final int inputTokens;
    private final int outputTokens;
    private final int cacheCreationTokens;
    private final int cacheReadTokens;

    public LlmUsage(int inputTokens, int outputTokens, int cacheCreationTokens, int cacheReadTokens) {
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.cacheCreationTokens = cacheCreationTokens;
        this.cacheReadTokens = cacheReadTokens;
    }

    public LlmUsage(int inputTokens, int outputTokens) {
        this(inputTokens, outputTokens, 0, 0);
    }

    /**
     * Parse usage from an Anthropic API response JSON.
     * Expects: {@code {"input_tokens":N, "output_tokens":N, ...}}
     */
    public static LlmUsage fromAnthropicJson(JsonObject usage) {
        if (usage == null) return null;
        int input = usage.has("input_tokens") ? usage.get("input_tokens").getAsInt() : 0;
        int output = usage.has("output_tokens") ? usage.get("output_tokens").getAsInt() : 0;
        int cacheCreate = usage.has("cache_creation_input_tokens")
                ? usage.get("cache_creation_input_tokens").getAsInt() : 0;
        int cacheRead = usage.has("cache_read_input_tokens")
                ? usage.get("cache_read_input_tokens").getAsInt() : 0;
        return new LlmUsage(input, output, cacheCreate, cacheRead);
    }

    /**
     * Parse usage from an OpenAI API response JSON.
     * Expects: {@code {"prompt_tokens":N, "completion_tokens":N, ...}}
     */
    public static LlmUsage fromOpenAiJson(JsonObject usage) {
        if (usage == null) return null;
        int input = usage.has("prompt_tokens") ? usage.get("prompt_tokens").getAsInt() : 0;
        int output = usage.has("completion_tokens") ? usage.get("completion_tokens").getAsInt() : 0;
        return new LlmUsage(input, output);
    }

    /**
     * Parse usage from a Google Gemini API response JSON.
     * Expects: {@code {"promptTokenCount":N, "candidatesTokenCount":N, ...}}
     */
    public static LlmUsage fromGeminiJson(JsonObject usageMetadata) {
        if (usageMetadata == null) return null;
        int input = usageMetadata.has("promptTokenCount") ? usageMetadata.get("promptTokenCount").getAsInt() : 0;
        int output = usageMetadata.has("candidatesTokenCount") ? usageMetadata.get("candidatesTokenCount").getAsInt() : 0;
        return new LlmUsage(input, output);
    }

    public int getInputTokens() { return inputTokens; }
    public int getOutputTokens() { return outputTokens; }
    public int getCacheCreationTokens() { return cacheCreationTokens; }
    public int getCacheReadTokens() { return cacheReadTokens; }
    public int getTotalTokens() { return inputTokens + outputTokens; }

    @Override
    public String toString() {
        return inputTokens + " in / " + outputTokens + " out";
    }
}
