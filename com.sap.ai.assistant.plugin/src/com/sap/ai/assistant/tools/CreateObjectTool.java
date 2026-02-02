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
 * Tool: <b>sap_create_object</b> -- Create a new ABAP repository object
 * (program, class, interface, function group, etc.) in the SAP system.
 */
public class CreateObjectTool extends AbstractSapTool {

    public static final String NAME = "sap_create_object";

    /**
     * Mapping from ADT object type codes to their ADT REST creation URLs.
     * Note: FUGR/FF (function module) is handled specially in execute() —
     * it requires a functionGroup parameter and POSTs to
     * /sap/bc/adt/functions/groups/{group}/fmodules.
     */
    private static final Map<String, String> TYPE_URL_MAP = new HashMap<>();

    /**
     * Mapping from ADT object type codes to Content-Type / Accept headers.
     */
    private static final Map<String, String> TYPE_CONTENT_TYPE_MAP = new HashMap<>();

    static {
        TYPE_URL_MAP.put("PROG/P", "/sap/bc/adt/programs/programs");
        TYPE_URL_MAP.put("CLAS/OC", "/sap/bc/adt/oo/classes");
        TYPE_URL_MAP.put("INTF/OI", "/sap/bc/adt/oo/interfaces");
        TYPE_URL_MAP.put("FUGR/F", "/sap/bc/adt/functions/groups");
        // FUGR/FF is NOT in TYPE_URL_MAP — handled dynamically in execute()
        TYPE_URL_MAP.put("DEVC/K", "/sap/bc/adt/packages");
        TYPE_URL_MAP.put("TABL/DT", "/sap/bc/adt/ddic/tables");
        TYPE_URL_MAP.put("DTEL/DE", "/sap/bc/adt/ddic/dataelements");
        TYPE_URL_MAP.put("DOMA/DO", "/sap/bc/adt/ddic/domains");
        TYPE_URL_MAP.put("TTYP/TT", "/sap/bc/adt/ddic/tabletypes");
        TYPE_URL_MAP.put("SHLP/SH", "/sap/bc/adt/ddic/searchhelps");
        TYPE_URL_MAP.put("MSAG/N", "/sap/bc/adt/messageclass");
        TYPE_URL_MAP.put("XSLT/XT", "/sap/bc/adt/transformations/xslt");

        TYPE_CONTENT_TYPE_MAP.put("PROG/P", "application/vnd.sap.adt.programs.programs.v2+xml");
        TYPE_CONTENT_TYPE_MAP.put("CLAS/OC", "application/vnd.sap.adt.oo.classes.v4+xml");
        TYPE_CONTENT_TYPE_MAP.put("INTF/OI", "application/vnd.sap.adt.oo.interfaces.v5+xml");
        TYPE_CONTENT_TYPE_MAP.put("FUGR/F", "application/vnd.sap.adt.functions.groups.v3+xml");
        TYPE_CONTENT_TYPE_MAP.put("FUGR/FF", "application/*");
        TYPE_CONTENT_TYPE_MAP.put("DEVC/K", "application/vnd.sap.adt.packages.v1+xml");
        TYPE_CONTENT_TYPE_MAP.put("TABL/DT", "application/vnd.sap.adt.tables.v2+xml");
        TYPE_CONTENT_TYPE_MAP.put("DTEL/DE", "application/vnd.sap.adt.dataelements.v2+xml");
        TYPE_CONTENT_TYPE_MAP.put("DOMA/DO", "application/vnd.sap.adt.domains.v2+xml");
        TYPE_CONTENT_TYPE_MAP.put("TTYP/TT", "application/vnd.sap.adt.tabletypes.v2+xml");
    }

    public CreateObjectTool(AdtRestClient client) {
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
                "ADT object type code: 'PROG/P' (program), 'CLAS/OC' (class), 'INTF/OI' (interface), "
                        + "'FUGR/F' (function group), 'FUGR/FF' (function module), 'DEVC/K' (package), "
                        + "'TABL/DT' (table), 'DTEL/DE' (data element), 'DOMA/DO' (domain), 'TTYP/TT' (table type)");

        JsonObject nameProp = new JsonObject();
        nameProp.addProperty("type", "string");
        nameProp.addProperty("description",
                "Object name (e.g. 'ZTEST_PROGRAM'). Must follow SAP naming conventions.");

