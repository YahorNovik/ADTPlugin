package com.sap.ai.assistant.tools;

import com.google.gson.JsonObject;
import com.sap.ai.assistant.model.ToolDefinition;
import com.sap.ai.assistant.model.ToolResult;
import com.sap.ai.assistant.sap.AdtRestClient;

/**
 * Tool: <b>sap_behavior_definition</b> -- Create or update a RAP
 * behavior definition.
 *
 * <p>Base URL: {@code /sap/bc/adt/bo/behaviordefinitions}</p>
 * <p>Note: Behavior definition names are lowercased in URLs.</p>
 */
public class BehaviorDefinitionTool extends AbstractDdicSourceTool {

    public static final String NAME = "sap_behavior_definition";

    public BehaviorDefinitionTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    protected String getBaseUrl() {
        return "/sap/bc/adt/bo/behaviordefinitions";
    }

    @Override
    protected String getCreateContentType() {
        return "application/vnd.sap.adt.blues.v1+xml";
    }

    @Override
    protected String getCreateAccept() {
        return "application/vnd.sap.adt.blues.v1+xml";
    }

    @Override
    protected String getSearchObjectType() {
        return "BDEF/BDO";
    }

    @Override
    protected String buildCreationXml(String name, String description,
                                       String packageName, String packagePath) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<blue:blueSource xmlns:blue=\"http://www.sap.com/wbobj/blue\" "
                + "xmlns:adtcore=\"http://www.sap.com/adt/core\" "
                + "adtcore:name=\"" + escapeXml(name.toUpperCase()) + "\" "
                + "adtcore:type=\"BDEF/BDO\" "
                + "adtcore:description=\"" + escapeXml(description) + "\" "
                + "adtcore:language=\"EN\">"
                + "<adtcore:packageRef adtcore:name=\"" + escapeXml(packageName) + "\" "
                + "adtcore:uri=\"" + escapeXml(packagePath) + "\"/>"
                + "</blue:blueSource>";
    }

    @Override
    public ToolDefinition getDefinition() {
        return DdicTableTool.buildDdicToolDefinition(NAME,
                "Create or update a RAP behavior definition. "
                + "Provide the full behavior definition source code.");
    }

    @Override
    public ToolResult execute(JsonObject arguments) throws Exception {
        return createAndWriteSource(arguments);
    }
}
