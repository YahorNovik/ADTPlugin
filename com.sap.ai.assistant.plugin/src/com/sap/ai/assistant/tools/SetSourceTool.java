package com.sap.ai.assistant.tools;

import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.ai.assistant.model.ToolDefinition;
import com.sap.ai.assistant.model.ToolResult;
import com.sap.ai.assistant.sap.AdtRestClient;

/**
 * Tool: <b>sap_set_source</b> -- Write (PUT) the ABAP source code of
 * an object. Requires a valid lock handle obtained via {@code sap_lock}.
 */
public class SetSourceTool extends AbstractSapTool {

    public static final String NAME = "sap_set_source";

    public SetSourceTool(AdtRestClient client) {
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

        JsonObject sourceProp = new JsonObject();
        sourceProp.addProperty("type", "string");
        sourceProp.addProperty("description",
                "The complete ABAP source code to write");

        JsonObject lockProp = new JsonObject();
        lockProp.addProperty("type", "string");
        lockProp.addProperty("description",
                "The lock handle obtained from sap_lock");

        JsonObject transportProp = new JsonObject();
        transportProp.addProperty("type", "string");
        transportProp.addProperty("description",
                "Optional transport request number (e.g. 'DEVK900123')");

        JsonObject properties = new JsonObject();
        properties.add("objectSourceUrl", urlProp);
        properties.add("source", sourceProp);
        properties.add("lockHandle", lockProp);
        properties.add("transport", transportProp);

        JsonArray required = new JsonArray();
        required.add("objectSourceUrl");
        required.add("source");
        required.add("lockHandle");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);

        return new ToolDefinition(NAME,
                "Write ABAP source code to a repository object. The object must be locked first using sap_lock. "
                        + "Provide the full source code, the source URL, the lock handle, and optionally a transport request.",
                schema);
    }

    @Override
    public ToolResult execute(JsonObject arguments) throws Exception {
        String objectSourceUrl = arguments.get("objectSourceUrl").getAsString();
        String source = arguments.get("source").getAsString();
        String lockHandle = arguments.get("lockHandle").getAsString();
        String transport = optString(arguments, "transport");

        Map<String, String> extraHeaders = new HashMap<>();
        extraHeaders.put("Lock-Handle", lockHandle);
        if (transport != null && !transport.isEmpty()) {
            extraHeaders.put("X-Transport", transport);
        }

        HttpResponse<String> response = client.putWithHeaders(
                objectSourceUrl, source, "text/plain", extraHeaders);

        JsonObject output = new JsonObject();
        output.addProperty("status", "success");
        output.addProperty("statusCode", response.statusCode());
        return ToolResult.success(null, output.toString());
    }
}
