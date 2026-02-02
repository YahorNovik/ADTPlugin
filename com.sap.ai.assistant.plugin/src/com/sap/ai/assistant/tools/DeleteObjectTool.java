package com.sap.ai.assistant.tools;

import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sap.ai.assistant.model.ToolDefinition;
import com.sap.ai.assistant.model.ToolResult;
import com.sap.ai.assistant.sap.AdtRestClient;

/**
 * Tool: <b>sap_delete_object</b> -- Delete one or more ABAP repository
 * objects from the SAP system.
 *
 * <p>Uses a two-step process:
 * <ol>
 *   <li>{@code POST /sap/bc/adt/deletion/check} — validates that the
 *       objects can be deleted</li>
 *   <li>{@code POST /sap/bc/adt/deletion/delete} — performs the actual
 *       deletion</li>
 * </ol>
 * Supports mass deletion of multiple objects in a single request.
 * </p>
 */
public class DeleteObjectTool extends AbstractSapTool {

    public static final String NAME = "sap_delete_object";

    private static final String CHECK_URL = "/sap/bc/adt/deletion/check";
    private static final String DELETE_URL = "/sap/bc/adt/deletion/delete";

    private static final String CHECK_CONTENT_TYPE =
            "application/vnd.sap.adt.deletion.check.request.v1+xml";
    private static final String CHECK_ACCEPT =
            "application/vnd.sap.adt.deletion.check.response.v1+xml";

    private static final String DELETE_CONTENT_TYPE =
            "application/vnd.sap.adt.deletion.request.v1+xml";
    private static final String DELETE_ACCEPT =
            "application/vnd.sap.adt.deletion.response.v1+xml";

    /**
     * Maps ADT object type codes to their base URL paths.
     */
    private static final Map<String, String> TYPE_URI_MAP = new HashMap<>();

    static {
        TYPE_URI_MAP.put("PROG/P", "/sap/bc/adt/programs/programs/");
        TYPE_URI_MAP.put("PROG", "/sap/bc/adt/programs/programs/");
        TYPE_URI_MAP.put("CLAS/OC", "/sap/bc/adt/oo/classes/");
        TYPE_URI_MAP.put("CLAS", "/sap/bc/adt/oo/classes/");
        TYPE_URI_MAP.put("INTF/OI", "/sap/bc/adt/oo/interfaces/");
        TYPE_URI_MAP.put("INTF", "/sap/bc/adt/oo/interfaces/");
        TYPE_URI_MAP.put("FUGR/F", "/sap/bc/adt/functions/groups/");
        TYPE_URI_MAP.put("FUGR", "/sap/bc/adt/functions/groups/");
        TYPE_URI_MAP.put("TABL/DT", "/sap/bc/adt/ddic/tables/");
        TYPE_URI_MAP.put("TABL", "/sap/bc/adt/ddic/tables/");
        TYPE_URI_MAP.put("TABL/DS", "/sap/bc/adt/ddic/structures/");
        TYPE_URI_MAP.put("STRU", "/sap/bc/adt/ddic/structures/");
        TYPE_URI_MAP.put("DDLS/DF", "/sap/bc/adt/ddic/ddl/sources/");
        TYPE_URI_MAP.put("DDLS", "/sap/bc/adt/ddic/ddl/sources/");
        TYPE_URI_MAP.put("DTEL/DE", "/sap/bc/adt/ddic/dataelements/");
        TYPE_URI_MAP.put("DTEL", "/sap/bc/adt/ddic/dataelements/");
        TYPE_URI_MAP.put("DOMA/DD", "/sap/bc/adt/ddic/domains/");
        TYPE_URI_MAP.put("DOMA", "/sap/bc/adt/ddic/domains/");
        TYPE_URI_MAP.put("TTYP/TT", "/sap/bc/adt/ddic/tabletypes/");
        TYPE_URI_MAP.put("TTYP", "/sap/bc/adt/ddic/tabletypes/");
        TYPE_URI_MAP.put("VIEW/DV", "/sap/bc/adt/ddic/views/");
        TYPE_URI_MAP.put("VIEW", "/sap/bc/adt/ddic/views/");
        TYPE_URI_MAP.put("SRVD/SRV", "/sap/bc/adt/ddic/srvd/sources/");
        TYPE_URI_MAP.put("BDEF/BDO", "/sap/bc/adt/bo/behaviordefinitions/");
        TYPE_URI_MAP.put("DDLX/EX", "/sap/bc/adt/ddic/ddlx/sources/");
        TYPE_URI_MAP.put("DEVC/K", "/sap/bc/adt/packages/");
        TYPE_URI_MAP.put("MSAG/N", "/sap/bc/adt/messageclass/");
        TYPE_URI_MAP.put("XSLT/XT", "/sap/bc/adt/transformations/xslt/");
        TYPE_URI_MAP.put("SHLP/SH", "/sap/bc/adt/ddic/searchhelps/");
    }

