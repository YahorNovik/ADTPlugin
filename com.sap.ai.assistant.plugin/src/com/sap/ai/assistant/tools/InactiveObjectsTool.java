package com.sap.ai.assistant.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.ai.assistant.model.ToolDefinition;
import com.sap.ai.assistant.model.ToolResult;
import com.sap.ai.assistant.sap.AdtRestClient;
import com.sap.ai.assistant.sap.AdtXmlParser;

/**
 * Tool: <b>sap_inactive_objects</b> -- List objects that have been
 * modified but not yet activated.
 *
 * <p>Endpoint: {@code GET /sap/bc/adt/activation/inactiveobjects}</p>
 */
public class InactiveObjectsTool extends AbstractSapTool {

    public static final String NAME = "sap_inactive_objects";

    public InactiveObjectsTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ToolDefinition getDefinition() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", new JsonObject());

        return new ToolDefinition(NAME,
                "List objects that have been modified but not yet activated (inactive objects).",
                schema);
    }

    @Override
    public ToolResult execute(JsonObject arguments) throws Exception {
        HttpResponse<String> resp = client.get(
                "/sap/bc/adt/activation/inactiveobjects",
                "application/vnd.sap.adt.inactivectsobjects.v1+xml, application/xml;q=0.8");

        JsonObject result = AdtXmlParser.parseInactiveObjects(resp.body());
        return ToolResult.success(null, result.toString());
    }
}
