package com.sap.ai.assistant.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.ai.assistant.model.ToolDefinition;
import com.sap.ai.assistant.model.ToolResult;
import com.sap.ai.assistant.sap.AdtRestClient;
import com.sap.ai.assistant.sap.AdtXmlParser;

/**
 * Tool: <b>sap_type_info</b> -- Retrieve DDIC type information
 * (data elements, domains, table types) by name.
 *
 * <p>Uses a fallback chain of ADT endpoints to find the type.</p>
 */
public class TypeInfoTool extends AbstractSapTool {

    public static final String NAME = "sap_type_info";

    private static final String[][] ENDPOINTS = {
        { "/sap/bc/adt/ddic/dataelements/", "application/vnd.sap.adt.dataelements.v2+xml, application/xml", "DTEL" },
        { "/sap/bc/adt/ddic/domains/",      "application/vnd.sap.adt.domains.v2+xml, application/xml",      "DOMA" },
        { "/sap/bc/adt/ddic/tabletypes/",    "application/vnd.sap.adt.tabletypes.v2+xml, application/xml",   "TTYP" },
    };

    public TypeInfoTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ToolDefinition getDefinition() {
        JsonObject nameProp = new JsonObject();
        nameProp.addProperty("type", "string");
        nameProp.addProperty("description",
                "ABAP type name (data element, domain, or table type)");

        JsonObject typeCategoryProp = new JsonObject();
        typeCategoryProp.addProperty("type", "string");
        typeCategoryProp.addProperty("description",
                "Optional hint: 'DTEL' (data element), 'DOMA' (domain), 'TTYP' (table type) "
                + "to skip the fallback chain and query directly");

        JsonObject properties = new JsonObject();
        properties.add("name", nameProp);
        properties.add("typeCategory", typeCategoryProp);

        JsonArray required = new JsonArray();
        required.add("name");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);

        return new ToolDefinition(NAME,
                "Get DDIC type information for a data element, domain, or table type.",
                schema);
    }

    @Override
    public ToolResult execute(JsonObject arguments) throws Exception {
        String name = arguments.get("name").getAsString().toUpperCase();
        String typeCategory = optString(arguments, "typeCategory");

        // If a specific category is provided, try that endpoint first
        if (typeCategory != null && !typeCategory.isEmpty()) {
            String cat = typeCategory.toUpperCase();
            for (String[] ep : ENDPOINTS) {
                if (ep[2].equals(cat)) {
                    String result = tryEndpoint(ep[0] + name.toLowerCase(), ep[1]);
                    if (result != null) {
                        return ToolResult.success(null, result);
                    }
                }
            }
        }

        // Fallback chain: try each endpoint in order
        for (String[] ep : ENDPOINTS) {
            String result = tryEndpoint(ep[0] + name.toLowerCase(), ep[1]);
            if (result != null) {
                return ToolResult.success(null, result);
            }
        }

        // Last resort: object properties
        try {
            String propsPath = "/sap/bc/adt/repository/informationsystem/objectproperties/values"
                    + "?uri=" + urlEncode("/sap/bc/adt/ddic/dataelements/" + name.toLowerCase());
            HttpResponse<String> resp = client.get(propsPath,
                    "application/vnd.sap.adt.objectproperties+xml, application/xml");
            if (resp.body() != null && !resp.body().isEmpty()) {
                JsonObject result = AdtXmlParser.parseDdicContent(resp.body());
                result.addProperty("name", name);
                return ToolResult.success(null, result.toString());
            }
        } catch (Exception e) {
            // Ignore
        }

        return ToolResult.error(null, "Type not found: " + name);
    }

    private String tryEndpoint(String path, String accept) {
        try {
            HttpResponse<String> resp = client.get(path, accept);
            if (resp.body() != null && !resp.body().isEmpty()) {
                JsonObject result = AdtXmlParser.parseDdicContent(resp.body());
                if (result.size() > 0) {
                    return result.toString();
                }
            }
        } catch (Exception e) {
            // Try next endpoint
        }
        return null;
    }
}
