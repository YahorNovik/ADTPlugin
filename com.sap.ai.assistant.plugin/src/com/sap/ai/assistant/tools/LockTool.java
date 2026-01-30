package com.sap.ai.assistant.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.ai.assistant.model.ToolDefinition;
import com.sap.ai.assistant.model.ToolResult;
import com.sap.ai.assistant.sap.AdtRestClient;
import com.sap.ai.assistant.sap.AdtXmlParser;

/**
 * Tool: <b>sap_lock</b> -- Acquire an exclusive lock on an ABAP
 * repository object so it can be edited.
 */
public class LockTool extends AbstractSapTool {

    public static final String NAME = "sap_lock";

    public LockTool(AdtRestClient client) {
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
                "The ADT object URL to lock (e.g. '/sap/bc/adt/programs/programs/ztest')");

        JsonObject modeProp = new JsonObject();
        modeProp.addProperty("type", "string");
        modeProp.addProperty("description",
                "Lock access mode (default 'MODIFY')");

        JsonObject properties = new JsonObject();
        properties.add("objectUrl", urlProp);
        properties.add("accessMode", modeProp);

        JsonArray required = new JsonArray();
        required.add("objectUrl");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);

        return new ToolDefinition(NAME,
                "Lock an ABAP repository object for editing. Returns a lock handle that must be "
                        + "passed to sap_set_source when writing code. Remember to call sap_unlock when done.",
                schema);
    }

    @Override
    public ToolResult execute(JsonObject arguments) throws Exception {
        String objectUrl = arguments.get("objectUrl").getAsString();
        String accessMode = optString(arguments, "accessMode");
        if (accessMode == null || accessMode.isEmpty()) {
            accessMode = "MODIFY";
        }

        String path = objectUrl + "?_action=LOCK&accessMode=" + urlEncode(accessMode);

        HttpResponse<String> response = client.post(path, "",
                "application/*",
                "application/*,application/vnd.sap.as+xml;charset=UTF-8;dataname=com.sap.adt.lock.result");
        String lockHandle = AdtXmlParser.extractLockHandle(response.body());

        if (lockHandle == null || lockHandle.isEmpty()) {
            return ToolResult.error(null,
                    "Failed to obtain lock handle. Response: " + response.body());
        }

        JsonObject output = new JsonObject();
        output.addProperty("lockHandle", lockHandle);
        output.addProperty("objectUrl", objectUrl);
        return ToolResult.success(null, output.toString());
    }
}
