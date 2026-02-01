package com.sap.ai.assistant.agent;

import java.util.Collections;
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
     * the code-change workflow, and (when available) the ADT editor contexts.
     * <p>
     * Tool descriptions are NOT included in the system prompt because they are
     * already provided via the API's {@code tools} parameter. This avoids
     * duplicating ~1000 tokens of tool descriptions on every request.
     * </p>
     *
     * @param contexts the list of ADT contexts to include (may be {@code null} or empty)
     * @param registry the tool registry (currently unused; kept for API compat)
     * @return the assembled system prompt
     */
    public static String buildSystemPrompt(List<AdtContext> contexts, SapToolRegistry registry,
                                              boolean hasResearchTool) {
        StringBuilder sb = new StringBuilder();

        // -- Base identity --
        sb.append("You are an expert SAP ABAP assistant with full read/write access ")
          .append("to a live SAP system via ADT REST APIs.\n\n");

        // -- Code-change workflow --
        sb.append("## Workflow for Code Changes\n\n");
        sb.append("1. **Validate first**: call `sap_syntax_check` with the `content` parameter to check code WITHOUT saving.\n");
        sb.append("2. **Fix errors**: iterate with `sap_syntax_check` until zero errors.\n");
        sb.append("3. **Write**: use `sap_write_and_check` (creates + writes + checks in one call) or `sap_set_source` (writes to existing object). ");
        sb.append("A diff preview is shown to the user; locking/unlocking is automatic.\n");
        sb.append("4. **Activation is automatic**: when the user accepts the diff, the object is activated automatically. Do NOT call `sap_activate` separately.\n\n");
        sb.append("IMPORTANT: Always validate syntax BEFORE writing. The system enforces this — ");
        sb.append("writes with syntax errors are rejected.\n");
        sb.append("Only run `sap_atc_run` when the user explicitly requests ATC or quality checks.\n\n");

        // -- Tool reference --
        sb.append("## Tool Reference\n\n");
        sb.append("**Reading:**\n");
        sb.append("- `sap_search_object` — search for ABAP objects by name pattern and type\n");
        sb.append("- `sap_get_source` — read source code of a program, class, interface, or function module\n");
        sb.append("- `sap_object_structure` — get the structure/hierarchy of an ABAP object (methods, includes, etc.)\n");
        sb.append("- `sap_node_contents` — list children of a package or namespace\n");
        sb.append("- `sap_find_definition` — navigate to where a symbol (class, method, type, variable) is defined\n");
        sb.append("- `sap_usage_references` — find all places where a symbol is used (where-used list)\n\n");
        sb.append("**Writing:**\n");
        sb.append("- `sap_syntax_check` — check syntax of source code; use `content` param to check inline without saving\n");
        sb.append("- `sap_write_and_check` — create a NEW object and write source in one step (use for new programs, classes, etc.)\n");
        sb.append("- `sap_set_source` — write source to an EXISTING object (use `objectSourceUrl` from sap_get_source or context)\n");
        sb.append("- `sap_create_object` — create an empty object without source (rarely needed; prefer `sap_write_and_check`)\n");
        sb.append("- `sap_activate` — activate an object (only call manually if auto-activation was skipped)\n\n");
        sb.append("**Other:**\n");
        sb.append("- `sap_transport_info` — get transport request info for an object\n");
        sb.append("- `sap_atc_run` — run ATC quality checks (only when explicitly requested)\n");
        sb.append("- `sap_run_unit_test` — execute ABAP Unit tests for a class\n\n");

        // -- Output style instruction --
        sb.append("## Response Style\n\n");
        sb.append("Do NOT include full source code in your text responses. ");
        sb.append("Use tool calls (sap_syntax_check, sap_set_source, sap_write_and_check) to validate and write code. ");
        sb.append("Only show short code snippets (under 10 lines) when explaining specific changes or errors. ");
        sb.append("This keeps responses concise and avoids duplicating code that is already in the tool call.\n\n");

        // -- Research tool / MCP documentation --
        if (hasResearchTool) {
            sb.append("## Research Tool\n\n");
            sb.append("You have a `research` tool that delegates queries to a specialized sub-agent. ");
            sb.append("Use it when you need to:\n");
            sb.append("- Look up SAP documentation or ABAP keyword reference\n");
            sb.append("- Search the SAP Help Portal for configuration, BAdIs, or enhancement spots\n");
            sb.append("- Read and understand existing SAP object source code or structure\n");
            sb.append("- Find class/interface definitions, method signatures, or data element details\n\n");
            sb.append("IMPORTANT: If you encounter a syntax error you cannot fix after one attempt, ");
            sb.append("use the `research` tool to look up the correct ABAP syntax before trying again.\n");
            sb.append("The research sub-agent has access to MCP documentation servers and SAP read tools.\n\n");
        } else {
            sb.append("## SAP Documentation\n\n");
            sb.append("MCP documentation tools (prefixed with mcp_) can search SAP documentation, ");
            sb.append("ABAP keyword reference, and SAP Help Portal.\n\n");
        }

        // -- ADT contexts --
        if (contexts != null && !contexts.isEmpty()) {
            if (contexts.size() == 1) {
                sb.append("## Current Editor Context\n\n");
                appendAdtContext(sb, contexts.get(0));
            } else {
                sb.append("## Editor Contexts\n\n");
                for (int i = 0; i < contexts.size(); i++) {
                    AdtContext ctx = contexts.get(i);
                    if (i == 0) {
                        sb.append("### Active Editor\n\n");
                    } else {
                        sb.append("### Additional Context: ")
                          .append(ctx.getObjectName() != null ? ctx.getObjectName() : "unknown")
                          .append("\n\n");
                    }
                    appendAdtContext(sb, ctx);
                }
            }
        }

        return sb.toString();
    }

    /**
     * Builds the full system prompt without the research tool section.
     */
    public static String buildSystemPrompt(List<AdtContext> contexts, SapToolRegistry registry) {
        return buildSystemPrompt(contexts, registry, false);
    }

    /**
     * Builds the system prompt with a single ADT context.
     *
     * @param context  the current ADT context (may be {@code null})
     * @param registry the tool registry (currently unused; kept for API compat)
     * @return the assembled system prompt
     */
    public static String buildSystemPrompt(AdtContext context, SapToolRegistry registry) {
        List<AdtContext> contexts = (context != null)
                ? Collections.singletonList(context)
                : null;
        return buildSystemPrompt(contexts, registry, false);
    }

    /**
     * Convenience overload that builds the system prompt without a tool registry.
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
