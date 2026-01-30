package com.sap.ai.assistant.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.ai.assistant.model.ToolDefinition;
import com.sap.ai.assistant.model.ToolResult;
import com.sap.ai.assistant.sap.AdtRestClient;

/**
 * Tool: <b>sap_transport_info</b> -- Retrieve transport request
 * information for an ABAP repository object.
 */
public class TransportInfoTool extends AbstractSapTool {

    public static final String NAME = "sap_transport_info";

    public TransportInfoTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ToolDefinition getDefinition() {
        JsonObject urlProp = new JsonObject();
        urlProp.addProperty("type", "string");
        urlProp.addProperty("description",
                "The ADT object URL (e.g. '/sap/bc/adt/programs/programs/ztest')");

        JsonObject devClassProp = new JsonObject();
        devClassProp.addProperty("type", "string");
        devClassProp.addProperty("description",
                "Optional development class / package name");

        JsonObject properties = new JsonObject();
        properties.add("objectUrl", urlProp);
        properties.add("devClass", devClassProp);

        JsonArray required = new JsonArray();
        required.add("objectUrl");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);

        return new ToolDefinition(NAME,
                "Get transport request information for an ABAP object. Returns available transport "
                        + "requests and whether the object is local ($TMP) or transportable.",
                schema);
    }

    @Override
    public ToolResult execute(JsonObject arguments) throws Exception {
        String objectUrl = arguments.get("objectUrl").getAsString();
        String devClass = optString(arguments, "devClass");

        StringBuilder path = new StringBuilder();
        path.append("/sap/bc/adt/cts/transportrequests");
        path.append("?uri=").append(urlEncode(objectUrl));

        if (devClass != null && !devClass.isEmpty()) {
            path.append("&devclass=").append(urlEncode(devClass));
        }

        HttpResponse<String> response = client.get(path.toString(), "application/*");

        // Return the raw XML as the transport info structure varies
        // across SAP releases; the LLM can interpret the XML directly
        JsonObject output = new JsonObject();
        output.addProperty("statusCode", response.statusCode());
        output.addProperty("response", response.body());
        return ToolResult.success(null, output.toString());
    }
}
