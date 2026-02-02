package com.sap.ai.assistant.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonObject;

import com.sap.ai.assistant.model.ToolDefinition;
import com.sap.ai.assistant.model.ToolResult;
import com.sap.ai.assistant.sap.AdtRestClient;

/**
 * Tool: <b>sap_get_source</b> -- Retrieve the ABAP source code of an
 * object by type + name, or by raw ADT URL.
 *
 * <p>Supports two calling modes:</p>
 * <ol>
 *   <li>{@code objectType} + {@code objectName} — the tool resolves the URL</li>
 *   <li>{@code objectSourceUrl} — raw ADT URL (legacy / advanced usage)</li>
 * </ol>
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
                "Alternative: raw ADT source URL. Use objectType + objectName instead when possible. "
                + "Example: '/sap/bc/adt/functions/groups/zfg/fmodules/zfm/source/main'");

        JsonObject versionProp = new JsonObject();
        versionProp.addProperty("type", "string");
        versionProp.addProperty("description",
                "Optional version: 'active', 'inactive', or 'workingArea'");

        JsonObject properties = new JsonObject();
        properties.add("objectType", AdtUrlResolver.buildTypeProperty());
        properties.add("objectName", AdtUrlResolver.buildNameProperty());
        properties.add("objectSourceUrl", urlProp);
        properties.add("version", versionProp);

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);

        return new ToolDefinition(NAME,
                "Read source code of an ABAP object. Provide objectType + objectName "
                + "(e.g. type='CLAS', name='ZCL_MY_CLASS') or a raw objectSourceUrl. "
                + "For tables/structures this returns field definitions, not data rows — "
                + "use sap_sql_query for actual data.",
                schema);
    }

    @Override
    public ToolResult execute(JsonObject arguments) throws Exception {
        String version = optString(arguments, "version");

        String path = resolveSourceUrlArg(arguments, "objectSourceUrl");
        if (path == null) {
            return ToolResult.error(null,
                    "Provide either objectType + objectName, or objectSourceUrl.");
        }

        if (version != null && !version.isEmpty()) {
            String separator = path.contains("?") ? "&" : "?";
            path = path + separator + "version=" + urlEncode(version);
        }

        HttpResponse<String> response = client.get(path, "text/plain");
        return ToolResult.success(null, response.body());
    }
}
