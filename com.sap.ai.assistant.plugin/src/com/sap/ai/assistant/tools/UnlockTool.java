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
        properties.add("objectUrl", urlProp);
        properties.add("lockHandle", lockProp);

        JsonArray required = new JsonArray();
        required.add("objectUrl");
        required.add("lockHandle");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);

        return new ToolDefinition(NAME,
                "Release the lock on an ABAP repository object. Always call this after finishing edits "
                        + "to avoid leaving orphaned locks.",
                schema);
    }

    @Override
    public ToolResult execute(JsonObject arguments) throws Exception {
        String objectUrl = arguments.get("objectUrl").getAsString();
        String lockHandle = arguments.get("lockHandle").getAsString();

        String path = objectUrl + "?_action=UNLOCK&lockHandle=" + urlEncode(lockHandle);

        HttpResponse<String> response = client.post(path, "", "application/xml", "application/xml");

        JsonObject output = new JsonObject();
        output.addProperty("status", "unlocked");
        output.addProperty("statusCode", response.statusCode());
        return ToolResult.success(null, output.toString());
    }
}