        JsonObject parentNameProp = new JsonObject();
        parentNameProp.addProperty("type", "string");
        parentNameProp.addProperty("description",
                "Parent package name (e.g. '$TMP' for local, 'ZPACKAGE' for transportable)");

        JsonObject descProp = new JsonObject();
        descProp.addProperty("type", "string");
        descProp.addProperty("description",
                "Short description of the object");

        JsonObject parentPathProp = new JsonObject();
        parentPathProp.addProperty("type", "string");
        parentPathProp.addProperty("description",
                "ADT path of the parent package (e.g. '/sap/bc/adt/packages/%24tmp')");

        JsonObject transportProp = new JsonObject();
        transportProp.addProperty("type", "string");
        transportProp.addProperty("description",
                "Optional transport request number (required for non-local packages)");

        JsonObject functionGroupProp = new JsonObject();
        functionGroupProp.addProperty("type", "string");
        functionGroupProp.addProperty("description",
                "Required for FUGR/FF (function module): name of the parent function group "
                        + "(e.g. 'ZCSV_UTILS'). The function module will be created inside this group.");

        JsonObject properties = new JsonObject();
        properties.add("objtype", objtypeProp);
        properties.add("name", nameProp);
        properties.add("parentName", parentNameProp);
        properties.add("description", descProp);
        properties.add("parentPath", parentPathProp);
        properties.add("transport", transportProp);
        properties.add("functionGroup", functionGroupProp);

