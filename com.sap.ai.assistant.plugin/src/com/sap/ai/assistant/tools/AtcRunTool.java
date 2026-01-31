package com.sap.ai.assistant.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.ai.assistant.model.ToolDefinition;
import com.sap.ai.assistant.model.ToolResult;
import com.sap.ai.assistant.sap.AdtRestClient;
import com.sap.ai.assistant.sap.AdtXmlParser;

/**
 * Tool: <b>sap_atc_run</b> -- Run the ABAP Test Cockpit (ATC) on an
 * object and return the worklist of findings (code-quality checks,
 * security findings, performance warnings, etc.).
 */
public class AtcRunTool extends AbstractSapTool {

    public static final String NAME = "sap_atc_run";

    public AtcRunTool(AdtRestClient client) {
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
                "The ADT object URL to check (e.g. '/sap/bc/adt/programs/programs/ztest')");

        JsonObject variantProp = new JsonObject();
        variantProp.addProperty("type", "string");
        variantProp.addProperty("description",
                "ATC check variant (default 'DEFAULT')");

        JsonObject maxProp = new JsonObject();
        maxProp.addProperty("type", "integer");
        maxProp.addProperty("description",
                "Maximum number of findings to return");

        JsonObject properties = new JsonObject();
        properties.add("objectUrl", urlProp);
        properties.add("variant", variantProp);
        properties.add("maxResults", maxProp);

        JsonArray required = new JsonArray();
        required.add("objectUrl");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);

        return new ToolDefinition(NAME,
                "Run ATC quality checks. Returns findings with priority and messages.",
                schema);
    }

    @Override
    public ToolResult execute(JsonObject arguments) throws Exception {
        String objectUrl = arguments.get("objectUrl").getAsString();
        String variant = optString(arguments, "variant");
        if (variant == null || variant.isEmpty()) {
            variant = "DEFAULT";
        }
        int maxResults = optInt(arguments, "maxResults", 100);

        // Step 1: Create a worklist for the check variant
        // POST /sap/bc/adt/atc/worklists?checkVariant=DEFAULT returns the worklist ID
        String worklistId;
        try {
            HttpResponse<String> wlResponse = client.post(
                    "/sap/bc/adt/atc/worklists?checkVariant=" + urlEncode(variant),
                    "", "application/xml", "text/plain");
            worklistId = wlResponse.body() != null ? wlResponse.body().trim() : "";
        } catch (Exception e) {
            // Some systems return the worklist ID differently; try a fallback
            worklistId = variant;
        }

        if (worklistId.isEmpty()) {
            worklistId = variant;
        }

        // Step 2: Create the ATC run with correct XML format
        // Uses <objectSets> with <adtcore:objectReferences> per SAP ADT API spec
        String runXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<atc:run maximumVerdicts=\"" + maxResults
                + "\" xmlns:atc=\"http://www.sap.com/adt/atc\">"
                + "<objectSets xmlns:adtcore=\"http://www.sap.com/adt/core\">"
                + "<objectSet kind=\"inclusive\">"
                + "<adtcore:objectReferences>"
                + "<adtcore:objectReference adtcore:uri=\"" + escapeXml(objectUrl) + "\"/>"
                + "</adtcore:objectReferences>"
                + "</objectSet>"
                + "</objectSets>"
                + "</atc:run>";

        HttpResponse<String> runResponse = client.post(
                "/sap/bc/adt/atc/runs?worklistId=" + urlEncode(worklistId),
                runXml,
                "application/xml",
                "application/xml");

        // Extract the worklist ID from the run response (may differ from the one we created)
        String runWorklistId = extractWorklistId(runResponse);
        if (runWorklistId != null && !runWorklistId.isEmpty()) {
            worklistId = runWorklistId;
        }

        // Step 3: Fetch the worklist results
        HttpResponse<String> worklistResponse = client.get(
                "/sap/bc/adt/atc/worklists/" + urlEncode(worklistId),
                "application/atc.worklist.v1+xml");

        JsonObject worklist = AdtXmlParser.parseAtcWorklist(worklistResponse.body());
        worklist.addProperty("worklistId", worklistId);
        return ToolResult.success(null, worklist.toString());
    }

    /**
     * Extract the worklist ID from the ATC run response. The ID may be in
     * a Location header or embedded in the XML body.
     */
    private String extractWorklistId(HttpResponse<String> response) {
        // Try Location header first
        String location = response.headers()
                .firstValue("Location")
                .orElse(null);
        if (location != null && !location.isEmpty()) {
            // Location is typically like /sap/bc/adt/atc/worklists/{id}
            int lastSlash = location.lastIndexOf('/');
            if (lastSlash >= 0 && lastSlash < location.length() - 1) {
                String id = location.substring(lastSlash + 1);
                // Strip query parameters if present
                int qMark = id.indexOf('?');
                if (qMark >= 0) {
                    id = id.substring(0, qMark);
                }
                return id;
            }
        }

        // Try to find worklistId in the response body
        String body = response.body();
        if (body != null) {
            // Look for worklist id attribute or element
            int idx = body.indexOf("worklistId=\"");
            if (idx >= 0) {
                int start = idx + "worklistId=\"".length();
                int end = body.indexOf("\"", start);
                if (end > start) {
                    return body.substring(start, end);
                }
            }

            // Try id attribute
            idx = body.indexOf("id=\"");
            if (idx >= 0) {
                int start = idx + "id=\"".length();
                int end = body.indexOf("\"", start);
                if (end > start) {
                    return body.substring(start, end);
                }
            }
        }

        return null;
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
