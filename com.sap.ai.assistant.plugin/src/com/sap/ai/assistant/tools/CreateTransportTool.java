package com.sap.ai.assistant.tools;

import java.net.http.HttpResponse;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.ai.assistant.model.ToolDefinition;
import com.sap.ai.assistant.model.ToolResult;
import com.sap.ai.assistant.sap.AdtRestClient;

/**
 * Tool: <b>sap_create_transport</b> -- Create a new transport
 * request in the SAP system.
 *
 * <p>Endpoint: {@code POST /sap/bc/adt/cts/transportrequests}</p>
 */
public class CreateTransportTool extends AbstractSapTool {

    public static final String NAME = "sap_create_transport";

    public CreateTransportTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ToolDefinition getDefinition() {
        JsonObject descProp = new JsonObject();
        descProp.addProperty("type", "string");
        descProp.addProperty("description",
                "Short description for the transport request");

        JsonObject typeProp = new JsonObject();
        typeProp.addProperty("type", "string");
        typeProp.addProperty("description",
                "Transport type: 'K' for workbench (default), 'T' for customizing");

        JsonObject targetProp = new JsonObject();
        targetProp.addProperty("type", "string");
        targetProp.addProperty("description",
                "Optional target system for the transport");

        JsonObject properties = new JsonObject();
        properties.add("description", descProp);
        properties.add("type", typeProp);
        properties.add("targetSystem", targetProp);

        JsonArray required = new JsonArray();
        required.add("description");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);

        return new ToolDefinition(NAME,
                "Create a new transport request (workbench or customizing).",
                schema);
    }

    @Override
    public ToolResult execute(JsonObject arguments) throws Exception {
        String description = arguments.get("description").getAsString();
        String type = optString(arguments, "type");
        if (type == null || type.isEmpty()) type = "K";
        String targetSystem = optString(arguments, "targetSystem");

        String owner = client.getUsername();
        String xmlBody = buildTransportXml(description, type, targetSystem, owner);

        HttpResponse<String> resp = client.post(
                "/sap/bc/adt/cts/transportrequests",
                xmlBody,
                "text/plain",
                "application/vnd.sap.adt.transportorganizer.v1+xml");

        String transportNumber = parseTransportNumber(resp.body(), resp);

        JsonObject result = new JsonObject();
        result.addProperty("transportNumber", transportNumber);
        result.addProperty("description", description);
        result.addProperty("type", type.equals("K") ? "workbench" : "customizing");

        return ToolResult.success(null, result.toString());
    }

    private String buildTransportXml(String description, String type,
                                      String targetSystem, String owner) {
        String target = (targetSystem != null && !targetSystem.isEmpty())
                ? "/" + escapeXml(targetSystem) + "/"
                : "LOCAL";
        if (owner == null || owner.isEmpty()) owner = "";

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"ASCII\"?>");
        xml.append("<tm:root xmlns:tm=\"http://www.sap.com/cts/adt/tm\" tm:useraction=\"newrequest\">");
        xml.append("<tm:request tm:desc=\"").append(escapeXml(description)).append("\" ");
        xml.append("tm:type=\"").append(escapeXml(type)).append("\" ");
        xml.append("tm:target=\"").append(target).append("\" ");
        xml.append("tm:cts_project=\"\">");
        xml.append("<tm:task tm:owner=\"").append(escapeXml(owner)).append("\"/>");
        xml.append("</tm:request>");
        xml.append("</tm:root>");
        return xml.toString();
    }

    private String parseTransportNumber(String body, HttpResponse<String> resp) {
        // Try Location header first
        String location = resp.headers().firstValue("Location").orElse(null);
        if (location != null && !location.isEmpty()) {
            // Location typically contains the transport number at the end
            int lastSlash = location.lastIndexOf('/');
            if (lastSlash >= 0) {
                return location.substring(lastSlash + 1);
            }
            return location;
        }

        // Try parsing from response body
        if (body != null && !body.trim().isEmpty()) {
            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
                factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(new InputSource(new java.io.StringReader(body)));

                // Look for transport number in various element names
                String[] tags = {"tm:number", "number", "TRKORR", "trkorr"};
                for (String tag : tags) {
                    NodeList nodes = doc.getElementsByTagName(tag);
                    if (nodes.getLength() > 0) {
                        String val = nodes.item(0).getTextContent();
                        if (val != null && !val.trim().isEmpty()) {
                            return val.trim();
                        }
                    }
                }

                // Try the root element's number attribute
                String num = doc.getDocumentElement().getAttribute("tm:number");
                if (num != null && !num.isEmpty()) return num;
                num = doc.getDocumentElement().getAttribute("number");
                if (num != null && !num.isEmpty()) return num;

            } catch (Exception e) {
                // Try regex fallback
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("[A-Z]\\d{2}K\\d{6}")
                        .matcher(body);
                if (m.find()) {
                    return m.group();
                }
            }
        }

        return "(unknown — check response)";
    }

    private String escapeXml(String value) {
        if (value == null) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
