package com.sap.ai.assistant.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.ai.assistant.model.ToolResult;
import com.sap.ai.assistant.sap.AdtRestClient;
import com.sap.ai.assistant.sap.AdtXmlParser;

/**
 * Abstract base class for DDIC / DDL-source write tools.
 * <p>
 * Provides the shared lifecycle:
 * <ol>
 *   <li>Search for existing object by name</li>
 *   <li>Create object if not found (POST XML metadata)</li>
 *   <li>Lock the object</li>
 *   <li>PUT source code</li>
 *   <li>Unlock the object</li>
 *   <li>Activate the object</li>
 * </ol>
 * Concrete subclasses only need to supply URLs, content types,
 * and the creation XML format for their specific object type.
 * </p>
 */
public abstract class AbstractDdicSourceTool extends AbstractSapTool {

    protected AbstractDdicSourceTool(AdtRestClient client) {
        super(client);
    }

    // ---------------------------------------------------------------
    // Abstract methods that subclasses must implement
    // ---------------------------------------------------------------

    /** Base ADT URL for the object type, e.g. {@code /sap/bc/adt/ddic/tables}. */
    protected abstract String getBaseUrl();

    /** Content-Type header for the creation POST request. */
    protected abstract String getCreateContentType();

    /** Accept header for the creation POST request. */
    protected abstract String getCreateAccept();

    /** ADT object type code for quickSearch, e.g. {@code TABL/DT}. */
    protected abstract String getSearchObjectType();

    /**
     * Build the XML body for object creation.
     *
     * @param name        object name (uppercase)
     * @param description short description
     * @param packageName parent package name
     * @param packagePath parent package ADT path
     * @return XML string for creation POST
     */
    protected abstract String buildCreationXml(String name, String description,
                                                String packageName, String packagePath);

    // ---------------------------------------------------------------
    // Shared lifecycle
    // ---------------------------------------------------------------

    /**
     * Execute the full create-and-write lifecycle.
     * Called from concrete subclass {@code execute()} methods.
     */
    protected ToolResult createAndWriteSource(JsonObject arguments) throws Exception {
        String name = arguments.get("name").getAsString().toUpperCase();
        String source = arguments.get("source").getAsString();
        String description = optString(arguments, "description");
        String packageName = optString(arguments, "packageName");
        String packagePath = optString(arguments, "packagePath");
        String transport = optString(arguments, "transport");

        if (description == null) description = name;
        if (packageName == null) packageName = "$TMP";
        if (packagePath == null) packagePath = "/sap/bc/adt/packages/%24tmp";

        JsonObject output = new JsonObject();
        boolean created = false;
        String objectUrl = null;

        // Step 1: Search for existing object
        objectUrl = searchForObject(name);

        // Step 2: Create if not found
        if (objectUrl == null) {
            objectUrl = createObject(name, description, packageName, packagePath, transport);
            created = true;
        }

        if (objectUrl == null) {
            return ToolResult.error(null,
                    "Could not determine object URL for: " + name);
        }

        output.addProperty("created", created);
        output.addProperty("objectUrl", objectUrl);

        // Step 3: Derive source URL
        String sourceUrl = objectUrl + "/source/main";
        output.addProperty("sourceUrl", sourceUrl);

        // Step 4: Lock + write + unlock
        lockWriteUnlock(objectUrl, sourceUrl, source, transport);

        // Step 5: Activate
        activate(objectUrl, name, output);

        return ToolResult.success(null, output.toString());
    }

    // ---------------------------------------------------------------
    // Search
    // ---------------------------------------------------------------

    /**
     * Search for an existing object by name and type.
     *
     * @return the object URL if found, or {@code null}
     */
    protected String searchForObject(String name) {
        try {
            String searchPath = "/sap/bc/adt/repository/informationsystem/search"
                    + "?operation=quickSearch"
                    + "&query=" + urlEncode(name)
                    + "&maxResults=5"
                    + "&objectType=" + urlEncode(getSearchObjectType());

            HttpResponse<String> resp = client.get(searchPath, "application/*");
            JsonArray results = AdtXmlParser.parseSearchResults(resp.body());

            for (int i = 0; i < results.size(); i++) {
                JsonObject result = results.get(i).getAsJsonObject();
                String resultName = result.has("name") ? result.get("name").getAsString() : "";
                if (resultName.equalsIgnoreCase(name)) {
                    return result.has("uri") ? result.get("uri").getAsString() : null;
                }
            }
        } catch (Exception e) {
            // Search failure is not fatal — we'll try to create
        }
        return null;
    }

    // ---------------------------------------------------------------
    // Create
    // ---------------------------------------------------------------

