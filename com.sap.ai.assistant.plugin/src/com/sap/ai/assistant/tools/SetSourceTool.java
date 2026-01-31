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
                "Write ABAP source code. Locks, writes, and unlocks automatically.",
                schema);
    }

    @Override
    public ToolResult execute(JsonObject arguments) throws Exception {
        String objectSourceUrl = ensureSourceUrl(arguments.get("objectSourceUrl").getAsString());
        String source = arguments.get("source").getAsString();
        String transport = optString(arguments, "transport");

        return lockWriteUnlock(objectSourceUrl, source, transport);
    }

    /**
     * Lock, write, and unlock with retry on HTTP 423 (invalid lock handle).
     * Retries up to 3 times with a 500ms delay between attempts to handle
     * race conditions.
     */
    private ToolResult lockWriteUnlock(String objectSourceUrl, String source,
                                        String transport) throws Exception {
        final int maxAttempts = 3;
        Exception lastError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return lockWriteUnlockAttempt(objectSourceUrl, source, transport);
            } catch (java.io.IOException e) {
                lastError = e;
                String msg = e.getMessage() != null ? e.getMessage() : "";
                if (msg.contains("HTTP 423") && attempt < maxAttempts) {
                    System.err.println("SetSourceTool: lock handle rejected (423), "
                            + "retrying after delay (attempt " + attempt + "/" + maxAttempts + ")...");
                    Thread.sleep(500);
                    continue;
                }
                throw e;
            }
        }
        throw lastError;
    }

    private ToolResult lockWriteUnlockAttempt(String sourceUrl, String source,
                                               String transport) throws Exception {
        // Programs: lock the INCLUDE (source URL). Classes/interfaces: lock the object URL.
        String lockUrl = toLockUrl(sourceUrl);

        System.err.println("SetSourceTool: LOCK on lockUrl=" + lockUrl);
        System.err.println("SetSourceTool: PUT will target sourceUrl=" + sourceUrl);

        // Step 1: Lock
        String lockPath = lockUrl + "?_action=LOCK&accessMode=MODIFY";
        HttpResponse<String> lockResp = client.post(lockPath, "",
                "application/*",
                "application/vnd.sap.as+xml;charset=UTF-8;dataname=com.sap.adt.lock.result;q=0.8, "
                + "application/vnd.sap.as+xml;charset=UTF-8;dataname=com.sap.adt.lock.result2;q=0.9");
        String lockHandle = AdtXmlParser.extractLockHandle(lockResp.body());
        System.err.println("SetSourceTool: lockHandle=" + lockHandle);

        if (lockHandle == null || lockHandle.isEmpty()) {
            return ToolResult.error(null,
                    "Failed to acquire lock on " + lockUrl + ". Response: " + lockResp.body());
        }

        try {
            // Step 2: Write source to the SOURCE URL with lockHandle
            String writePath = sourceUrl + "?lockHandle=" + urlEncode(lockHandle);
            if (transport != null && !transport.isEmpty()) {
                writePath = writePath + "&corrNr=" + urlEncode(transport);
            }

            HttpResponse<String> response = client.put(writePath, source, "text/plain; charset=utf-8");

            JsonObject output = new JsonObject();
            output.addProperty("status", "success");
            output.addProperty("statusCode", response.statusCode());
            return ToolResult.success(null, output.toString());
        } finally {
            safeUnlock(lockUrl, lockHandle);
        }
    }

    private void safeUnlock(String lockUrl, String lockHandle) {
        try {
            String unlockPath = lockUrl + "?_action=UNLOCK&lockHandle=" + urlEncode(lockHandle);
            client.post(unlockPath, "", "application/*", "application/*");
        } catch (Exception e) {
            // Ignore -- lock may already be released or handle invalid
        }
    }
}
