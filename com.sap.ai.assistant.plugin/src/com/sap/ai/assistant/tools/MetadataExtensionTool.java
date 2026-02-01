package com.sap.ai.assistant.tools;

import com.google.gson.JsonObject;
import com.sap.ai.assistant.model.ToolDefinition;
import com.sap.ai.assistant.model.ToolResult;
import com.sap.ai.assistant.sap.AdtRestClient;

/**
 * Tool: <b>sap_metadata_extension</b> -- Create or update a CDS
 * metadata extension.
 *
 * <p>Base URL: {@code /sap/bc/adt/ddic/ddlx/sources}</p>
 */
public class MetadataExtensionTool extends AbstractDdicSourceTool {

    public static final String NAME = "sap_metadata_extension";

    public MetadataExtensionTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    protected String getBaseUrl() {
        return "/sap/bc/adt/ddic/ddlx/sources";
    }

    @Override
    protected String getCreateContentType() {
        return "application/vnd.sap.adt.ddic.ddlx.v1+xml";
    }

    @Override
    protected String getCreateAccept() {
        return "application/vnd.sap.adt.ddic.ddlx.v1+xml";
    }

    @Override
    protected String getSearchObjectType() {
        return "DDLX/EX";
    }

    @Override
    protected String buildCreationXml(String name, String description,
                                       String packageName, String packagePath) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<ddlx:ddlxSource xmlns:ddlx=\"http://www.sap.com/adt/ddic/ddlxsources\" "
                + "xmlns:adtcore=\"http://www.sap.com/adt/core\" "
                + "adtcore:name=\"" + escapeXml(name.toUpperCase()) + "\" "
                + "adtcore:type=\"DDLX/EX\" "
                + "adtcore:description=\"" + escapeXml(description) + "\" "
                + "adtcore:language=\"EN\">"
                + "<adtcore:packageRef adtcore:name=\"" + escapeXml(packageName) + "\" "
                + "adtcore:uri=\"" + escapeXml(packagePath) + "\"/>"
                + "</ddlx:ddlxSource>";
    }

    @Override
    public ToolDefinition getDefinition() {
        return DdicTableTool.buildDdicToolDefinition(NAME,
                "Create or update a CDS metadata extension. "
                + "Provide the full metadata extension source code.");
    }

    @Override
    public ToolResult execute(JsonObject arguments) throws Exception {
        return createAndWriteSource(arguments);
    }
}
