package com.sap.ai.assistant.agent;

import java.util.List;

import com.sap.ai.assistant.model.AdtContext;
import com.sap.ai.assistant.model.ToolDefinition;
import com.sap.ai.assistant.tools.SapToolRegistry;

/**
 * Builds the system prompt and enriches user messages with the current ADT editor
 * context. The system prompt instructs the LLM on its role, available tools,
 * and the expected workflow for modifying SAP ABAP objects.
 */
public class ContextBuilder {

    private ContextBuilder() {
        // static utility class
    }

    // ------------------------------------------------------------------
    // System prompt construction
    // ------------------------------------------------------------------

    /**
     * Builds the full system prompt for the agent, including the base instructions,
     * available tool descriptions, the code-change workflow, and (when available)
     * the current ADT editor context.
     *
     * @param context  the current ADT context (may be {@code null})
     * @param registry the tool registry used to list available tools
     * @return the assembled system prompt
     */
    public static String buildSystemPrompt(AdtContext context, SapToolRegistry registry) {
        StringBuilder sb = new StringBuilder();

        // -- Base identity --
        sb.append("You are an expert SAP ABAP assistant with full read/write access ")
          .append("to a live SAP system via ADT REST APIs.\n\n");

        // -- Available tools --
        sb.append("## Available Tools\n\n");
        if (registry != null) {
            List<ToolDefinition> definitions = registry.getAllDefinitions();
            for (ToolDefinition def : definitions) {
                sb.append("- **").append(def.getName()).append("**: ")
                  .append(def.getDescription()).append("\n");
            }
        }
        sb.append("\n");

        // -- Code-change workflow --
        sb.append("## Workflow for Code Changes\n\n");
        sb.append("When modifying ABAP source code, always follow this sequence:\n\n");
        sb.append("1. **Lock** the object before making changes (sap_lock_object).\n");
        sb.append("2. **Write** the new source code (sap_set_source or sap_write_and_check).\n");
        sb.append("3. **Fix errors** \u2014 run a syntax check (sap_syntax_check) and fix any issues.\n");
        sb.append("4. **Activate** the object (sap_activate_object). If activation fails, ")
          .append("fix the reported errors and retry.\n");
        sb.append("5. **ATC check** \u2014 run the ABAP Test Cockpit (sap_atc_run) and resolve ")
          .append("any critical findings.\n");
        sb.append("6. **Unlock** the object when finished (sap_unlock_object).\n\n");
        sb.append("Always ensure the object is unlocked after changes, even if an error occurs.\n\n");

        // -- SAP Documentation tools --
        sb.append("## SAP Documentation\n\n");
        sb.append("You have access to MCP documentation tools (prefixed with mcp_) that can search ");
        sb.append("official SAP documentation, ABAP keyword reference, SAP Help Portal, and SAP Community. ");
        sb.append("Use these tools when you need reference information about ABAP syntax, CDS annotations, ");
        sb.append("RAP patterns, S/4HANA changes, or any SAP development topic.\n\n");

        // -- ADT context --
        if (context != null) {
            appendAdtContext(sb, context);
        }

        return sb.toString();
    }

    /**
     * Convenience overload that builds the system prompt without a tool registry.
     * Tool descriptions will be omitted.
     *
     * @param context the current ADT context (may be {@code null})
     * @return the assembled system prompt
     */
    public static String buildSystemPrompt(AdtContext context) {
        return buildSystemPrompt(context, null);
    }

    // ------------------------------------------------------------------
    // User message enrichment
    // ------------------------------------------------------------------

    /**
     * Enriches a user message with the current ADT editor context when appropriate.
     * <p>
     * If the user's text does not explicitly mention a specific ABAP object name
     * and the ADT context contains an open object, a contextual preamble is prepended
     * to the message so the LLM knows which object the user is referring to.
     * </p>
     *
     * @param userText the raw user message text
     * @param context  the current ADT context (may be {@code null})
     * @return the (possibly enriched) user message
     */
    public static String buildUserMessageWithContext(String userText, AdtContext context) {
        if (userText == null || userText.isEmpty()) {
            return userText;
        }
        if (context == null || context.getObjectName() == null || context.getObjectName().isEmpty()) {
            return userText;
        }

        // If the user already mentions the object name, skip enrichment
        if (userText.toUpperCase().contains(context.getObjectName().toUpperCase())) {
            return userText;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[Context: Currently viewing ");
        sb.append(context.getObjectType() != null ? context.getObjectType() : "object");
        sb.append(" \"").append(context.getObjectName()).append("\"");

        if (context.getObjectUri() != null) {
            sb.append(" (URI: ").append(context.getObjectUri()).append(")");
        }

        if (context.getCursorLine() > 0) {
            sb.append(", cursor at line ").append(context.getCursorLine())
              .append(" col ").append(context.getCursorColumn());
        }

        if (context.getSelectedText() != null && !context.getSelectedText().isEmpty()) {
            sb.append(", selected text: \"").append(context.getSelectedText()).append("\"");
        }

        sb.append("]\n\n");
        sb.append(userText);
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Appends the ADT context section to the system prompt.
     */
    private static void appendAdtContext(StringBuilder sb, AdtContext context) {
        sb.append("## Current Editor Context\n\n");

        if (context.getObjectName() != null && !context.getObjectName().isEmpty()) {
            sb.append("- **Object**: ").append(context.getObjectName());
            if (context.getObjectType() != null) {
                sb.append(" (").append(context.getObjectType()).append(")");
            }
            sb.append("\n");
        }

        if (context.getObjectUri() != null && !context.getObjectUri().isEmpty()) {
            sb.append("- **URI**: ").append(context.getObjectUri()).append("\n");
        }

        if (context.getCursorLine() > 0) {
            sb.append("- **Cursor position**: line ").append(context.getCursorLine())
              .append(", column ").append(context.getCursorColumn()).append("\n");
        }

        if (context.getSelectedText() != null && !context.getSelectedText().isEmpty()) {
            sb.append("- **Selected text**: `").append(context.getSelectedText()).append("`\n");
        }

        // Errors from Problems view
        List<String> errors = context.getErrors();
        if (errors != null && !errors.isEmpty()) {
            sb.append("- **Current errors/warnings**:\n");
            for (String error : errors) {
                sb.append("  - ").append(error).append("\n");
            }
        }

        // Full source code
        if (context.getSourceCode() != null && !context.getSourceCode().isEmpty()) {
            sb.append("\n### Source Code\n\n");
            sb.append("```abap\n");
            sb.append(context.getSourceCode());
            if (!context.getSourceCode().endsWith("\n")) {
                sb.append("\n");
            }
            sb.append("```\n");
        }

        sb.append("\n");
    }
}
