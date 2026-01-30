package com.sap.ai.assistant.model;

/**
 * Configuration for an LLM provider, including the provider type, API key,
 * model identifier, base URL, and token limits.
 */
public class LlmProviderConfig {

    /**
     * Supported LLM providers, each with sensible defaults and predefined models.
     */
    public enum Provider {
        ANTHROPIC("Anthropic", "claude-sonnet-4-20250514", "https://api.anthropic.com",
                new String[]{
                    "claude-sonnet-4-20250514",
                    "claude-opus-4-20250514",
                    "claude-haiku-3-5-20241022",
                    "claude-3-5-sonnet-20241022"
                }),
        OPENAI("OpenAI", "gpt-4o", "https://api.openai.com",
                new String[]{
                    "gpt-4o",
                    "gpt-4o-mini",
                    "gpt-4-turbo",
                    "o1",
                    "o1-mini",
                    "o3-mini"
                }),
        GOOGLE("Google", "gemini-2.5-flash", "https://generativelanguage.googleapis.com",
                new String[]{
                    "gemini-2.5-flash",
                    "gemini-2.5-pro",
                    "gemini-2.0-flash",
                    "gemini-2.0-flash-lite"
                }),
        MISTRAL("Mistral", "mistral-large-latest", "https://api.mistral.ai",
                new String[]{
                    "mistral-large-latest",
                    "mistral-medium-latest",
                    "mistral-small-latest",
                    "open-mistral-nemo"
                });

        private final String displayName;
        private final String defaultModel;
        private final String defaultBaseUrl;
        private final String[] availableModels;

        Provider(String displayName, String defaultModel, String defaultBaseUrl, String[] availableModels) {
            this.displayName = displayName;
            this.defaultModel = defaultModel;
            this.defaultBaseUrl = defaultBaseUrl;
            this.availableModels = availableModels;
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

        public String[] getAvailableModels() {
            return availableModels;
        }
    }

    private Provider provider;
    private String apiKey;
    private String model;
    private String baseUrl;
    private int maxTokens;

    public LlmProviderConfig(Provider provider, String apiKey, String model, String baseUrl, int maxTokens) {
        this.provider = provider;
        this.apiKey = apiKey;
        this.model = (model != null && !model.isEmpty()) ? model : provider.getDefaultModel();
        this.baseUrl = (baseUrl != null && !baseUrl.isEmpty()) ? baseUrl : provider.getDefaultBaseUrl();
        this.maxTokens = maxTokens;
    }

    public LlmProviderConfig(Provider provider, String apiKey) {
        this(provider, apiKey, null, null, 4096);
    }

    public Provider getProvider() { return provider; }
    public void setProvider(Provider provider) { this.provider = provider; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }

    @Override
    public String toString() {
        return "LlmProviderConfig{provider=" + provider
                + ", model='" + model + "'"
                + ", baseUrl='" + baseUrl + "'"
                + ", maxTokens=" + maxTokens
                + "}";
    }
}
