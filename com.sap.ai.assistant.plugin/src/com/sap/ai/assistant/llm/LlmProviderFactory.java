package com.sap.ai.assistant.llm;

import com.sap.ai.assistant.model.LlmProviderConfig;

/**
 * Factory that creates the appropriate {@link LlmProvider} implementation
 * based on the configured {@link LlmProviderConfig.Provider} enum value.
 */
public final class LlmProviderFactory {

    private LlmProviderFactory() {
        // Utility class — no instances
    }

    /**
     * Creates a new {@link LlmProvider} for the given configuration.
     *
     * @param config the LLM provider configuration (must not be {@code null})
     * @return a provider instance ready to send messages
     * @throws IllegalArgumentException if the provider type is not supported
     */
    public static LlmProvider create(LlmProviderConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("LlmProviderConfig must not be null");
        }
        if (config.getProvider() == null) {
            throw new IllegalArgumentException("Provider type must not be null");
        }

        switch (config.getProvider()) {
            case ANTHROPIC:
                return new AnthropicProvider(config);
            case OPENAI:
                return new OpenAiProvider(config);
            case GOOGLE:
                return new GeminiProvider(config);
            case MISTRAL:
                return new MistralProvider(config);
            case CUSTOM:
                return new OpenAiProvider(config);
            default:
                throw new IllegalArgumentException("Unsupported LLM provider: " + config.getProvider());
        }
    }
}
