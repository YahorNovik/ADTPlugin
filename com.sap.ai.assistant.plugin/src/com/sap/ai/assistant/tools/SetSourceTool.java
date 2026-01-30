package com.sap.ai.assistant.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.ai.assistant.model.ToolDefinition;
import com.sap.ai.assistant.model.ToolResult;
import com.sap.ai.assistant.sap.AdtRestClient;
import com.sap.ai.assistant.sap.AdtXmlParser;

/**
 * Tool: <b>sap_set_source</b> -- Write (PUT) the ABAP source code of
 * an object. Automatically locks the source URL before writing and
 * unlocks it afterwards.
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

        JsonObject transportProp = new JsonObject();
        transportProp.addProperty("type", "string");
        transportProp.addProperty("description",
                "Optional transport request number (e.g. 'DEVK900123')");

        JsonObject properties = new JsonObject();
        properties.add("objectSourceUrl", urlProp);
        properties.add("source", sourceProp);
        properties.add("transport", transportProp);

        JsonArray required = new JsonArray();
        required.add("objectSourceUrl");
        required.add("source");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);

        return new ToolDefinition(NAME,
                "Write ABAP source code to a repository object. Automatically locks the object before writing "
                        + "and unlocks it afterwards. Provide the full source code, the source URL, "
                        + "and optionally a transport request.",
                schema);
    }

    @Override
    public ToolResult execute(JsonObject arguments) throws Exception {
        String objectSourceUrl = arguments.get("objectSourceUrl").getAsString();
        String source = arguments.get("source").getAsString();
        String transport = optString(arguments, "transport");

        // Step 1: Lock the source URL
        String lockPath = objectSourceUrl + "?_action=LOCK&accessMode=MODIFY";
        HttpResponse<String> lockResp = client.post(lockPath, "",
                "application/*",
                "application/*,application/vnd.sap.as+xml;charset=UTF-8;dataname=com.sap.adt.lock.result");
        String lockHandle = AdtXmlParser.extractLockHandle(lockResp.body());

        if (lockHandle == null || lockHandle.isEmpty()) {
            return ToolResult.error(null,
                    "Failed to acquire lock on " + objectSourceUrl + ". Response: " + lockResp.body());
        }

        try {
            // Step 2: Write source with lockHandle as query parameter
            String separator = objectSourceUrl.contains("?") ? "&" : "?";
            String path = objectSourceUrl + separator + "lockHandle=" + urlEncode(lockHandle);
            if (transport != null && !transport.isEmpty()) {
                path = path + "&corrNr=" + urlEncode(transport);
            }

            HttpResponse<String> response = client.put(path, source, "text/plain; charset=utf-8");

            JsonObject output = new JsonObject();
            output.addProperty("status", "success");
            output.addProperty("statusCode", response.statusCode());
            return ToolResult.success(null, output.toString());
        } finally {
            // Step 3: Always unlock
            try {
                String unlockPath = objectSourceUrl + "?_action=UNLOCK&lockHandle=" + urlEncode(lockHandle);
                client.post(unlockPath, "", "application/*", "application/*");
            } catch (Exception e) {
                System.err.println("SetSourceTool: auto-unlock failed: " + e.getMessage());
            }
        }
    }
}
