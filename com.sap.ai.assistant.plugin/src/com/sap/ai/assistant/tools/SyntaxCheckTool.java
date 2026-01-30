package com.sap.ai.assistant.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.ai.assistant.model.ToolDefinition;
import com.sap.ai.assistant.model.ToolResult;
import com.sap.ai.assistant.sap.AdtRestClient;
import com.sap.ai.assistant.sap.AdtXmlParser;

/**
 * Tool: <b>sap_syntax_check</b> -- Run an ABAP syntax check on the
 * given source URL to detect compilation errors and warnings.
 */
public class SyntaxCheckTool extends AbstractSapTool {

    public static final String NAME = "sap_syntax_check";

    public SyntaxCheckTool(AdtRestClient client) {
        super(client);
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
                "Run an ABAP syntax check on a source object. Returns a list of errors and warnings with "
                        + "line numbers, severity, and message text. Use this after writing code to verify correctness.",
                schema);
    }

    @Override
    public ToolResult execute(JsonObject arguments) throws Exception {
        String url = arguments.get("url").getAsString();
        String content = optString(arguments, "content");
        String mainUrl = optString(arguments, "mainUrl");
        String mainProgram = optString(arguments, "mainProgram");

        StringBuilder xmlBody = new StringBuilder();
        xmlBody.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xmlBody.append("<chkrun:checkObjectList xmlns:chkrun=\"http://www.sap.com/adt/checkrun\" ");
        xmlBody.append("xmlns:adtcore=\"http://www.sap.com/adt/core\">");
        xmlBody.append("<chkrun:checkObject adtcore:uri=\"").append(escapeXml(url)).append("\"");

        if (mainUrl != null && !mainUrl.isEmpty()) {
            xmlBody.append(" chkrun:mainUri=\"").append(escapeXml(mainUrl)).append("\"");
        }
        if (mainProgram != null && !mainProgram.isEmpty()) {
            xmlBody.append(" chkrun:mainProgram=\"").append(escapeXml(mainProgram)).append("\"");
        }

        xmlBody.append(">");

        if (content != null && !content.isEmpty()) {
            xmlBody.append("<chkrun:source><![CDATA[").append(content).append("]]></chkrun:source>");
        }

        xmlBody.append("</chkrun:checkObject>");
        xmlBody.append("</chkrun:checkObjectList>");

        HttpResponse<String> response = client.post(
                "/sap/bc/adt/abapsource/syntaxcheck",
                xmlBody.toString(),
                "application/*",
                "application/*");

        JsonArray results = AdtXmlParser.parseSyntaxCheckResults(response.body());

        JsonObject output = new JsonObject();
        output.addProperty("hasErrors", hasErrors(results));
        output.addProperty("messageCount", results.size());
        output.add("messages", results);
        return ToolResult.success(null, output.toString());
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
