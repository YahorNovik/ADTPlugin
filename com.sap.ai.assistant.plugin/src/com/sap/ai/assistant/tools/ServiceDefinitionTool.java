package com.sap.ai.assistant.tools;

import com.google.gson.JsonObject;
import com.sap.ai.assistant.model.ToolDefinition;
import com.sap.ai.assistant.model.ToolResult;
import com.sap.ai.assistant.sap.AdtRestClient;

/**
 * Tool: <b>sap_service_definition</b> -- Create or update a RAP
 * service definition.
 *
 * <p>Base URL: {@code /sap/bc/adt/ddic/srvd/sources}</p>
 */
public class ServiceDefinitionTool extends AbstractDdicSourceTool {

    public static final String NAME = "sap_service_definition";

    public ServiceDefinitionTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    protected String getBaseUrl() {
        return "/sap/bc/adt/ddic/srvd/sources";
    }

    @Override
    protected String getCreateContentType() {
        return "application/vnd.sap.adt.ddic.srvd.v1+xml";
    }

    @Override
    protected String getCreateAccept() {
        return "application/vnd.sap.adt.ddic.srvd.v1+xml";
    }

    @Override
    protected String getSearchObjectType() {
        return "SRVD/SRV";
    }

    @Override
    protected String buildCreationXml(String name, String description,
                                       String packageName, String packagePath) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<srvd:srvdSource xmlns:srvd=\"http://www.sap.com/adt/ddic/srvdsources\" "
                + "xmlns:adtcore=\"http://www.sap.com/adt/core\" "
                + "adtcore:name=\"" + escapeXml(name.toUpperCase()) + "\" "
                + "adtcore:type=\"SRVD/SRV\" "
                + "adtcore:description=\"" + escapeXml(description) + "\" "
                + "adtcore:language=\"EN\" "
                + "srvd:srvdSourceType=\"S\">"
                + "<adtcore:packageRef adtcore:name=\"" + escapeXml(packageName) + "\" "
                + "adtcore:uri=\"" + escapeXml(packagePath) + "\"/>"
                + "</srvd:srvdSource>";
    }

    @Override
    public ToolDefinition getDefinition() {
        return DdicTableTool.buildDdicToolDefinition(NAME,
                "Create or update a RAP service definition. "
                + "Provide the full service definition source code.");
    }

    @Override
    public ToolResult execute(JsonObject arguments) throws Exception {
        return createAndWriteSource(arguments);
    }
}