    public DeleteObjectTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ToolDefinition getDefinition() {
        // -- objects array item schema --
        JsonObject typeProp = new JsonObject();
        typeProp.addProperty("type", "string");
        typeProp.addProperty("description",
                "ADT object type code: 'PROG/P' (program), 'CLAS/OC' (class), 'INTF/OI' (interface), "
                + "'FUGR/F' (function group), 'TABL/DT' (table), 'DDLS/DF' (CDS view), "
                + "'DTEL/DE' (data element), 'DOMA/DD' (domain), 'DEVC/K' (package), etc.");

        JsonObject nameProp = new JsonObject();
        nameProp.addProperty("type", "string");
        nameProp.addProperty("description", "Object name (e.g. 'ZTEST_PROGRAM')");

        JsonObject itemProps = new JsonObject();
        itemProps.add("type", typeProp);
        itemProps.add("name", nameProp);

        JsonArray itemRequired = new JsonArray();
        itemRequired.add("type");
        itemRequired.add("name");

        JsonObject itemSchema = new JsonObject();
        itemSchema.addProperty("type", "object");
        itemSchema.add("properties", itemProps);
        itemSchema.add("required", itemRequired);

        // -- objects array --
        JsonObject objectsProp = new JsonObject();
        objectsProp.addProperty("type", "array");
        objectsProp.addProperty("description",
                "Array of objects to delete. Each object has 'type' and 'name'. "
                + "Supports mass deletion of multiple objects in one call.");
        objectsProp.add("items", itemSchema);

        // -- transport --
        JsonObject transportProp = new JsonObject();
        transportProp.addProperty("type", "string");
        transportProp.addProperty("description",
                "Optional transport request number (required for non-local objects)");

        // -- skipCheck --
        JsonObject skipCheckProp = new JsonObject();
        skipCheckProp.addProperty("type", "boolean");
        skipCheckProp.addProperty("description",
                "Skip the deletion check step (default: false). "
                + "Set to true only if you already verified the objects can be deleted.");

        JsonObject properties = new JsonObject();
        properties.add("objects", objectsProp);
        properties.add("transport", transportProp);
        properties.add("skipCheck", skipCheckProp);

        JsonArray required = new JsonArray();
        required.add("objects");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);

