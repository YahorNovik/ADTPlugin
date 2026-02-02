package com.sap.ai.assistant.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.ai.assistant.model.ToolDefinition;
import com.sap.ai.assistant.model.ToolResult;
import com.sap.ai.assistant.sap.AdtRestClient;
import com.sap.ai.assistant.sap.AdtXmlParser;

/**
 * Tool: <b>sap_get_includes</b> -- List includes of a program,
 * class, or function group.
 *
 * <p>Endpoint: {@code GET {objectUrl}} with Accept: application/*</p>
 */
public class GetIncludesTool extends AbstractSapTool {

    public static final String NAME = "sap_get_includes";

    public GetIncludesTool(AdtRestClient client) {
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
                "ADT URL of the program, class, or function group "
                + "(e.g. '/sap/bc/adt/programs/programs/ztest')");

        JsonObject properties = new JsonObject();
        properties.add("objectType", AdtUrlResolver.buildTypeProperty());
        properties.add("objectName", AdtUrlResolver.buildNameProperty());
        properties.add("objectUrl", urlProp);

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);

        return new ToolDefinition(NAME,
                "List includes of a program, class, or function group. "
                + "Provide objectType + objectName, or objectUrl.",
                schema);
    }

    @Override
    public ToolResult execute(JsonObject arguments) throws Exception {
        String objectUrl = resolveObjectUrlArg(arguments, "objectUrl");
        if (objectUrl == null) {
            return ToolResult.error(null, "Provide either objectType + objectName, or objectUrl.");
        }

        HttpResponse<String> resp = client.get(objectUrl, "application/*");
        JsonObject structure = AdtXmlParser.parseObjectStructure(resp.body());

        JsonObject result = new JsonObject();
        JsonArray includes = structure.has("includes")
                ? structure.getAsJsonArray("includes")
                : new JsonArray();
        result.addProperty("totalIncludes", includes.size());
        result.add("includes", includes);

        return ToolResult.success(null, result.toString());
    }
}
