package com.sap.ai.assistant.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.ai.assistant.model.ToolDefinition;
import com.sap.ai.assistant.model.ToolResult;
import com.sap.ai.assistant.sap.AdtRestClient;
import com.sap.ai.assistant.sap.AdtXmlParser;

/**
 * Tool: <b>sap_sql_query</b> -- Execute an ABAP SQL query via the
 * ADT data preview freestyle endpoint.
 *
 * <p>Endpoint: {@code POST /sap/bc/adt/datapreview/freestyle?rowNumber={maxRows}}</p>
 */
public class SqlQueryTool extends AbstractSapTool {

    public static final String NAME = "sap_sql_query";

    public SqlQueryTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ToolDefinition getDefinition() {
        JsonObject queryProp = new JsonObject();
        queryProp.addProperty("type", "string");
        queryProp.addProperty("description",
                "The ABAP SQL query to execute (e.g. 'SELECT * FROM mara UP TO 10 ROWS')");

        JsonObject maxRowsProp = new JsonObject();
        maxRowsProp.addProperty("type", "integer");
        maxRowsProp.addProperty("description",
                "Maximum number of rows to return (default: 100)");

        JsonObject properties = new JsonObject();
        properties.add("query", queryProp);
        properties.add("maxRows", maxRowsProp);

        JsonArray required = new JsonArray();
        required.add("query");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);

        return new ToolDefinition(NAME,
                "Execute an ABAP SQL SELECT query against SAP database tables and return "
                + "actual DATA ROWS. Use this to read real data from tables (e.g. "
                + "'SELECT matnr, mtart, matkl FROM mara UP TO 10 ROWS'). "
                + "This returns row values, NOT table structure or field definitions. "
                + "For table structure/fields, use sap_get_source or sap_type_info instead.",
                schema);
    }

    @Override
    public ToolResult execute(JsonObject arguments) throws Exception {
        String query = arguments.get("query").getAsString();
        int maxRows = optInt(arguments, "maxRows", 100);

        String path = "/sap/bc/adt/datapreview/freestyle?rowNumber=" + maxRows;
        HttpResponse<String> resp = client.post(path, query,
                "text/plain; charset=utf-8",
                "application/vnd.sap.adt.datapreview.table.v1+xml");

        JsonObject result = AdtXmlParser.parseDataPreview(resp.body());
        return ToolResult.success(null, result.toString());
    }
}