        JsonArray required = new JsonArray();
        required.add("objtype");
        required.add("name");
        required.add("parentName");
        required.add("description");
        required.add("parentPath");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);

        return new ToolDefinition(NAME,
                "Create a new ABAP object (program, class, interface, function module, etc.). "
                        + "For function modules (FUGR/FF), the 'functionGroup' parameter is required.",
                schema);
    }

    @Override
    public ToolResult execute(JsonObject arguments) throws Exception {
        String objtype = arguments.get("objtype").getAsString();
        String name = arguments.get("name").getAsString();
        String parentName = arguments.get("parentName").getAsString();
        String description = arguments.get("description").getAsString();
        String parentPath = arguments.get("parentPath").getAsString();
        String transport = optString(arguments, "transport");
        String functionGroup = optString(arguments, "functionGroup");

        String typeUpper = objtype.toUpperCase();

        // Function modules require a functionGroup parameter
        if ("FUGR/FF".equals(typeUpper)) {
            if (functionGroup == null || functionGroup.isEmpty()) {
                return ToolResult.error(null,
                        "Function modules (FUGR/FF) require the 'functionGroup' parameter "
                                + "specifying the parent function group name (e.g. 'ZCSV_UTILS').");
            }
            return createFunctionModule(name, functionGroup, description, transport);
        }

        // Resolve the creation URL for non-FM types
        String creationUrl = TYPE_URL_MAP.get(typeUpper);
        if (creationUrl == null) {
            return ToolResult.error(null,
                    "Unsupported object type: " + objtype
                            + ". Supported types: " + TYPE_URL_MAP.keySet()
                            + " (also FUGR/FF with functionGroup param)");
        }

        // Resolve Content-Type (use type-specific or fallback)
        String contentType = TYPE_CONTENT_TYPE_MAP.getOrDefault(typeUpper, "application/xml");

        // Build the creation XML body
        String xmlBody = buildCreationXml(objtype, name, parentName, description, parentPath, transport);

        // Add transport header if needed
        String path = creationUrl;
        if (transport != null && !transport.isEmpty()) {
            path = path + "?corrNr=" + urlEncode(transport);
        }

        try {
            HttpResponse<String> response = client.post(
                    path,
                    xmlBody,
                    contentType,
                    contentType + ", application/xml");

            JsonObject output = new JsonObject();
            output.addProperty("status", "created");
            output.addProperty("name", name);
            output.addProperty("type", objtype);
            output.addProperty("statusCode", response.statusCode());

            // Try to extract the object URL from the Location header
            String location = response.headers()
                    .firstValue("Location")
                    .orElse(null);
            if (location != null && !location.isEmpty()) {
                output.addProperty("objectUrl", location);
            } else {
                output.addProperty("objectUrl", creationUrl + "/" + name.toLowerCase());
            }

            return ToolResult.success(null, output.toString());

        } catch (java.io.IOException createEx) {
            // If the object already exists, return a helpful message instead of an error
            String msg = createEx.getMessage() != null ? createEx.getMessage() : "";
            if (msg.contains("already exists")) {
                JsonObject output = new JsonObject();
                output.addProperty("status", "already_exists");
                output.addProperty("name", name);
                output.addProperty("type", objtype);
                output.addProperty("objectUrl", creationUrl + "/" + name.toLowerCase());
                output.addProperty("message",
                        "Object '" + name + "' already exists. Use sap_write_and_check to modify it.");
                return ToolResult.success(null, output.toString());
            }
            throw createEx;
        }
    }

    /**
     * Create a function module inside an existing function group.
     * POSTs to /sap/bc/adt/functions/groups/{group}/fmodules with
     * fmodule:abapFunctionModule XML body.
     */
    private ToolResult createFunctionModule(String name, String functionGroup,
                                             String description, String transport) throws Exception {
        String groupLower = functionGroup.toLowerCase();
        String creationUrl = "/sap/bc/adt/functions/groups/" + urlEncode(groupLower) + "/fmodules";

        // Limit description to 60 chars
        if (description != null && description.length() > 60) {
            description = description.substring(0, 60);
        }

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<fmodule:abapFunctionModule ");
        xml.append("xmlns:fmodule=\"http://www.sap.com/adt/functions/fmodules\" ");
        xml.append("xmlns:adtcore=\"http://www.sap.com/adt/core\" ");
        xml.append("adtcore:description=\"").append(escapeXml(description)).append("\" ");
        xml.append("adtcore:name=\"").append(escapeXml(name)).append("\" ");
        xml.append("adtcore:type=\"FUGR/FF\">");
        xml.append("<adtcore:containerRef adtcore:name=\"").append(escapeXml(functionGroup.toUpperCase())).append("\" ");
        xml.append("adtcore:type=\"FUGR/F\" ");
        xml.append("adtcore:uri=\"/sap/bc/adt/functions/groups/").append(escapeXml(groupLower)).append("\"/>");
        xml.append("</fmodule:abapFunctionModule>");

        String path = creationUrl;
        if (transport != null && !transport.isEmpty()) {
            path = path + "?corrNr=" + urlEncode(transport);
        }

        String objectUrl = "/sap/bc/adt/functions/groups/" + groupLower
                + "/fmodules/" + name.toLowerCase();

        try {
            HttpResponse<String> response = client.post(
                    path, xml.toString(), "application/*", "application/*");

            JsonObject output = new JsonObject();
            output.addProperty("status", "created");
            output.addProperty("name", name);
            output.addProperty("type", "FUGR/FF");
            output.addProperty("functionGroup", functionGroup.toUpperCase());
            output.addProperty("statusCode", response.statusCode());

            String location = response.headers()
                    .firstValue("Location").orElse(null);
            if (location != null && !location.isEmpty()) {
                output.addProperty("objectUrl", location);
            } else {
                output.addProperty("objectUrl", objectUrl);
            }
            output.addProperty("sourceUrl", objectUrl + "/source/main");

            return ToolResult.success(null, output.toString());

        } catch (java.io.IOException createEx) {
            String msg = createEx.getMessage() != null ? createEx.getMessage() : "";
            if (msg.contains("already exists")) {
                JsonObject output = new JsonObject();
                output.addProperty("status", "already_exists");
                output.addProperty("name", name);
                output.addProperty("type", "FUGR/FF");
                output.addProperty("functionGroup", functionGroup.toUpperCase());
                output.addProperty("objectUrl", objectUrl);
                output.addProperty("sourceUrl", objectUrl + "/source/main");
                output.addProperty("message",
                        "Function module '" + name + "' already exists in group '"
                                + functionGroup + "'. Use sap_set_source to modify it.");
                return ToolResult.success(null, output.toString());
            }
            throw createEx;
        }
    }

    private String buildCreationXml(String objtype, String name, String parentName,
                                     String description, String parentPath, String transport) {
        String typeUpper = objtype.toUpperCase();

        // Limit description to 60 chars (SAP ADT constraint)
        if (description != null && description.length() > 60) {
            description = description.substring(0, 60);
        }

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");

        if (typeUpper.startsWith("PROG")) {
            xml.append("<program:abapProgram xmlns:program=\"http://www.sap.com/adt/programs/programs\" ");
            xml.append("xmlns:adtcore=\"http://www.sap.com/adt/core\" ");
            xml.append("adtcore:description=\"").append(escapeXml(description)).append("\" ");
            xml.append("adtcore:language=\"EN\" ");
            xml.append("adtcore:name=\"").append(escapeXml(name)).append("\" ");
            xml.append("adtcore:type=\"PROG/P\" ");
            xml.append("adtcore:masterLanguage=\"EN\">");
            xml.append("<adtcore:packageRef adtcore:name=\"").append(escapeXml(parentName)).append("\"/>");
            xml.append("</program:abapProgram>");

        } else if (typeUpper.startsWith("CLAS")) {
            xml.append("<class:abapClass xmlns:class=\"http://www.sap.com/adt/oo/classes\" ");
            xml.append("xmlns:adtcore=\"http://www.sap.com/adt/core\" ");
            xml.append("adtcore:description=\"").append(escapeXml(description)).append("\" ");
            xml.append("adtcore:language=\"EN\" ");
            xml.append("adtcore:name=\"").append(escapeXml(name)).append("\" ");
            xml.append("adtcore:type=\"CLAS/OC\" ");
            xml.append("adtcore:masterLanguage=\"EN\" ");
            xml.append("class:final=\"true\" ");
            xml.append("class:visibility=\"public\">");
            xml.append("<adtcore:packageRef adtcore:name=\"").append(escapeXml(parentName)).append("\"/>");
            xml.append("<class:include adtcore:name=\"CLAS/OC\" adtcore:type=\"CLAS/OC\" class:includeType=\"testclasses\"/>");
            xml.append("</class:abapClass>");

        } else if (typeUpper.startsWith("INTF")) {
            xml.append("<intf:abapInterface xmlns:intf=\"http://www.sap.com/adt/oo/interfaces\" ");
            xml.append("xmlns:adtcore=\"http://www.sap.com/adt/core\" ");
            xml.append("adtcore:description=\"").append(escapeXml(description)).append("\" ");
            xml.append("adtcore:language=\"EN\" ");
            xml.append("adtcore:name=\"").append(escapeXml(name)).append("\" ");
            xml.append("adtcore:type=\"INTF/OI\" ");
            xml.append("adtcore:masterLanguage=\"EN\">");
            xml.append("<adtcore:packageRef adtcore:name=\"").append(escapeXml(parentName)).append("\"/>");
            xml.append("</intf:abapInterface>");

        } else if ("FUGR/F".equals(typeUpper)) {
            xml.append("<group:abapFunctionGroup xmlns:group=\"http://www.sap.com/adt/functions/groups\" ");
            xml.append("xmlns:adtcore=\"http://www.sap.com/adt/core\" ");
            xml.append("adtcore:description=\"").append(escapeXml(description)).append("\" ");
            xml.append("adtcore:language=\"EN\" ");
            xml.append("adtcore:name=\"").append(escapeXml(name)).append("\" ");
            xml.append("adtcore:type=\"FUGR/F\" ");
            xml.append("adtcore:masterLanguage=\"EN\">");
            xml.append("<adtcore:packageRef adtcore:name=\"").append(escapeXml(parentName)).append("\"/>");
            xml.append("</group:abapFunctionGroup>");

        } else {
            // Generic creation XML for other types (DDIC, packages, etc.)
            xml.append("<adtcore:objectReference xmlns:adtcore=\"http://www.sap.com/adt/core\" ");
            xml.append("adtcore:type=\"").append(escapeXml(objtype)).append("\" ");
            xml.append("adtcore:description=\"").append(escapeXml(description)).append("\" ");
            xml.append("adtcore:language=\"EN\" ");
            xml.append("adtcore:name=\"").append(escapeXml(name)).append("\" ");
            xml.append("adtcore:masterLanguage=\"EN\">");
            xml.append("<adtcore:packageRef adtcore:name=\"").append(escapeXml(parentName)).append("\"/>");
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
