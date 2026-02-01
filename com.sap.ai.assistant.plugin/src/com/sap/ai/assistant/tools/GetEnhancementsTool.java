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
 * Tool: <b>sap_get_enhancements</b> -- Retrieve enhancement spot
 * details including BAdI definitions.
 *
 * <p>Endpoint: {@code GET /sap/bc/adt/enhancements/enhsxsb/{spotName}}</p>
 */
public class GetEnhancementsTool extends AbstractSapTool {

    public static final String NAME = "sap_get_enhancements";

    public GetEnhancementsTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ToolDefinition getDefinition() {
        JsonObject spotNameProp = new JsonObject();
        spotNameProp.addProperty("type", "string");
        spotNameProp.addProperty("description",
                "Enhancement spot name (e.g. 'BADI_MATERIAL_CHECK')");

        JsonObject properties = new JsonObject();
        properties.add("spotName", spotNameProp);

        JsonArray required = new JsonArray();
        required.add("spotName");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);

        return new ToolDefinition(NAME,
                "Get enhancement spot details and BAdI definitions.",
                schema);
    }

    @Override
    public ToolResult execute(JsonObject arguments) throws Exception {
        String spotName = arguments.get("spotName").getAsString();

        String path = "/sap/bc/adt/enhancements/enhsxsb/" + urlEncode(spotName);
        HttpResponse<String> resp = client.get(path,
                "application/vnd.sap.adt.enhancements.v1+xml, application/xml");

        return ToolResult.success(null, parseEnhancementResponse(resp.body()).toString());
    }

    private JsonObject parseEnhancementResponse(String xml) {
        JsonObject result = new JsonObject();
        JsonArray badis = new JsonArray();
        result.add("badis", badis);

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

            Element root = doc.getDocumentElement();
            result.addProperty("name", getAttr(root, "adtcore:name", getAttr(root, "name", "")));
            result.addProperty("description", getAttr(root, "adtcore:description",
                    getAttr(root, "description", "")));
            result.addProperty("type", getAttr(root, "adtcore:type", getAttr(root, "type", "")));
            result.addProperty("packageName", getAttr(root, "adtcore:packageName",
                    getAttr(root, "packageName", "")));

            // Find BAdI definitions
            NodeList badiNodes = doc.getElementsByTagName("badi");
            if (badiNodes.getLength() == 0) {
                badiNodes = doc.getElementsByTagName("enhsxsb:badi");
            }
            for (int i = 0; i < badiNodes.getLength(); i++) {
                Element badiEl = (Element) badiNodes.item(i);
                JsonObject badi = new JsonObject();
                badi.addProperty("name", getAttr(badiEl, "adtcore:name",
                        getAttr(badiEl, "name", "")));
                badi.addProperty("description", getAttr(badiEl, "adtcore:description",
                        getAttr(badiEl, "description", "")));

                // Interface
                NodeList intfNodes = badiEl.getElementsByTagName("interface");
                if (intfNodes.getLength() == 0) {
                    intfNodes = badiEl.getElementsByTagName("enhsxsb:interface");
                }
                if (intfNodes.getLength() > 0) {
                    Element intfEl = (Element) intfNodes.item(0);
                    badi.addProperty("interface", getAttr(intfEl, "adtcore:name",
                            getAttr(intfEl, "name", intfEl.getTextContent().trim())));
                }

                badis.add(badi);
            }

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
