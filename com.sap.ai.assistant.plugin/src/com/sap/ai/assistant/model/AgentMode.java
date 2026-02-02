package com.sap.ai.assistant.model;

/**
 * Defines the available agent modes that users can select from the toolbar dropdown.
 * <p>
 * Each mode configures a different agent personality: system prompt, tool set,
 * and LLM model. Adding a new agent is as simple as adding a new enum value
 * and handling it in {@code AiAssistantView.handleSend()}.
 * </p>
 */
public enum AgentMode {

    MAIN("Main Agent", "Full SAP assistant with read/write access"),
    RESEARCH("Research", "Read-only research — SAP docs, code reading, no writes"),
    CODE_REVIEW("Code Review", "Review ABAP code quality — syntax, ATC, best practices");

    private final String displayName;
    private final String description;

    AgentMode(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
