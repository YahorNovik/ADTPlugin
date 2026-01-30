package com.sap.ai.assistant.tools;

import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.ai.assistant.model.ToolDefinition;
import com.sap.ai.assistant.model.ToolResult;
import com.sap.ai.assistant.sap.AdtRestClient;
import com.sap.ai.assistant.sap.AdtXmlParser;

/**
 * Tool: <b>sap_write_and_check</b> -- Composite tool that performs a
 * full write-cycle: search/create the object, get its structure, lock,
 * write source, and run a syntax check. This reduces the number of
 * round-trips the LLM needs to make for common edit operations.
 *
 * <p>Workflow:
 * <ol>
 *   <li>Search for the object by name and type</li>
 *   <li>Create the object if it does not exist</li>
 *   <li>GET the object structure to find the source URL</li>
 *   <li>Lock the object (or reuse a supplied lock handle)</li>
 *   <li>PUT the source code</li>
 *   <li>Run a syntax check</li>
 * </ol>
 * </p>
 */
public class WriteAndCheckTool extends AbstractSapTool {

    public static final String NAME = "sap_write_and_check";

    /** Mapping from object type codes to ADT REST creation URLs. */
    private static final Map<String, String> TYPE_URL_MAP = new HashMap<>();
    /** Mapping from object type codes to ADT REST object base URLs. */
    private static final Map<String, String> TYPE_BASE_URL_MAP = new HashMap<>();

    static {
        TYPE_URL_MAP.put("PROG/P", "/sap/bc/adt/programs/programs");
        TYPE_URL_MAP.put("CLAS/OC", "/sap/bc/adt/oo/classes");
        TYPE_URL_MAP.put("INTF/OI", "/sap/bc/adt/oo/interfaces");
        TYPE_URL_MAP.put("FUGR/F", "/sap/bc/adt/functions/groups");

        TYPE_BASE_URL_MAP.put("PROG/P", "/sap/bc/adt/programs/programs/");
        TYPE_BASE_URL_MAP.put("CLAS/OC", "/sap/bc/adt/oo/classes/");
        TYPE_BASE_URL_MAP.put("INTF/OI", "/sap/bc/adt/oo/interfaces/");
        TYPE_BASE_URL_MAP.put("FUGR/F", "/sap/bc/adt/functions/groups/");
    }

    public WriteAndCheckTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ToolDefinition getDefinition() {
        JsonObject objtypeProp = new JsonObject();
        objtypeProp.addProperty("type", "string");
        objtypeProp.addProperty("description",
                "ADT object type code: 'PROG/P' (program), 'CLAS/OC' (class), 'INTF/OI' (interface), 'FUGR/F' (function group)");

        JsonObject nameProp = new JsonObject();
        nameProp.addProperty("type", "string");
        nameProp.addProperty("description",
                "Object name (e.g. 'ZTEST_PROGRAM')");

        JsonObject parentNameProp = new JsonObject();
        parentNameProp.addProperty("type", "string");
        parentNameProp.addProperty("description",
                "Parent package name (e.g. '$TMP')");

        JsonObject parentPathProp = new JsonObject();
        parentPathProp.addProperty("type", "string");
        parentPathProp.addProperty("description",
                "ADT path of the parent package (e.g. '/sap/bc/adt/packages/%24tmp')");

        JsonObject descProp = new JsonObject();
        descProp.addProperty("type", "string");
        descProp.addProperty("description",
                "Short description of the object");

        JsonObject sourceProp = new JsonObject();
        sourceProp.addProperty("type", "string");
        sourceProp.addProperty("description",
                "The complete ABAP source code to write");

        JsonObject transportProp = new JsonObject();
        transportProp.addProperty("type", "string");
        transportProp.addProperty("description",
                "Optional transport request number");

        JsonObject lockHandleProp = new JsonObject();
        lockHandleProp.addProperty("type", "string");
        lockHandleProp.addProperty("description",
                "Optional lock handle to reuse (skip locking step)");

        JsonObject properties = new JsonObject();
        properties.add("objtype", objtypeProp);
        properties.add("name", nameProp);
        properties.add("parentName", parentNameProp);
        properties.add("parentPath", parentPathProp);
        properties.add("description", descProp);
        properties.add("source", sourceProp);
        properties.add("transport", transportProp);
        properties.add("lockHandle", lockHandleProp);

        JsonArray required = new JsonArray();
        required.add("objtype");
        required.add("name");
        required.add("parentName");
        required.add("parentPath");
        required.add("description");
        required.add("source");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);

