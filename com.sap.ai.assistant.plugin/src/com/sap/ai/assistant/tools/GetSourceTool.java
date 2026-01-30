package com.sap.ai.assistant.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonObject;
import com.sap.ai.assistant.model.ToolDefinition;
import com.sap.ai.assistant.model.ToolResult;
import com.sap.ai.assistant.sap.AdtRestClient;

/**
 * Tool: <b>sap_get_source</b> -- Retrieve the ABAP source code of an
 * object by its ADT source URL.
 */
public class GetSourceTool extends AbstractSapTool {

    public static final String NAME = "sap_get_source";

    public GetSourceTool(AdtRestClient client) {
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
                "The ADT source URL of the object (e.g. '/sap/bc/adt/programs/programs/ztest/source/main')");

        JsonObject versionProp = new JsonObject();
        versionProp.addProperty("type", "string");
        versionProp.addProperty("description",
                "Optional version: 'active', 'inactive', or 'workingArea'");

        JsonObject properties = new JsonObject();
        properties.add("objectSourceUrl", urlProp);
        properties.add("version", versionProp);

        JsonArray required = new com.google.gson.JsonArray();
        required.add("objectSourceUrl");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);

        return new ToolDefinition(NAME,
                "Retrieve the ABAP source code of a repository object. "
                        + "Returns the plain text source code. Use sap_search_object first to find the object, "
                        + "then sap_object_structure to get the source URL.",
                schema);
    }

    @Override
    public ToolResult execute(JsonObject arguments) throws Exception {
        String objectSourceUrl = arguments.get("objectSourceUrl").getAsString();
        String version = optString(arguments, "version");

        String path = objectSourceUrl;
        if (version != null && !version.isEmpty()) {
            String separator = path.contains("?") ? "&" : "?";
            path = path + separator + "version=" + urlEncode(version);
        }

        HttpResponse<String> response = client.get(path, "text/plain");
        return ToolResult.success(null, response.body());
    }
}
