package com.sap.ai.assistant.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.ai.assistant.model.ToolDefinition;
import com.sap.ai.assistant.model.ToolResult;
import com.sap.ai.assistant.sap.AdtRestClient;
import com.sap.ai.assistant.sap.AdtXmlParser;

/**
 * Tool: <b>sap_node_contents</b> -- Browse the ABAP repository tree
 * (packages, object lists) via the ADT node structure API.
 */
public class NodeContentsTool extends AbstractSapTool {

    public static final String NAME = "sap_node_contents";

    public NodeContentsTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ToolDefinition getDefinition() {
        JsonObject parentTypeProp = new JsonObject();
        parentTypeProp.addProperty("type", "string");
        parentTypeProp.addProperty("description",
                "The type of the parent node (e.g. 'DEVC/K' for packages, 'PROG/P' for programs)");

        JsonObject parentNameProp = new JsonObject();
        parentNameProp.addProperty("type", "string");
        parentNameProp.addProperty("description",
                "Optional parent object name (e.g. '$TMP' for the local package)");

        JsonObject userNameProp = new JsonObject();
        userNameProp.addProperty("type", "string");
        userNameProp.addProperty("description",
                "Optional SAP user name to filter objects by owner");

        JsonObject properties = new JsonObject();
        properties.add("parent_type", parentTypeProp);
        properties.add("parent_name", parentNameProp);
        properties.add("user_name", userNameProp);

        JsonArray required = new JsonArray();
        required.add("parent_type");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);

        return new ToolDefinition(NAME,
                "Browse the ABAP repository tree to list objects inside a package or container. "
                        + "Returns child nodes with name, type, URI, description, and whether they are expandable.",
                schema);
    }

    @Override
    public ToolResult execute(JsonObject arguments) throws Exception {
        String parentType = arguments.get("parent_type").getAsString();
        String parentName = optString(arguments, "parent_name");
        String userName = optString(arguments, "user_name");

        // ADT API: parameters go as query params on the URL, not as form body
        StringBuilder path = new StringBuilder();
        path.append("/sap/bc/adt/repository/nodestructure");
        path.append("?parent_type=").append(urlEncode(parentType));
        if (parentName != null && !parentName.isEmpty()) {
            path.append("&parent_name=").append(urlEncode(parentName));
        }
        if (userName != null && !userName.isEmpty()) {
            path.append("&user_name=").append(urlEncode(userName));
        }

        HttpResponse<String> response = client.post(
                path.toString(),
                "",
                "application/*",
                "application/*");

        JsonObject result = AdtXmlParser.parseNodeContents(response.body());
        return ToolResult.success(null, result.toString());
    }
}
