package com.sap.ai.assistant.model;

/**
 * Configuration for an LLM provider, including the provider type, API key,
 * model identifier, base URL, and token limits.
 */
public class LlmProviderConfig {

    /**
     * Supported LLM providers, each with sensible defaults.
     */
    public enum Provider {
        ANTHROPIC("Anthropic", "claude-sonnet-4-20250514", "https://api.anthropic.com"),
        OPENAI("OpenAI", "gpt-4o", "https://api.openai.com"),
        GOOGLE("Google", "gemini-2.0-flash", "https://generativelanguage.googleapis.com"),
        MISTRAL("Mistral", "mistral-large-latest", "https://api.mistral.ai");

        private final String displayName;
        private final String defaultModel;
        private final String defaultBaseUrl;

        Provider(String displayName, String defaultModel, String defaultBaseUrl) {
            this.displayName = displayName;
            this.defaultModel = defaultModel;
            this.defaultBaseUrl = defaultBaseUrl;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDefaultModel() {
            return defaultModel;
        }

        public String getDefaultBaseUrl() {
            return defaultBaseUrl;
        }
    }

    private Provider provider;
    private String apiKey;
    private String model;
    private String baseUrl;
    private int maxTokens;

    /**
     * Creates a new LLM provider configuration.
     *
     * @param provider  the LLM provider
     * @param apiKey    the API key for authentication
     * @param model     the model identifier (pass {@code null} to use the provider default)
     * @param baseUrl   the base URL (pass {@code null} to use the provider default)
     * @param maxTokens the maximum number of tokens in a response
     */
    public LlmProviderConfig(Provider provider, String apiKey, String model, String baseUrl, int maxTokens) {
        this.provider = provider;
        this.apiKey = apiKey;
        this.model = (model != null && !model.isEmpty()) ? model : provider.getDefaultModel();
        this.baseUrl = (baseUrl != null && !baseUrl.isEmpty()) ? baseUrl : provider.getDefaultBaseUrl();
        this.maxTokens = maxTokens;
    }

    /**
     * Creates a configuration with provider defaults for model, base URL, and
     * a default max-tokens value of 4096.
     *
     * @param provider the LLM provider
     * @param apiKey   the API key
     */
    public LlmProviderConfig(Provider provider, String apiKey) {
        this(provider, apiKey, null, null, 4096);
    }

    // -- Getters / Setters -------------------------------------------------------

    public Provider getProvider() {
        return provider;
    }

    public void setProvider(Provider provider) {
        this.provider = provider;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    @Override
    public String toString() {
        return "LlmProviderConfig{provider=" + provider
                + ", model='" + model + "'"
                + ", baseUrl='" + baseUrl + "'"
                + ", maxTokens=" + maxTokens
                + "}";
    }
}
