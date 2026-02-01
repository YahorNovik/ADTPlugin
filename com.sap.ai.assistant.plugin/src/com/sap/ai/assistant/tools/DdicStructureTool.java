package com.sap.ai.assistant.tools;

import com.google.gson.JsonObject;
import com.sap.ai.assistant.model.ToolDefinition;
import com.sap.ai.assistant.model.ToolResult;
import com.sap.ai.assistant.sap.AdtRestClient;

/**
 * Tool: <b>sap_ddic_structure</b> -- Create or update a DDIC
 * structure definition using DDL source.
 *
 * <p>Base URL: {@code /sap/bc/adt/ddic/structures}</p>
 */
public class DdicStructureTool extends AbstractDdicSourceTool {

    public static final String NAME = "sap_ddic_structure";

    public DdicStructureTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    protected String getBaseUrl() {
        return "/sap/bc/adt/ddic/structures";
    }

    @Override
    protected String getCreateContentType() {
        return "application/vnd.sap.adt.structures.v2+xml";
    }

    @Override
    protected String getCreateAccept() {
        return "application/vnd.sap.adt.blues.v1+xml, application/vnd.sap.adt.structures.v2+xml";
    }

    @Override
    protected String getSearchObjectType() {
        return "TABL/DS";
    }

    @Override
    protected String buildCreationXml(String name, String description,
                                       String packageName, String packagePath) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<blue:blueSource xmlns:blue=\"http://www.sap.com/wbobj/blue\" "
                + "xmlns:adtcore=\"http://www.sap.com/adt/core\" "
                + "adtcore:name=\"" + escapeXml(name.toUpperCase()) + "\" "
                + "adtcore:type=\"TABL/DS\" "
                + "adtcore:description=\"" + escapeXml(description) + "\" "
                + "adtcore:language=\"EN\">"
                + "<adtcore:packageRef adtcore:name=\"" + escapeXml(packageName) + "\" "
                + "adtcore:uri=\"" + escapeXml(packagePath) + "\"/>"
                + "</blue:blueSource>";
    }

    @Override
    public ToolDefinition getDefinition() {
        return DdicTableTool.buildDdicToolDefinition(NAME,
                "Create or update a DDIC structure definition (DDL source).");
    }

    @Override
    public ToolResult execute(JsonObject arguments) throws Exception {
        return createAndWriteSource(arguments);
    }
}
