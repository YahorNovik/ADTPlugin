package com.sap.ai.assistant.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.ai.assistant.model.ToolDefinition;
import com.sap.ai.assistant.model.ToolResult;
import com.sap.ai.assistant.sap.AdtRestClient;

/**
 * Tool: <b>sap_usage_references</b> -- Find all usages (where-used list)
 * of an ABAP element across the entire system.
 */
public class UsageReferencesTool extends AbstractSapTool {

    public static final String NAME = "sap_usage_references";

    public UsageReferencesTool(AdtRestClient client) {
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
                "The ADT source URL of the element whose usages to find (e.g. '/sap/bc/adt/oo/classes/zcl_test')");

        JsonObject lineProp = new JsonObject();
        lineProp.addProperty("type", "integer");
        lineProp.addProperty("description",
                "Optional 1-based line number of the element");

        JsonObject colProp = new JsonObject();
        colProp.addProperty("type", "integer");
        colProp.addProperty("description",
                "Optional 0-based column of the element");

        JsonObject properties = new JsonObject();
        properties.add("url", urlProp);
        properties.add("line", lineProp);
        properties.add("column", colProp);

        JsonArray required = new JsonArray();
        required.add("url");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);

        return new ToolDefinition(NAME,
                "Find all usages (where-used) of an ABAP element.",
                schema);
    }

    @Override
    public ToolResult execute(JsonObject arguments) throws Exception {
        String url = arguments.get("url").getAsString();
        String line = optString(arguments, "line");
        String column = optString(arguments, "column");

        StringBuilder path = new StringBuilder();
        path.append("/sap/bc/adt/repository/informationsystem/usagereferences");
        path.append("?uri=").append(urlEncode(url));

        if (line != null && !line.isEmpty()) {
            path.append("&line=").append(urlEncode(line));
        }
        if (column != null && !column.isEmpty()) {
            path.append("&column=").append(urlEncode(column));
        }

        HttpResponse<String> response = client.post(
                path.toString(),
                "",
                "application/*",
                "application/*");

        // Return the raw response; usage reference results vary by
        // SAP release and the LLM can interpret the XML
        JsonObject output = new JsonObject();
        output.addProperty("statusCode", response.statusCode());
        output.addProperty("response", response.body());
        return ToolResult.success(null, output.toString());
    }
}
