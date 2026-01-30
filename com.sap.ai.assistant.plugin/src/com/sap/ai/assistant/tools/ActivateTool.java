package com.sap.ai.assistant.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.ai.assistant.model.ToolDefinition;
import com.sap.ai.assistant.model.ToolResult;
import com.sap.ai.assistant.sap.AdtRestClient;
import com.sap.ai.assistant.sap.AdtXmlParser;

/**
 * Tool: <b>sap_activate</b> -- Activate one or more ABAP repository
 * objects so their inactive versions become the active (runtime) versions.
 */
public class ActivateTool extends AbstractSapTool {

    public static final String NAME = "sap_activate";

    public ActivateTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ToolDefinition getDefinition() {
        JsonObject nameProp = new JsonObject();
        nameProp.addProperty("type", "string");
        nameProp.addProperty("description",
                "The object name (e.g. 'ZTEST_PROGRAM')");

        JsonObject urlProp = new JsonObject();
        urlProp.addProperty("type", "string");
        urlProp.addProperty("description",
                "The ADT object URL (e.g. '/sap/bc/adt/programs/programs/ztest')");

        JsonObject properties = new JsonObject();
        properties.add("objectName", nameProp);
        properties.add("objectUrl", urlProp);

        JsonArray required = new JsonArray();
        required.add("objectName");
        required.add("objectUrl");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);

        return new ToolDefinition(NAME,
                "Activate an ABAP repository object (make the inactive version active). "
                        + "Call this after writing source code to compile and activate the object.",
                schema);
    }

    @Override
    public ToolResult execute(JsonObject arguments) throws Exception {
        String objectName = arguments.get("objectName").getAsString();
        String objectUrl = arguments.get("objectUrl").getAsString();

        String xmlBody = "<adtcore:objectReferences xmlns:adtcore=\"http://www.sap.com/adt/core\">"
                + "<adtcore:objectReference adtcore:uri=\"" + escapeXml(objectUrl)
                + "\" adtcore:name=\"" + escapeXml(objectName) + "\"/>"
                + "</adtcore:objectReferences>";

        HttpResponse<String> response = client.post(
                "/sap/bc/adt/activation",
                xmlBody,
                "application/xml",
                "application/xml");

        JsonObject result = AdtXmlParser.parseActivationResult(response.body());
        return ToolResult.success(null, result.toString());
    }

    /**
     * Minimal XML attribute value escaping.
     */
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
