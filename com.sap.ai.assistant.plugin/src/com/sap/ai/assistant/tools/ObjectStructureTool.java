package com.sap.ai.assistant.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonObject;
import com.sap.ai.assistant.model.ToolDefinition;
import com.sap.ai.assistant.model.ToolResult;
import com.sap.ai.assistant.sap.AdtRestClient;
import com.sap.ai.assistant.sap.AdtXmlParser;

/**
 * Tool: <b>sap_object_structure</b> -- Retrieve the metadata and
 * structural information of an ABAP repository object (links, includes,
 * etc.).
 */
public class ObjectStructureTool extends AbstractSapTool {

    public static final String NAME = "sap_object_structure";

    public ObjectStructureTool(AdtRestClient client) {
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
                "The ADT object URL (e.g. '/sap/bc/adt/programs/programs/ztest')");

        JsonObject versionProp = new JsonObject();
        versionProp.addProperty("type", "string");
        versionProp.addProperty("description",
                "Optional version: 'active', 'inactive', or 'workingArea'");

        JsonObject properties = new JsonObject();
        properties.add("objectType", AdtUrlResolver.buildTypeProperty());
        properties.add("objectName", AdtUrlResolver.buildNameProperty());
        properties.add("objectUrl", urlProp);
        properties.add("version", versionProp);

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);

        return new ToolDefinition(NAME,
                "Get structure/metadata of an ABAP object. Provide objectType + objectName, "
                + "or objectUrl. Returns links, includes, and source URLs.",
                schema);
    }

    @Override
    public ToolResult execute(JsonObject arguments) throws Exception {
        String objectUrl = resolveObjectUrlArg(arguments, "objectUrl");
        if (objectUrl == null) {
            return ToolResult.error(null, "Provide either objectType + objectName, or objectUrl.");
        }
        String version = optString(arguments, "version");

        String path = objectUrl;
        if (version != null && !version.isEmpty()) {
            String separator = path.contains("?") ? "&" : "?";
            path = path + separator + "version=" + urlEncode(version);
        }

        HttpResponse<String> response = client.get(path, "application/*");
        JsonObject structure = AdtXmlParser.parseObjectStructure(response.body());
        return ToolResult.success(null, structure.toString());
    }
}
