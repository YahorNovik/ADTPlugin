package com.sap.ai.assistant.tools;

import com.google.gson.JsonObject;
import com.sap.ai.assistant.model.ToolDefinition;
import com.sap.ai.assistant.model.ToolResult;
import com.sap.ai.assistant.sap.AdtRestClient;

/**
 * Tool: <b>sap_ddic_data_element</b> -- Create or update a data
 * element definition.
 *
 * <p>Base URL: {@code /sap/bc/adt/ddic/dataelements}</p>
 */
public class DdicDataElementTool extends AbstractDdicSourceTool {

    public static final String NAME = "sap_ddic_data_element";

    public DdicDataElementTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    protected String getBaseUrl() {
        return "/sap/bc/adt/ddic/dataelements";
    }

    @Override
    protected String getCreateContentType() {
        return "application/vnd.sap.adt.dataelements.v2+xml";
    }

    @Override
    protected String getCreateAccept() {
        return "application/vnd.sap.adt.dataelements.v1+xml, application/vnd.sap.adt.dataelements.v2+xml";
    }

    @Override
    protected String getSearchObjectType() {
        return "DTEL/DE";
    }

    @Override
    protected String buildCreationXml(String name, String description,
                                       String packageName, String packagePath) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<blue:wbobj xmlns:blue=\"http://www.sap.com/wbobj/dictionary/dtel\" "
                + "xmlns:adtcore=\"http://www.sap.com/adt/core\" "
                + "xmlns:dtel=\"http://www.sap.com/adt/dictionary/dataelements\" "
                + "adtcore:name=\"" + escapeXml(name.toUpperCase()) + "\" "
                + "adtcore:type=\"DTEL/DE\" "
                + "adtcore:description=\"" + escapeXml(description) + "\" "
                + "adtcore:language=\"EN\" "
                + "adtcore:masterLanguage=\"EN\">"
                + "<adtcore:packageRef adtcore:name=\"" + escapeXml(packageName) + "\"/>"
                + "<dtel:dataElement>"
                + "<dtel:typeKind>predefinedAbapType</dtel:typeKind>"
                + "<dtel:dataType>CHAR</dtel:dataType>"
                + "<dtel:dataTypeLength>000001</dtel:dataTypeLength>"
                + "<dtel:dataTypeDecimals>000000</dtel:dataTypeDecimals>"
                + "</dtel:dataElement>"
                + "</blue:wbobj>";
    }

    @Override
    public ToolDefinition getDefinition() {
        return DdicTableTool.buildDdicToolDefinition(NAME,
                "Create or update a data element definition.");
    }

    @Override
    public ToolResult execute(JsonObject arguments) throws Exception {
        return createAndWriteSource(arguments);
    }
}
