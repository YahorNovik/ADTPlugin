package com.sap.ai.assistant.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.ai.assistant.model.ToolDefinition;
import com.sap.ai.assistant.model.ToolResult;
import com.sap.ai.assistant.sap.AdtRestClient;

/**
 * Tool: <b>sap_find_definition</b> -- Navigate to the definition of an
 * ABAP element (class, method, variable, etc.) from a given source
 * position, similar to "Go to Definition" in an IDE.
 */
public class FindDefinitionTool extends AbstractSapTool {

    public static final String NAME = "sap_find_definition";

    public FindDefinitionTool(AdtRestClient client) {
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
                "The ADT source URL containing the element (e.g. '/sap/bc/adt/programs/programs/ztest/source/main')");

        JsonObject sourceProp = new JsonObject();
        sourceProp.addProperty("type", "string");
        sourceProp.addProperty("description",
                "The ABAP source code of the file (used for context)");

        JsonObject lineProp = new JsonObject();
        lineProp.addProperty("type", "integer");
        lineProp.addProperty("description",
                "1-based line number where the element is located");

        JsonObject startColProp = new JsonObject();
        startColProp.addProperty("type", "integer");
        startColProp.addProperty("description",
                "0-based start column of the element");

        JsonObject endColProp = new JsonObject();
        endColProp.addProperty("type", "integer");
        endColProp.addProperty("description",
                "0-based end column of the element");

        JsonObject properties = new JsonObject();
        properties.add("objectType", AdtUrlResolver.buildTypeProperty());
        properties.add("objectName", AdtUrlResolver.buildNameProperty());
        properties.add("url", urlProp);
        properties.add("source", sourceProp);
        properties.add("line", lineProp);
        properties.add("startColumn", startColProp);
        properties.add("endColumn", endColProp);

        JsonArray required = new JsonArray();
        required.add("source");
        required.add("line");
        required.add("startColumn");
        required.add("endColumn");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);

        return new ToolDefinition(NAME,
                "Find definition of an ABAP element at a source position. "
                + "Provide objectType + objectName, or url for the source file.",
                schema);
    }

    @Override
    public ToolResult execute(JsonObject arguments) throws Exception {
        String url = resolveSourceUrlArg(arguments, "url");
        if (url == null) {
            return ToolResult.error(null, "Provide either objectType + objectName, or url.");
        }
        String source = arguments.get("source").getAsString();
        int line = arguments.get("line").getAsInt();
        int startColumn = arguments.get("startColumn").getAsInt();
        int endColumn = arguments.get("endColumn").getAsInt();

        // ADT navigation API: POST source as text/plain with position as query params
        String navPath = "/sap/bc/adt/navigation/target"
                + "?uri=" + urlEncode(url)
                + "&line=" + line
                + "&column=" + startColumn
                + "&endColumn=" + endColumn;

        HttpResponse<String> response = client.post(
                navPath,
                source,
                "text/plain",
                "application/*");

        // Return the raw response; the navigation result contains the
        // target URI, line number, and column of the definition
        JsonObject output = new JsonObject();
        output.addProperty("statusCode", response.statusCode());
        output.addProperty("response", response.body());
        return ToolResult.success(null, output.toString());
    }
}