    /**
     * Create the object via POST.
     *
     * @return the object URL from the Location header, or derived from base URL
     */
    protected String createObject(String name, String description,
                                   String packageName, String packagePath,
                                   String transport) throws Exception {
        String xmlBody = buildCreationXml(name, description, packageName, packagePath);
        String createPath = getBaseUrl();
        if (transport != null && !transport.isEmpty()) {
            createPath += "?corrNr=" + urlEncode(transport);
        }

        try {
            HttpResponse<String> resp = client.post(
                    createPath, xmlBody, getCreateContentType(), getCreateAccept());

            String location = resp.headers().firstValue("Location").orElse(null);
            if (location != null && !location.isEmpty()) {
                return location;
            }
            // Derive from base URL
            return getBaseUrl() + "/" + name.toLowerCase();

        } catch (java.io.IOException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("already exists") || msg.contains("HTTP 500")) {
                return getBaseUrl() + "/" + name.toLowerCase();
            }
            throw e;
        }
    }

    // ---------------------------------------------------------------
    // Lock / Write / Unlock
    // ---------------------------------------------------------------

    /**
     * Lock the object, write source, then unlock.
     * Retries up to 3 times on HTTP 423 (stale lock handle).
     */
    protected void lockWriteUnlock(String objectUrl, String sourceUrl,
                                    String source, String transport) throws Exception {
        final int maxAttempts = 3;
        Exception lastError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                lockWriteUnlockAttempt(objectUrl, sourceUrl, source, transport);
                return;
            } catch (java.io.IOException e) {
                lastError = e;
                String msg = e.getMessage() != null ? e.getMessage() : "";
                if (msg.contains("HTTP 423") && attempt < maxAttempts) {
                    Thread.sleep(500);
                    continue;
                }
                throw e;
            }
        }
        throw lastError;
    }

    private void lockWriteUnlockAttempt(String objectUrl, String sourceUrl,
                                         String source, String transport) throws Exception {
        // Lock the object (not the source URL)
        String lockPath = objectUrl + "?_action=LOCK&accessMode=MODIFY";
        HttpResponse<String> lockResp = client.postWithHeaders(lockPath, "",
                "application/*",
                "application/vnd.sap.as+xml;charset=UTF-8;dataname=com.sap.adt.lock.result;q=0.8, "
                + "application/vnd.sap.as+xml;charset=UTF-8;dataname=com.sap.adt.lock.result2;q=0.9",
                STATEFUL_HEADERS);
        String lockHandle = AdtXmlParser.extractLockHandle(lockResp.body());

        if (lockHandle == null || lockHandle.isEmpty()) {
            throw new java.io.IOException(
                    "Failed to acquire lock on " + objectUrl + ". Response: " + lockResp.body());
        }

        try {
            String writePath = sourceUrl + "?lockHandle=" + urlEncode(lockHandle);
            if (transport != null && !transport.isEmpty()) {
                writePath += "&corrNr=" + urlEncode(transport);
            }
            client.putWithHeaders(writePath, source, "text/plain; charset=utf-8",
                    STATEFUL_HEADERS);
        } finally {
            safeUnlock(objectUrl, lockHandle);
        }
    }

    private void safeUnlock(String objectUrl, String lockHandle) {
        try {
            String unlockPath = objectUrl + "?_action=UNLOCK&lockHandle=" + urlEncode(lockHandle);
            client.postWithHeaders(unlockPath, "", "application/*", "application/*",
                    STATEFUL_HEADERS);
        } catch (Exception e) {
            // Ignore -- lock may already be released
        }
    }

    // ---------------------------------------------------------------
    // Activate
    // ---------------------------------------------------------------

    /**
     * Activate the object after writing.
     */
    protected void activate(String objectUrl, String name, JsonObject output) {
        try {
            String activateXml = "<adtcore:objectReferences xmlns:adtcore=\"http://www.sap.com/adt/core\">"
                    + "<adtcore:objectReference adtcore:uri=\"" + escapeXml(objectUrl)
                    + "\" adtcore:name=\"" + escapeXml(name) + "\"/>"
                    + "</adtcore:objectReferences>";

            client.post(
                    "/sap/bc/adt/activation?method=activate&preauditRequested=true",
                    activateXml,
                    "application/xml",
                    "application/xml,application/vnd.sap.adt.inactivectsobjects.v1+xml;q=0.9");

            output.addProperty("activated", true);
        } catch (Exception e) {
            output.addProperty("activated", false);
            output.addProperty("activationError", e.getMessage());
        }
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    protected String escapeXml(String value) {
        if (value == null) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
