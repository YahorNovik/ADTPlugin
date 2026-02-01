package com.sap.ai.assistant.tools;

import com.google.gson.JsonObject;
import com.sap.ai.assistant.model.ToolDefinition;
import com.sap.ai.assistant.model.ToolResult;
import com.sap.ai.assistant.sap.AdtRestClient;

/**
 * Tool: <b>sap_ddic_cds_view</b> -- Create or update a CDS view
 * definition.
 *
 * <p>Base URL: {@code /sap/bc/adt/ddic/ddl/sources}</p>
 */
public class DdicCdsViewTool extends AbstractDdicSourceTool {

    public static final String NAME = "sap_ddic_cds_view";

    public DdicCdsViewTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    protected String getBaseUrl() {
        return "/sap/bc/adt/ddic/ddl/sources";
    }

    @Override
    protected String getCreateContentType() {
        return "application/vnd.sap.adt.ddlSource+xml";
    }

    @Override
    protected String getCreateAccept() {
        return "application/vnd.sap.adt.ddlSource.v2+xml, application/vnd.sap.adt.ddlSource+xml";
    }

    @Override
    protected String getSearchObjectType() {
        return "DDLS/DF";
    }

    @Override
    protected String buildCreationXml(String name, String description,
                                       String packageName, String packagePath) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<ddlSource:ddlSource xmlns:ddlSource=\"http://www.sap.com/adt/ddic/ddlsources\" "
                + "xmlns:adtcore=\"http://www.sap.com/adt/core\" "
                + "adtcore:name=\"" + escapeXml(name.toUpperCase()) + "\" "
                + "adtcore:type=\"DDLS/DF\" "
                + "adtcore:description=\"" + escapeXml(description) + "\" "
                + "adtcore:language=\"EN\">"
                + "<adtcore:packageRef adtcore:name=\"" + escapeXml(packageName) + "\" "
                + "adtcore:uri=\"" + escapeXml(packagePath) + "\"/>"
                + "</ddlSource:ddlSource>";
    }

    @Override
    public ToolDefinition getDefinition() {
        return DdicTableTool.buildDdicToolDefinition(NAME,
                "Create or update a CDS view definition. "
                + "Provide the full CDS DDL source code.");
    }

    @Override
    public ToolResult execute(JsonObject arguments) throws Exception {
        return createAndWriteSource(arguments);
    }
}
