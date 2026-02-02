package com.sap.ai.assistant.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.ai.assistant.model.ToolDefinition;
import com.sap.ai.assistant.model.ToolResult;
import com.sap.ai.assistant.sap.AdtRestClient;

/**
 * Tool: <b>sap_unlock</b> -- Release an exclusive lock previously
 * obtained with {@code sap_lock}.
 */
public class UnlockTool extends AbstractSapTool {

    public static final String NAME = "sap_unlock";

    public UnlockTool(AdtRestClient client) {
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
                "The ADT object URL to unlock (same URL used for sap_lock)");

        JsonObject lockProp = new JsonObject();
        lockProp.addProperty("type", "string");
        lockProp.addProperty("description",
                "The lock handle obtained from sap_lock");

        JsonObject properties = new JsonObject();
        properties.add("objectType", AdtUrlResolver.buildTypeProperty());
        properties.add("objectName", AdtUrlResolver.buildNameProperty());
        properties.add("objectUrl", urlProp);
        properties.add("lockHandle", lockProp);

        JsonArray required = new JsonArray();
        required.add("lockHandle");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);

        return new ToolDefinition(NAME,
                "Unlock an ABAP object after editing. Provide objectType + objectName, or objectUrl.",
                schema);
    }

    @Override
    public ToolResult execute(JsonObject arguments) throws Exception {
        String objectUrl = resolveObjectUrlArg(arguments, "objectUrl");
        if (objectUrl == null) {
            return ToolResult.error(null, "Provide either objectType + objectName, or objectUrl.");
        }
        String lockHandle = arguments.get("lockHandle").getAsString();

        String path = objectUrl + "?_action=UNLOCK&lockHandle=" + urlEncode(lockHandle);

        HttpResponse<String> response = client.postWithHeaders(path, "",
                "application/*",
                "application/*",
                STATEFUL_HEADERS);

        JsonObject output = new JsonObject();
        output.addProperty("status", "unlocked");
        output.addProperty("statusCode", response.statusCode());
        return ToolResult.success(null, output.toString());
    }
}