        return new ToolDefinition(NAME,
                "Delete one or more ABAP objects from the SAP system. "
                + "Supports mass deletion. Runs a deletion check first, then deletes.",
                schema);
    }

    @Override
    public ToolResult execute(JsonObject arguments) throws Exception {
        JsonArray objects = arguments.getAsJsonArray("objects");
        String transport = optString(arguments, "transport");
        boolean skipCheck = arguments.has("skipCheck")
                && arguments.get("skipCheck").getAsBoolean();

        if (objects == null || objects.size() == 0) {
            return ToolResult.error(null, "No objects specified for deletion.");
        }

        // Build object URI list
        StringBuilder uriElements = new StringBuilder();
        JsonArray objectSummary = new JsonArray();

        for (JsonElement elem : objects) {
            JsonObject obj = elem.getAsJsonObject();
            String type = obj.get("type").getAsString().toUpperCase();
            String name = obj.get("name").getAsString();
            String uri = buildObjectUri(name, type);

            if (uri == null) {
                return ToolResult.error(null,
                        "Unsupported object type '" + type + "' for deletion. "
                        + "Supported types: " + TYPE_URI_MAP.keySet());
            }

            uriElements.append("  <del:object adtcore:uri=\"")
                    .append(escapeXml(uri)).append("\">");
            if (transport != null && !transport.isEmpty()) {
                uriElements.append("<del:transportNumber>")
                        .append(escapeXml(transport))
                        .append("</del:transportNumber>");
            } else {
                uriElements.append("<del:transportNumber/>");
            }
            uriElements.append("</del:object>\n");

            JsonObject summary = new JsonObject();
            summary.addProperty("name", name);
            summary.addProperty("type", type);
            summary.addProperty("uri", uri);
            objectSummary.add(summary);
        }

        // Step 1: Check deletion (unless skipped)
        if (!skipCheck) {
            String checkXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<del:checkRequest xmlns:del=\"http://www.sap.com/adt/deletion\" "
                    + "xmlns:adtcore=\"http://www.sap.com/adt/core\">\n"
                    + buildCheckElements(objects)
                    + "</del:checkRequest>";

            HttpResponse<String> checkResp = client.post(
                    CHECK_URL, checkXml, CHECK_CONTENT_TYPE, CHECK_ACCEPT);

            if (checkResp.statusCode() >= 400) {
                JsonObject result = new JsonObject();
                result.addProperty("status", "check_failed");
                result.addProperty("statusCode", checkResp.statusCode());
                result.addProperty("message",
                        "Deletion check failed. The objects may have dependencies or "
                        + "you may not have authorization to delete them.");
                result.add("objects", objectSummary);
                if (checkResp.body() != null && !checkResp.body().isEmpty()) {
                    result.addProperty("detail", checkResp.body().length() > 500
                            ? checkResp.body().substring(0, 500) : checkResp.body());
                }
                return ToolResult.error(null, result.toString());
            }
        }

        // Step 2: Delete
        String deleteXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<del:deletionRequest xmlns:del=\"http://www.sap.com/adt/deletion\" "
                + "xmlns:adtcore=\"http://www.sap.com/adt/core\">\n"
                + uriElements.toString()
                + "</del:deletionRequest>";

        HttpResponse<String> deleteResp = client.post(
                DELETE_URL, deleteXml, DELETE_CONTENT_TYPE, DELETE_ACCEPT);

        JsonObject result = new JsonObject();
        result.addProperty("statusCode", deleteResp.statusCode());
        result.add("objects", objectSummary);

        if (deleteResp.statusCode() >= 200 && deleteResp.statusCode() < 300) {
            result.addProperty("status", "deleted");
            result.addProperty("message",
                    objects.size() == 1
                            ? "Object deleted successfully."
                            : objects.size() + " objects deleted successfully.");
        } else {
            result.addProperty("status", "failed");
            result.addProperty("message", "Deletion failed with HTTP " + deleteResp.statusCode());
            if (deleteResp.body() != null && !deleteResp.body().isEmpty()) {
                result.addProperty("detail", deleteResp.body().length() > 500
                        ? deleteResp.body().substring(0, 500) : deleteResp.body());
            }
            return ToolResult.error(null, result.toString());
        }

        return ToolResult.success(null, result.toString());
    }

    /**
     * Build the check XML elements (without transport numbers).
     */
    private String buildCheckElements(JsonArray objects) {
        StringBuilder sb = new StringBuilder();
        for (JsonElement elem : objects) {
            JsonObject obj = elem.getAsJsonObject();
            String type = obj.get("type").getAsString().toUpperCase();
            String name = obj.get("name").getAsString();
            String uri = buildObjectUri(name, type);
            sb.append("  <del:object adtcore:uri=\"")
              .append(escapeXml(uri)).append("\"/>\n");
        }
        return sb.toString();
    }

    /**
     * Build the ADT object URI from a name and type code.
     */
    private String buildObjectUri(String name, String type) {
        String basePath = TYPE_URI_MAP.get(type);
        if (basePath == null) {
            return null;
        }
        return basePath + name.toLowerCase();
    }

    private String escapeXml(String value) {
        if (value == null) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
