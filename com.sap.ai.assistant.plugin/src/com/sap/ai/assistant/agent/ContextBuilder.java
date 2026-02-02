package com.sap.ai.assistant.agent;

import java.util.Collections;
import java.util.List;

import com.sap.ai.assistant.model.AdtContext;
import com.sap.ai.assistant.model.ToolDefinition;
import com.sap.ai.assistant.model.TransportSelection;
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
    // Code Review system prompt
    // ------------------------------------------------------------------

    public static final String CODE_REVIEW_SYSTEM_PROMPT =
            "You are an expert SAP ABAP code reviewer. Your job is to perform a thorough "
            + "code review of ABAP objects, identifying errors, warnings, code quality issues, "
            + "and suggesting improvements.\n\n"
            + "## Workflow\n\n"
            + "1. **Read the source code** using `sap_get_source`\n"
            + "2. **Run syntax check** using `sap_syntax_check` — report ALL errors AND warnings\n"
            + "3. **Run ATC checks** using `sap_atc_run` — report all findings by priority\n"
            + "4. **Review the code** for quality issues (see checklist below)\n\n"
            + "IMPORTANT: Always start with syntax check and ATC — never skip these steps.\n\n"
            + "## Code Review Checklist\n\n"
            + "- **Naming conventions**: Hungarian notation (iv_, ev_, lv_, lt_, etc.), Z/Y custom prefix\n"
            + "- **Error handling**: Missing TRY/CATCH, unchecked sy-subrc after CALL/READ/SELECT\n"
            + "- **Performance**: SELECT *, nested SELECTs in loops, missing WHERE clauses, "
            + "missing FOR ALL ENTRIES, unnecessary LOOP/READ TABLE combinations\n"
            + "- **Security**: Missing authority checks, SQL injection risks in dynamic SQL\n"
            + "- **Modern ABAP style**: Inline declarations (DATA(...)), NEW, VALUE, CONV, "
            + "CORRESPONDING, string templates, functional methods\n"
            + "- **Dead code**: Unused variables, unreachable code, commented-out blocks\n"
            + "- **Documentation**: Missing class/method descriptions, unclear logic without comments\n"
            + "- **Hardcoded values**: Magic numbers, hardcoded texts (should use message class)\n\n"
            + "## ADT URL Patterns for sap_get_source\n\n"
            + "Use these URLs with `sap_get_source` (object names MUST be lowercase):\n"
            + "- **Class**: `/sap/bc/adt/oo/classes/{class}/source/main`\n"
            + "- **Interface**: `/sap/bc/adt/oo/interfaces/{intf}/source/main`\n"
            + "- **Program**: `/sap/bc/adt/programs/programs/{prog}/source/main`\n"
            + "- **Function module**: `/sap/bc/adt/functions/groups/{group}/fmodules/{fm}/source/main`\n"
            + "- **CDS view**: `/sap/bc/adt/ddic/ddl/sources/{view}/source/main`\n"
            + "- **Table fields**: `/sap/bc/adt/ddic/tables/{table}/source/main`\n"
            + "- **Structure fields**: `/sap/bc/adt/ddic/structures/{struct}/source/main`\n"
            + "- **Data element**: `/sap/bc/adt/ddic/dataelements/{dtel}/source/main`\n"
            + "- **Domain**: `/sap/bc/adt/ddic/domains/{domain}/source/main`\n\n"
            + "## Reading Data vs Structure\n\n"
            + "| Need | Tool | Example |\n"
            + "|------|------|---------|\n"
            + "| **Field definitions** | `sap_get_source` | `.../tables/mara/source/main` → field names, types |\n"
            + "| **Data rows** | `sap_sql_query` | `SELECT matnr FROM mara UP TO 5 ROWS` → row values |\n"
            + "| **Data element details** | `sap_type_info` | name='MATNR' → domain, type, labels |\n\n"
            + "## SAP Documentation (MCP Tools)\n\n"
            + "Use these for documentation lookup during review:\n"
            + "- `mcp_sap_help_search` — ABAP keyword reference, BAdIs, SAP standard behavior\n"
            + "- `mcp_sap_help_get` — fetch full SAP Help page\n"
            + "- `mcp_sap_community_search` — best practices and community solutions\n\n"
            + "## Output Format\n\n"
            + "Structure your review as:\n\n"
            + "### Syntax Check Results\n"
            + "List all errors and warnings with line numbers.\n\n"
            + "### ATC Findings\n"
            + "List all findings with priority and description.\n\n"
            + "### Code Review\n"
            + "Group findings by category (naming, performance, security, etc.). "
            + "Reference specific line numbers and show short code snippets (under 5 lines).\n\n"
            + "### Summary\n"
            + "Overall code quality assessment and priority-ordered list of recommended changes.\n\n"
            + "## Guidelines\n\n"
            + "- Report warnings, not just errors — warnings indicate potential issues\n"
            + "- Be specific: cite line numbers and show problematic code\n"
            + "- For each issue, explain WHY it is a problem and HOW to fix it\n"
            + "- If the object is clean, say so — do not invent issues\n"
            + "- Keep code snippets short (under 5 lines)\n";

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

        // -- Function module workflow --
        sb.append("## Creating Function Modules\n\n");
        sb.append("Function modules (FUGR/FF) live inside function groups (FUGR/F). To create one:\n");
        sb.append("1. Create the function group first (if it doesn't exist) using `sap_create_object` with objtype='FUGR/F'\n");
        sb.append("2. Create the function module using `sap_write_and_check` or `sap_create_object` with objtype='FUGR/FF' ");
        sb.append("AND the `functionGroup` parameter set to the parent group name\n");
        sb.append("3. The `functionGroup` parameter is REQUIRED for FUGR/FF — without it, the tool will return an error\n");
        sb.append("4. Source URL for function modules: `/sap/bc/adt/functions/groups/{group}/fmodules/{fm_name}/source/main`\n\n");

        // -- Function module source format --
        sb.append("## Function Module Source Format\n\n");
        sb.append("CRITICAL: NEVER use `*\"` comment lines in function module source. ");
        sb.append("Any line starting with `*\"` causes 'Parameter comment blocks are not allowed' errors.\n\n");
        sb.append("Define parameters INLINE in the FUNCTION statement:\n");
        sb.append("```\nFUNCTION z_my_func\n");
        sb.append("  IMPORTING\n");
        sb.append("    iv_param TYPE string\n");
        sb.append("  EXPORTING\n");
        sb.append("    ev_result TYPE string.\n\n");
        sb.append("  \" implementation here\n");
        sb.append("ENDFUNCTION.\n```\n\n");
        sb.append("For function modules with no parameters:\n");
        sb.append("```\nFUNCTION z_my_func.\n  \" implementation here\nENDFUNCTION.\n```\n\n");

        // NOTE: Tool descriptions are NOT listed here — they are already sent
        // via the API's `tools` parameter. Listing them here would duplicate
        // ~800-1000 tokens on every request.

        // -- Think before acting --
        sb.append("## Think Before Acting\n\n");
        sb.append("Before making any tool calls, briefly outline your approach in 1-3 sentences:\n");
        sb.append("- What objects need to be read, created, or modified\n");
        sb.append("- What order you will do it in and why\n");
        sb.append("- Any dependencies between steps (e.g. function group before function module)\n\n");
        sb.append("This helps the user understand your plan and catch mistakes early. ");
        sb.append("For simple single-object tasks (read source, check syntax), a one-sentence summary is enough.\n\n");

        // -- Output style instruction --
        sb.append("## Response Style\n\n");
        sb.append("Do NOT include full source code in your text responses. ");
        sb.append("Use tool calls (sap_syntax_check, sap_set_source, sap_write_and_check) to validate and write code. ");
        sb.append("Only show short code snippets (under 10 lines) when explaining specific changes or errors. ");
        sb.append("This keeps responses concise and avoids duplicating code that is already in the tool call.\n\n");

        // -- Reading source code and data --
        sb.append("## Reading Source Code and Data\n\n");
        sb.append("Use `sap_get_source` to read source code and field definitions. ");
        sb.append("URL patterns (object names MUST be lowercase):\n");
        sb.append("- **Table fields**: `/sap/bc/adt/ddic/tables/{table}/source/main` (e.g. `.../tables/mara/source/main`)\n");
        sb.append("- **Structure fields**: `/sap/bc/adt/ddic/structures/{struct}/source/main`\n");
        sb.append("- **CDS view (DDL)**: `/sap/bc/adt/ddic/ddl/sources/{view}/source/main`\n");
        sb.append("- **Data element**: `/sap/bc/adt/ddic/dataelements/{dtel}/source/main`\n");
        sb.append("- **Domain**: `/sap/bc/adt/ddic/domains/{domain}/source/main`\n");
        sb.append("- **Class**: `/sap/bc/adt/oo/classes/{class}/source/main`\n");
        sb.append("- **Interface**: `/sap/bc/adt/oo/interfaces/{intf}/source/main`\n");
        sb.append("- **Program**: `/sap/bc/adt/programs/programs/{prog}/source/main`\n");
        sb.append("- **Function module**: `/sap/bc/adt/functions/groups/{group}/fmodules/{fm}/source/main`\n\n");
        sb.append("**Reading data vs structure** — these tools serve DIFFERENT purposes:\n\n");
        sb.append("| Need | Tool | Example |\n");
        sb.append("|------|------|---------|\n");
        sb.append("| **Table/structure field definitions** | `sap_get_source` | `.../tables/mara/source/main` → field names, types, lengths |\n");
        sb.append("| **Actual data rows from a table** | `sap_sql_query` | `SELECT matnr, mtart FROM mara UP TO 10 ROWS` → row values |\n");
        sb.append("| **Data element/domain details** | `sap_type_info` | name='MATNR' → domain, data type, length, labels |\n\n");
        sb.append("`sap_sql_query` executes real ABAP SQL against the database and returns DATA ROWS (not structure). ");
        sb.append("Example: `SELECT carrid, connid, fldate FROM sflight UP TO 5 ROWS`.\n\n");

        // -- Research tool / MCP documentation --
        if (hasResearchTool) {
            sb.append("## Research Tool\n\n");
            sb.append("You have a `research` tool that delegates queries to a specialized sub-agent. ");
            sb.append("Use it when you need to:\n");
            sb.append("- Look up ABAP keyword reference or SAP Help Portal documentation\n");
            sb.append("- Search for BAdIs, enhancement spots, or configuration details\n");
            sb.append("- Read and understand existing SAP object source code or structure\n");
            sb.append("- Find class/interface definitions, method signatures, or data element details\n");
            sb.append("- Search SAP Community for real-world examples and solutions\n\n");
            sb.append("IMPORTANT: If you encounter a syntax error you cannot fix after one attempt, ");
            sb.append("use the `research` tool to look up the correct ABAP syntax before trying again.\n");
            sb.append("The research sub-agent has access to SAP read tools and documentation tools ");
            sb.append("(SAP Help Portal, SAP Community, SAPUI5/CAP docs).\n\n");
        } else {
            sb.append("## SAP Documentation (MCP Tools)\n\n");
            sb.append("Use these tools to look up SAP documentation:\n");
            sb.append("- `mcp_sap_help_search` — search SAP Help Portal (ABAP keyword reference, ");
            sb.append("transactions, BAdIs, configuration)\n");
            sb.append("- `mcp_sap_help_get` — fetch full SAP Help page content\n");
            sb.append("- `mcp_sap_community_search` — search SAP Community for blog posts and solutions\n");
            sb.append("- `mcp_sap_docs_search` — search SAPUI5, CAP, and OpenUI5 documentation\n");
            sb.append("- `mcp_sap_docs_get` — fetch full documentation page content\n\n");
            sb.append("**Workflow**: Search first, then fetch full content for relevant results.\n\n");
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
     * Builds the full system prompt including transport session info.
     */
    public static String buildSystemPrompt(List<AdtContext> contexts, SapToolRegistry registry,
                                              boolean hasResearchTool,
                                              TransportSelection transport) {
        String base = buildSystemPrompt(contexts, registry, hasResearchTool);
        return base + buildTransportSection(transport);
    }

    /**
     * Builds the system prompt section that tells the LLM about the active
     * transport selection so it does not ask the user about transports.
     */
    static String buildTransportSection(TransportSelection transport) {
        if (transport == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("\n## Active Transport\n\n");
        if (transport.isLocal()) {
            sb.append("All objects will be saved as LOCAL ($TMP). ");
            sb.append("Do NOT ask the user about transport requests. ");
            sb.append("Always use parentName='$TMP' and parentPath='/sap/bc/adt/packages/%24tmp'.\n");
        } else {
            sb.append("Transport request: ").append(transport.getTransportNumber()).append("\n");
            sb.append("Always include transport='").append(transport.getTransportNumber())
              .append("' when creating or modifying objects. Do NOT ask the user about transports.\n");
        }
        return sb.toString();
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
    // Public helpers
    // ------------------------------------------------------------------

    /**
     * Builds just the editor context portion of a system prompt.
     * Used by non-main agents (e.g. Research) that have their own base prompt
     * but still need editor context awareness.
     *
     * @param contexts the list of ADT contexts (may be {@code null} or empty)
     * @return the editor context section, or empty string if no contexts
     */
    public static String buildEditorContextSection(List<AdtContext> contexts) {
        if (contexts == null || contexts.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        if (contexts.size() == 1) {
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
