package com.sap.ai.assistant.tools;

import java.net.http.HttpResponse;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.ai.assistant.model.ToolDefinition;
import com.sap.ai.assistant.model.ToolResult;
import com.sap.ai.assistant.sap.AdtRestClient;
import com.sap.ai.assistant.sap.AdtXmlParser;

/**
 * Tool: <b>sap_syntax_check</b> -- Run an ABAP syntax check on the
 * given source URL to detect compilation errors and warnings.
 *
 * <p>When checking code for objects that do not yet exist in the SAP system,
 * this tool transparently falls back to a scratch placeholder object so
 * that the syntax check API has a valid URL to work against.</p>
 */
public class SyntaxCheckTool extends AbstractSapTool {

    public static final String NAME = "sap_syntax_check";

    private final ScratchObjectManager scratchManager;

    public SyntaxCheckTool(AdtRestClient client) {
        super(client);
        this.scratchManager = new ScratchObjectManager(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ToolDefinition getDefinition() {
        JsonObject urlProp = new JsonObject();
        urlProp.addProperty("type", "string");
        urlProp.addProperty("description",
                "The ADT source URL to check (e.g. '/sap/bc/adt/programs/programs/ztest/source/main')");

        JsonObject contentProp = new JsonObject();
        contentProp.addProperty("type", "string");
        contentProp.addProperty("description",
                "Optional: ABAP source content to check (if not provided, the saved version is checked)");

        JsonObject mainUrlProp = new JsonObject();
        mainUrlProp.addProperty("type", "string");
        mainUrlProp.addProperty("description",
                "Optional: URL of the main program (for includes)");

        JsonObject mainProgramProp = new JsonObject();
        mainProgramProp.addProperty("type", "string");
        mainProgramProp.addProperty("description",
                "Optional: name of the main program (for includes)");

        JsonObject properties = new JsonObject();
        properties.add("url", urlProp);
        properties.add("content", contentProp);
        properties.add("mainUrl", mainUrlProp);
        properties.add("mainProgram", mainProgramProp);

        JsonArray required = new JsonArray();
        required.add("url");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);

        return new ToolDefinition(NAME,
                "Run an ABAP syntax check. When the `content` parameter is provided, checks the given "
                        + "source code WITHOUT saving it to the repository — use this to validate generated code "
                        + "before writing. When `content` is omitted, checks the already-saved version. "
                        + "Returns errors and warnings with line numbers, severity, and message text.",
                schema);
    }

    @Override
    public ToolResult execute(JsonObject arguments) throws Exception {
        String url = ensureSourceUrl(arguments.get("url").getAsString());
        String content = optString(arguments, "content");
        String mainUrl = optString(arguments, "mainUrl");
        String mainProgram = optString(arguments, "mainProgram");

        String xmlBody = buildSyntaxCheckXml(url, content, mainUrl, mainProgram);

        try {
            HttpResponse<String> response = client.post(
                    "/sap/bc/adt/abapsource/syntaxcheck",
                    xmlBody,
                    "application/*",
                    "application/*");

            return buildResult(response);

        } catch (Exception e) {
            // If content was provided, try scratch fallback for new (non-existent) objects
            if (content != null && !content.isEmpty()) {
                String originalName = extractObjectNameFromUrl(url);
                String scratchUrl = scratchManager.getScratchSourceUrl(url);

                if (scratchUrl != null && originalName != null) {
                    String scratchName = extractObjectNameFromUrl(scratchUrl);
                    if (scratchName != null) {
                        // Replace the original object name in source with the scratch name
                        // so SAP's validation passes (it checks that the name in source
                        // matches the object URL). Case-insensitive replacement.
                        String adjustedContent = content.replaceAll(
                                "(?i)" + Pattern.quote(originalName), scratchName);

                        String retryXml = buildSyntaxCheckXml(
                                scratchUrl, adjustedContent, mainUrl, mainProgram);

                        HttpResponse<String> retryResp = client.post(
                                "/sap/bc/adt/abapsource/syntaxcheck",
                                retryXml,
                                "application/*",
                                "application/*");

                        return buildResult(retryResp);
                    }
                }
            }
            throw e; // no fallback available — propagate original error
        }
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    /**
     * Build the XML body for the syntax check request.
     */
    private String buildSyntaxCheckXml(String url, String content,
                                        String mainUrl, String mainProgram) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<chkrun:checkObjectList xmlns:chkrun=\"http://www.sap.com/adt/checkrun\" ");
        xml.append("xmlns:adtcore=\"http://www.sap.com/adt/core\">");
        xml.append("<chkrun:checkObject adtcore:uri=\"").append(escapeXml(url)).append("\"");

        if (mainUrl != null && !mainUrl.isEmpty()) {
            xml.append(" chkrun:mainUri=\"").append(escapeXml(mainUrl)).append("\"");
        }
        if (mainProgram != null && !mainProgram.isEmpty()) {
            xml.append(" chkrun:mainProgram=\"").append(escapeXml(mainProgram)).append("\"");
        }

        xml.append(">");

        if (content != null && !content.isEmpty()) {
            xml.append("<chkrun:source><![CDATA[").append(content).append("]]></chkrun:source>");
        }

        xml.append("</chkrun:checkObject>");
        xml.append("</chkrun:checkObjectList>");
        return xml.toString();
    }

    /**
     * Parse the syntax check response and build a {@link ToolResult}.
     */
    private ToolResult buildResult(HttpResponse<String> response) {
        JsonArray results = AdtXmlParser.parseSyntaxCheckResults(response.body());

        JsonObject output = new JsonObject();
        output.addProperty("hasErrors", hasErrors(results));
        output.addProperty("messageCount", results.size());
        output.add("messages", results);
        return ToolResult.success(null, output.toString());
    }

    /**
     * Extract the object name from an ADT source URL.
     * <p>
     * For example, given
     * {@code /sap/bc/adt/oo/classes/zcl_customer_api/source/main}
     * this returns {@code "zcl_customer_api"}.
     * </p>
     *
     * @param url the ADT source URL
     * @return the object name, or {@code null} if it cannot be determined
     */
    static String extractObjectNameFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        // Strip /source/main (or /source/main/...) suffix if present
        String path = url;
        int sourceIdx = path.indexOf("/source/");
        if (sourceIdx >= 0) {
            path = path.substring(0, sourceIdx);
        }
        // Strip query string
        int queryIdx = path.indexOf('?');
        if (queryIdx >= 0) {
            path = path.substring(0, queryIdx);
        }
        // Take the last path segment
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < path.length() - 1) {
            return path.substring(lastSlash + 1);
        }
        return null;
    }

    private boolean hasErrors(JsonArray messages) {
        for (int i = 0; i < messages.size(); i++) {
            JsonObject msg = messages.get(i).getAsJsonObject();
            String severity = msg.has("severity") ? msg.get("severity").getAsString() : "";
            if (severity.equalsIgnoreCase("error") || severity.equalsIgnoreCase("E")) {
                return true;
            }
        }
        return false;
    }

    private String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
