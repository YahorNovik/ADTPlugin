package com.sap.ai.assistant.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.ai.assistant.model.ToolDefinition;
import com.sap.ai.assistant.model.ToolResult;
import com.sap.ai.assistant.sap.AdtRestClient;

/**
 * Tool: <b>sap_find_definition</b> -- Navigate to the definition of an
 * ABAP element (class, method, variable, etc.) from a given source
 * position, similar to "Go to Definition" in an IDE.
 */
public class FindDefinitionTool extends AbstractSapTool {

    public static final String NAME = "sap_find_definition";

    public FindDefinitionTool(AdtRestClient client) {
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
                "The ADT source URL containing the element (e.g. '/sap/bc/adt/programs/programs/ztest/source/main')");

        JsonObject sourceProp = new JsonObject();
        sourceProp.addProperty("type", "string");
        sourceProp.addProperty("description",
                "The ABAP source code of the file (used for context)");

        JsonObject lineProp = new JsonObject();
        lineProp.addProperty("type", "integer");
        lineProp.addProperty("description",
                "1-based line number where the element is located");

        JsonObject startColProp = new JsonObject();
        startColProp.addProperty("type", "integer");
        startColProp.addProperty("description",
                "0-based start column of the element");

        JsonObject endColProp = new JsonObject();
        endColProp.addProperty("type", "integer");
        endColProp.addProperty("description",
                "0-based end column of the element");

        JsonObject properties = new JsonObject();
        properties.add("url", urlProp);
        properties.add("source", sourceProp);
        properties.add("line", lineProp);
        properties.add("startColumn", startColProp);
        properties.add("endColumn", endColProp);

        JsonArray required = new JsonArray();
        required.add("url");
        required.add("source");
        required.add("line");
        required.add("startColumn");
        required.add("endColumn");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);

        return new ToolDefinition(NAME,
                "Find the definition location of an ABAP element (class, method, variable, type, etc.) "
                        + "given a source position. Returns the URI and position of the definition.",
                schema);
    }

    @Override
    public ToolResult execute(JsonObject arguments) throws Exception {
        String url = arguments.get("url").getAsString();
        String source = arguments.get("source").getAsString();
        int line = arguments.get("line").getAsInt();
        int startColumn = arguments.get("startColumn").getAsInt();
        int endColumn = arguments.get("endColumn").getAsInt();

        StringBuilder xmlBody = new StringBuilder();
        xmlBody.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xmlBody.append("<navigation:navigationRequest xmlns:navigation=\"http://www.sap.com/adt/navigation\">");
        xmlBody.append("<navigation:sourceObject uri=\"").append(escapeXml(url)).append("\" ");
        xmlBody.append("line=\"").append(line).append("\" ");
        xmlBody.append("startColumn=\"").append(startColumn).append("\" ");
        xmlBody.append("endColumn=\"").append(endColumn).append("\">");
        xmlBody.append("<navigation:source><![CDATA[").append(source).append("]]></navigation:source>");
        xmlBody.append("</navigation:sourceObject>");
        xmlBody.append("</navigation:navigationRequest>");

        HttpResponse<String> response = client.post(
                "/sap/bc/adt/navigation/targets",
                xmlBody.toString(),
                "application/xml",
                "application/xml");

        // Return the raw response; the navigation result contains the
        // target URI, line number, and column of the definition
        JsonObject output = new JsonObject();
        output.addProperty("statusCode", response.statusCode());
        output.addProperty("response", response.body());
        return ToolResult.success(null, output.toString());
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
