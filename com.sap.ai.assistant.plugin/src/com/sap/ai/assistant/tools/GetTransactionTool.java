package com.sap.ai.assistant.tools;

import java.net.http.HttpResponse;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.ai.assistant.model.ToolDefinition;
import com.sap.ai.assistant.model.ToolResult;
import com.sap.ai.assistant.sap.AdtRestClient;

/**
 * Tool: <b>sap_get_transaction</b> -- Look up a transaction code
 * and its properties.
 *
 * <p>Endpoint: {@code GET /sap/bc/adt/repository/informationsystem/objectproperties/values
 *    ?uri=/sap/bc/adt/transactions/{tcode}}</p>
 */
public class GetTransactionTool extends AbstractSapTool {

    public static final String NAME = "sap_get_transaction";

    public GetTransactionTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ToolDefinition getDefinition() {
        JsonObject tcodeProp = new JsonObject();
        tcodeProp.addProperty("type", "string");
        tcodeProp.addProperty("description",
                "SAP transaction code (e.g. 'SE38', 'MM01', 'VA01')");

        JsonObject properties = new JsonObject();
        properties.add("transactionCode", tcodeProp);

        JsonArray required = new JsonArray();
        required.add("transactionCode");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);

        return new ToolDefinition(NAME,
                "Look up an SAP transaction code to get its properties (program, screen, etc.).",
                schema);
    }

    @Override
    public ToolResult execute(JsonObject arguments) throws Exception {
        String tcode = arguments.get("transactionCode").getAsString();

        String uri = "/sap/bc/adt/transactions/" + urlEncode(tcode.toLowerCase());
        String path = "/sap/bc/adt/repository/informationsystem/objectproperties/values"
                + "?uri=" + urlEncode(uri);

        HttpResponse<String> resp = client.get(path,
                "application/vnd.sap.adt.objectproperties+xml, application/xml");

        return ToolResult.success(null, parsePropertiesResponse(tcode, resp.body()).toString());
    }

    private JsonObject parsePropertiesResponse(String tcode, String xml) {
        JsonObject result = new JsonObject();
        result.addProperty("transactionCode", tcode.toUpperCase());

        if (xml == null || xml.trim().isEmpty()) {
            return result;
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new java.io.StringReader(xml)));

            // Properties are returned as <property> elements with name/value
            NodeList propNodes = doc.getElementsByTagName("property");
            JsonObject properties = new JsonObject();
            for (int i = 0; i < propNodes.getLength(); i++) {
                Element prop = (Element) propNodes.item(i);
                String name = getAttr(prop, "name", "");
                String value = prop.getTextContent();
                if (value == null) value = getAttr(prop, "value", "");
                if (!name.isEmpty()) {
                    properties.addProperty(name, value != null ? value.trim() : "");
                }
            }
            result.add("properties", properties);

        } catch (Exception e) {
            result.addProperty("parseError", e.getMessage());
        }

        return result;
    }

    private static String getAttr(Element el, String name, String defaultValue) {
        if (el == null) return defaultValue;
        String val = el.getAttribute(name);
        return (val != null && !val.isEmpty()) ? val : defaultValue;
    }
}
