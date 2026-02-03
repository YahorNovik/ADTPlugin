package com.sap.ai.assistant.preferences;

/**
 * Preference keys for the AI Assistant plug-in settings.
 * <p>
 * These constants are shared between the preference page and any code that
 * reads/writes the Eclipse preference store.
 * </p>
 */
public final class PreferenceConstants {

    /** The selected LLM provider name (e.g. "ANTHROPIC", "OPENAI"). */
    public static final String LLM_PROVIDER = "com.sap.ai.assistant.llm.provider";

    /** The API key for the selected LLM provider. */
    public static final String LLM_API_KEY = "com.sap.ai.assistant.llm.apiKey";

    /** The model identifier (e.g. "claude-sonnet-4-20250514", "gpt-4o"). */
    public static final String LLM_MODEL = "com.sap.ai.assistant.llm.model";

    /** Custom base URL for the LLM API (used with Custom provider). */
    public static final String LLM_BASE_URL = "com.sap.ai.assistant.llm.baseUrl";

    /** Maximum number of response tokens (256 - 128 000). */
    public static final String LLM_MAX_TOKENS = "com.sap.ai.assistant.llm.maxTokens";

    /** Maximum cumulative input tokens per agent run (token budget). */
    public static final String LLM_MAX_INPUT_TOKENS = "com.sap.ai.assistant.llm.maxInputTokens";

    /** Whether to include the current editor context in prompts. */
    public static final String INCLUDE_CONTEXT = "com.sap.ai.assistant.includeContext";

    /** The model identifier for the research sub-agent (uses same provider/API key). */
    public static final String RESEARCH_MODEL = "com.sap.ai.assistant.llm.researchModel";

    /** JSON array of MCP server configurations. */
    public static final String MCP_SERVERS = "com.sap.ai.assistant.mcp.servers";

    /** JSON array of saved SAP system connections (without passwords). */
    public static final String SAP_SAVED_SYSTEMS = "com.sap.ai.assistant.sap.savedSystems";

    private PreferenceConstants() {
        // Utility class -- no instances
    }
}