        return new ToolDefinition(NAME,
                "Composite tool: search/create an ABAP object, lock it, write source code, and run a syntax check "
                        + "-- all in one call. Returns the object URL, source URL, lock handle, and syntax check results.",
                schema);
    }

    @Override
    public ToolResult execute(JsonObject arguments) throws Exception {
        String objtype = arguments.get("objtype").getAsString();
        String name = arguments.get("name").getAsString();
        String parentName = arguments.get("parentName").getAsString();
        String parentPath = arguments.get("parentPath").getAsString();
        String description = arguments.get("description").getAsString();
        String source = arguments.get("source").getAsString();
        String transport = optString(arguments, "transport");
        String lockHandle = optString(arguments, "lockHandle");

        JsonObject output = new JsonObject();
        boolean created = false;
        String objectUrl = null;
        String sourceUrl = null;

        // ----------------------------------------------------------
        // Step 1: Search for existing object
        // ----------------------------------------------------------
        try {
            String searchPath = "/sap/bc/adt/repository/informationsystem/search"
                    + "?operation=quickSearch"
                    + "&query=" + urlEncode(name)
                    + "&maxResults=5"
                    + "&objectType=" + urlEncode(objtype);

            HttpResponse<String> searchResp = client.get(searchPath, "application/*");
            JsonArray searchResults = AdtXmlParser.parseSearchResults(searchResp.body());

            // Look for an exact name match (case-insensitive)
            for (int i = 0; i < searchResults.size(); i++) {
                JsonObject result = searchResults.get(i).getAsJsonObject();
                String resultName = result.has("name") ? result.get("name").getAsString() : "";
                if (resultName.equalsIgnoreCase(name)) {
                    objectUrl = result.has("uri") ? result.get("uri").getAsString() : null;
                    break;
                }
            }
        } catch (Exception e) {
            // Search may fail if the object doesn't exist yet -- that's fine
        }

        // ----------------------------------------------------------
        // Step 2: Create if not found
        // ----------------------------------------------------------
        if (objectUrl == null || objectUrl.isEmpty()) {
            String creationUrl = TYPE_URL_MAP.get(objtype.toUpperCase());
            if (creationUrl == null) {
                return ToolResult.error(null, "Unsupported object type for creation: " + objtype);
            }

            String xmlBody = buildCreationXml(objtype, name, parentName, description, parentPath);
            String createPath = creationUrl;
            if (transport != null && !transport.isEmpty()) {
                createPath = createPath + "?corrNr=" + urlEncode(transport);
            }

            try {
                HttpResponse<String> createResp = client.post(
                        createPath, xmlBody, "application/*", "application/*");

                // Derive the object URL
                String location = createResp.headers()
                        .firstValue("Location").orElse(null);
                if (location != null && !location.isEmpty()) {
                    objectUrl = location;
                } else {
                    String baseUrl = TYPE_BASE_URL_MAP.get(objtype.toUpperCase());
                    if (baseUrl != null) {
                        objectUrl = baseUrl + name.toLowerCase();
                    }
                }
                created = true;
            } catch (java.io.IOException createEx) {
                // If creation fails because object already exists, derive URL and continue
                String msg = createEx.getMessage() != null ? createEx.getMessage() : "";
                if (msg.contains("already exists") || msg.contains("HTTP 500")) {
                    String baseUrl = TYPE_BASE_URL_MAP.get(objtype.toUpperCase());
                    if (baseUrl != null) {
                        objectUrl = baseUrl + name.toLowerCase();
                    }
                } else {
                    throw createEx;
                }
            }
        }

        if (objectUrl == null || objectUrl.isEmpty()) {
            return ToolResult.error(null,
                    "Could not determine object URL after search/create for: " + name);
        }

        output.addProperty("created", created);
        output.addProperty("objectUrl", objectUrl);

        // ----------------------------------------------------------
        // Step 3: Get object structure to find source URL
        // ----------------------------------------------------------
        try {
            HttpResponse<String> structResp = client.get(objectUrl, "application/*");
            String mainInclude = AdtXmlParser.extractMainInclude(structResp.body());
            if (mainInclude != null && !mainInclude.isEmpty()) {
                // The main include URL may be relative to the object URL
                if (mainInclude.startsWith("/")) {
                    sourceUrl = mainInclude;
                } else {
                    sourceUrl = objectUrl + "/" + mainInclude;
                }
            }
        } catch (Exception e) {
            // Fallback: construct a default source URL
        }

        if (sourceUrl == null || sourceUrl.isEmpty()) {
            sourceUrl = objectUrl + "/source/main";
        }
        output.addProperty("sourceUrl", sourceUrl);

        // ----------------------------------------------------------
        // Step 4: Lock (or reuse existing handle)
        // Lock the SOURCE URL (not the object URL) because the PUT
        // goes to the source URL and SAP validates the lock against
        // the resource being written.
        // ----------------------------------------------------------
        if (lockHandle == null || lockHandle.isEmpty()) {
            String lockPath = sourceUrl + "?_action=LOCK&accessMode=MODIFY";
            HttpResponse<String> lockResp = client.post(lockPath, "",
                    "application/*",
                    "application/*,application/vnd.sap.as+xml;charset=UTF-8;dataname=com.sap.adt.lock.result");
            lockHandle = AdtXmlParser.extractLockHandle(lockResp.body());

            if (lockHandle == null || lockHandle.isEmpty()) {
                return ToolResult.error(null,
                        "Failed to acquire lock on " + sourceUrl + ". Response: " + lockResp.body());
            }
        }
        output.addProperty("lockHandle", lockHandle);

        // ----------------------------------------------------------
        // Step 5: Write source code (lockHandle as query parameter per ADT API spec)
        // ----------------------------------------------------------
        String separator = sourceUrl.contains("?") ? "&" : "?";
        String writePath = sourceUrl + separator + "lockHandle=" + urlEncode(lockHandle);
        if (transport != null && !transport.isEmpty()) {
            writePath = writePath + "&corrNr=" + urlEncode(transport);
        }

        client.put(writePath, source, "text/plain; charset=utf-8");

        // ----------------------------------------------------------
        // Step 6: Syntax check
        // ----------------------------------------------------------
        JsonArray syntaxMessages = new JsonArray();
        boolean hasErrors = false;

        try {
            StringBuilder syntaxXml = new StringBuilder();
            syntaxXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            syntaxXml.append("<chkrun:checkObjectList xmlns:chkrun=\"http://www.sap.com/adt/checkrun\" ");
            syntaxXml.append("xmlns:adtcore=\"http://www.sap.com/adt/core\">");
            syntaxXml.append("<chkrun:checkObject adtcore:uri=\"").append(escapeXml(sourceUrl)).append("\">");
            syntaxXml.append("</chkrun:checkObject>");
            syntaxXml.append("</chkrun:checkObjectList>");

            HttpResponse<String> syntaxResp = client.post(
                    "/sap/bc/adt/abapsource/syntaxcheck",
                    syntaxXml.toString(),
                    "application/*",
                    "application/*");

            syntaxMessages = AdtXmlParser.parseSyntaxCheckResults(syntaxResp.body());

            for (int i = 0; i < syntaxMessages.size(); i++) {
                JsonObject msg = syntaxMessages.get(i).getAsJsonObject();
                String severity = msg.has("severity") ? msg.get("severity").getAsString() : "";
                if (severity.equalsIgnoreCase("error") || severity.equalsIgnoreCase("E")) {
                    hasErrors = true;
                    break;
                }
            }
        } catch (Exception e) {
            JsonObject syntaxErr = new JsonObject();
            syntaxErr.addProperty("severity", "error");
            syntaxErr.addProperty("text", "Syntax check failed: " + e.getMessage());
            syntaxMessages.add(syntaxErr);
            hasErrors = true;
        }

        output.addProperty("hasErrors", hasErrors);
        output.add("syntaxMessages", syntaxMessages);

        return ToolResult.success(null, output.toString());
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    private String buildCreationXml(String objtype, String name, String parentName,
                                     String description, String parentPath) {
        String typeUpper = objtype.toUpperCase();
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");

        if (typeUpper.startsWith("PROG")) {
            xml.append("<program:abapProgram xmlns:program=\"http://www.sap.com/adt/programs/programs\" ");
            xml.append("xmlns:adtcore=\"http://www.sap.com/adt/core\" ");
            xml.append("adtcore:type=\"").append(escapeXml(objtype)).append("\" ");
            xml.append("adtcore:description=\"").append(escapeXml(description)).append("\" ");
            xml.append("adtcore:name=\"").append(escapeXml(name)).append("\">");
            xml.append("<adtcore:packageRef adtcore:name=\"").append(escapeXml(parentName)).append("\" ");
            xml.append("adtcore:uri=\"").append(escapeXml(parentPath)).append("\"/>");
            xml.append("</program:abapProgram>");
        } else if (typeUpper.startsWith("CLAS")) {
            xml.append("<class:abapClass xmlns:class=\"http://www.sap.com/adt/oo/classes\" ");
            xml.append("xmlns:adtcore=\"http://www.sap.com/adt/core\" ");
            xml.append("adtcore:type=\"").append(escapeXml(objtype)).append("\" ");
            xml.append("adtcore:description=\"").append(escapeXml(description)).append("\" ");
            xml.append("adtcore:name=\"").append(escapeXml(name)).append("\">");
            xml.append("<adtcore:packageRef adtcore:name=\"").append(escapeXml(parentName)).append("\" ");
            xml.append("adtcore:uri=\"").append(escapeXml(parentPath)).append("\"/>");
            xml.append("<class:include class:includeType=\"testclasses\" adtcore:type=\"CLAS/OC\"/>");
            xml.append("</class:abapClass>");
        } else if (typeUpper.startsWith("INTF")) {
            xml.append("<intf:abapInterface xmlns:intf=\"http://www.sap.com/adt/oo/interfaces\" ");
            xml.append("xmlns:adtcore=\"http://www.sap.com/adt/core\" ");
            xml.append("adtcore:type=\"").append(escapeXml(objtype)).append("\" ");
            xml.append("adtcore:description=\"").append(escapeXml(description)).append("\" ");
            xml.append("adtcore:name=\"").append(escapeXml(name)).append("\">");
            xml.append("<adtcore:packageRef adtcore:name=\"").append(escapeXml(parentName)).append("\" ");
            xml.append("adtcore:uri=\"").append(escapeXml(parentPath)).append("\"/>");
            xml.append("</intf:abapInterface>");
        } else if (typeUpper.startsWith("FUGR")) {
            xml.append("<group:functionGroup xmlns:group=\"http://www.sap.com/adt/functions/groups\" ");
            xml.append("xmlns:adtcore=\"http://www.sap.com/adt/core\" ");
            xml.append("adtcore:type=\"").append(escapeXml(objtype)).append("\" ");
            xml.append("adtcore:description=\"").append(escapeXml(description)).append("\" ");
            xml.append("adtcore:name=\"").append(escapeXml(name)).append("\">");
            xml.append("<adtcore:packageRef adtcore:name=\"").append(escapeXml(parentName)).append("\" ");
            xml.append("adtcore:uri=\"").append(escapeXml(parentPath)).append("\"/>");
            xml.append("</group:functionGroup>");
        } else {
            xml.append("<adtcore:objectReference xmlns:adtcore=\"http://www.sap.com/adt/core\" ");
            xml.append("adtcore:type=\"").append(escapeXml(objtype)).append("\" ");
            xml.append("adtcore:description=\"").append(escapeXml(description)).append("\" ");
            xml.append("adtcore:name=\"").append(escapeXml(name)).append("\">");
            xml.append("<adtcore:packageRef adtcore:name=\"").append(escapeXml(parentName)).append("\" ");
            xml.append("adtcore:uri=\"").append(escapeXml(parentPath)).append("\"/>");
            xml.append("</adtcore:objectReference>");
        }

        return xml.toString();
    }

    private String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
