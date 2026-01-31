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
     * the code-change workflow, and (when available) the current ADT editor context.
     * <p>
     * Tool descriptions are NOT included in the system prompt because they are
     * already provided via the API's {@code tools} parameter. This avoids
     * duplicating ~1000 tokens of tool descriptions on every request.
     * </p>
     *
     * @param context  the current ADT context (may be {@code null})
     * @param registry the tool registry (currently unused; kept for API compat)
     * @return the assembled system prompt
     */
    public static String buildSystemPrompt(AdtContext context, SapToolRegistry registry) {
        StringBuilder sb = new StringBuilder();

        // -- Base identity --
        sb.append("You are an expert SAP ABAP assistant with full read/write access ")
          .append("to a live SAP system via ADT REST APIs.\n\n");

        // -- Concise code-change workflow --
        sb.append("## Workflow\n\n");
        sb.append("1. **Validate first**: call sap_syntax_check with the `content` parameter to check code WITHOUT saving.\n");
        sb.append("2. **Fix errors**: iterate with sap_syntax_check until zero errors.\n");
        sb.append("3. **Write**: use sap_set_source or sap_write_and_check (diff preview shown to user; locking is automatic).\n");
        sb.append("4. **Activate**: call sap_activate. Fix errors if activation fails.\n\n");
        sb.append("IMPORTANT: Always validate syntax BEFORE writing. The system enforces this — ");
        sb.append("writes with syntax errors are rejected.\n");
        sb.append("Only run sap_atc_run when the user explicitly requests ATC or quality checks.\n\n");

        // -- Output style instruction --
        sb.append("## Response Style\n\n");
        sb.append("Do NOT include full source code in your text responses. ");
        sb.append("Use tool calls (sap_syntax_check, sap_set_source, sap_write_and_check) to validate and write code. ");
        sb.append("Only show short code snippets (under 10 lines) when explaining specific changes or errors. ");
        sb.append("This keeps responses concise and avoids duplicating code that is already in the tool call.\n\n");

        // -- SAP Documentation tools (only if MCP tools might be registered) --
        sb.append("## SAP Documentation\n\n");
        sb.append("MCP documentation tools (prefixed with mcp_) can search SAP documentation, ");
        sb.append("ABAP keyword reference, and SAP Help Portal.\n\n");

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
