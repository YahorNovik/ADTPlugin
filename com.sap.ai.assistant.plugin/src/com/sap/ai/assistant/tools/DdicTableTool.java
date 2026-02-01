package com.sap.ai.assistant.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.ai.assistant.model.ToolDefinition;
import com.sap.ai.assistant.model.ToolResult;
import com.sap.ai.assistant.sap.AdtRestClient;

/**
 * Tool: <b>sap_ddic_table</b> -- Create or update a DDIC table
 * definition using DDL source.
 *
 * <p>Base URL: {@code /sap/bc/adt/ddic/tables}</p>
 */
public class DdicTableTool extends AbstractDdicSourceTool {

    public static final String NAME = "sap_ddic_table";

    public DdicTableTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    protected String getBaseUrl() {
        return "/sap/bc/adt/ddic/tables";
    }

    @Override
    protected String getCreateContentType() {
        return "application/vnd.sap.adt.tables.v2+xml";
    }

    @Override
    protected String getCreateAccept() {
        return "application/vnd.sap.adt.blues.v1+xml, application/vnd.sap.adt.tables.v2+xml";
    }

    @Override
    protected String getSearchObjectType() {
        return "TABL/DT";
    }

    @Override
    protected String buildCreationXml(String name, String description,
                                       String packageName, String packagePath) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<blue:blueSource xmlns:blue=\"http://www.sap.com/wbobj/blue\" "
                + "xmlns:adtcore=\"http://www.sap.com/adt/core\" "
                + "adtcore:name=\"" + escapeXml(name.toUpperCase()) + "\" "
                + "adtcore:type=\"TABL/DT\" "
                + "adtcore:description=\"" + escapeXml(description) + "\" "
                + "adtcore:language=\"EN\">"
                + "<adtcore:packageRef adtcore:name=\"" + escapeXml(packageName) + "\" "
                + "adtcore:uri=\"" + escapeXml(packagePath) + "\"/>"
                + "</blue:blueSource>";
    }

    @Override
    public ToolDefinition getDefinition() {
        return buildDdicToolDefinition(NAME,
                "Create or update a DDIC table definition (DDL source). "
                + "Provide the full DDL source code for the table.");
    }

    @Override
    public ToolResult execute(JsonObject arguments) throws Exception {
        return createAndWriteSource(arguments);
    }

    static ToolDefinition buildDdicToolDefinition(String toolName, String toolDescription) {
        JsonObject nameProp = new JsonObject();
        nameProp.addProperty("type", "string");
        nameProp.addProperty("description", "Object name (e.g. 'ZTEST_TABLE')");

        JsonObject sourceProp = new JsonObject();
        sourceProp.addProperty("type", "string");
        sourceProp.addProperty("description", "Complete DDL source code");

        JsonObject descProp = new JsonObject();
        descProp.addProperty("type", "string");
        descProp.addProperty("description", "Short description of the object");

        JsonObject packageNameProp = new JsonObject();
        packageNameProp.addProperty("type", "string");
        packageNameProp.addProperty("description", "Parent package name (default: '$TMP')");

        JsonObject packagePathProp = new JsonObject();
        packagePathProp.addProperty("type", "string");
        packagePathProp.addProperty("description",
                "ADT path of the parent package (default: '/sap/bc/adt/packages/%24tmp')");

        JsonObject transportProp = new JsonObject();
        transportProp.addProperty("type", "string");
        transportProp.addProperty("description", "Optional transport request number");

        JsonObject properties = new JsonObject();
        properties.add("name", nameProp);
        properties.add("source", sourceProp);
        properties.add("description", descProp);
        properties.add("packageName", packageNameProp);
        properties.add("packagePath", packagePathProp);
        properties.add("transport", transportProp);

        JsonArray required = new JsonArray();
        required.add("name");
        required.add("source");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);

        return new ToolDefinition(toolName, toolDescription, schema);
    }
}
