package com.sap.ai.assistant.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.ai.assistant.model.ToolDefinition;
import com.sap.ai.assistant.model.ToolResult;

/**
 * Tool: <b>guidelines_update</b> -- Update guideline files for ABAP object types.
 * <p>
 * Allows the agent to save naming conventions, best practices, lessons learned,
 * and other notes for future reference. Files are stored in
 * {@code ~/.sap-ai-assistant/guidelines/}.
 * </p>
 */
public class UpdateGuidelinesTool implements SapTool {

    public static final String NAME = "guidelines_update";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ToolDefinition getDefinition() {
        JsonObject typeProp = AdtUrlResolver.buildTypeProperty();
        typeProp.addProperty("description",
                "Object type to update guidelines for. Maps to a file: "
                + "CLAS→class.md, INTF→interface.md, PROG→report.md, "
                + "FUGR→functionmodule.md, TABL→table.md, etc.");

        JsonArray typeEnum = AdtUrlResolver.buildTypeEnumArray();
        typeEnum.add("FUGR");
        typeProp.add("enum", typeEnum);

        JsonObject fileProp = new JsonObject();
        fileProp.addProperty("type", "string");
        fileProp.addProperty("description",
                "Direct filename (e.g. 'class.md', 'general.md'). "
                + "Use for custom guideline files.");

        JsonObject contentProp = new JsonObject();
        contentProp.addProperty("type", "string");
        contentProp.addProperty("description",
                "The guideline content to write (Markdown format).");

        JsonObject appendProp = new JsonObject();
        appendProp.addProperty("type", "boolean");
        appendProp.addProperty("description",
                "If true (default), appends to existing file with a timestamp separator. "
                + "If false, replaces the entire file content.");

        JsonObject properties = new JsonObject();
        properties.add("objectType", typeProp);
        properties.add("fileName", fileProp);
        properties.add("content", contentProp);
        properties.add("append", appendProp);

        JsonArray required = new JsonArray();
        required.add("content");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);

        return new ToolDefinition(NAME,
                "Update guideline files for ABAP object types. Save naming conventions, "
                + "best practices, and notes for future reference. "
                + "Provide objectType or fileName, plus the content to write.",
                schema);
    }

    @Override
    public ToolResult execute(JsonObject arguments) throws Exception {
        if (arguments == null || !arguments.has("content")
                || arguments.get("content").isJsonNull()) {
            return ToolResult.error(null, "Missing required parameter 'content'.");
        }

        String objectType = null;
        String fileName = null;
        if (arguments.has("objectType") && !arguments.get("objectType").isJsonNull()) {
            objectType = arguments.get("objectType").getAsString();
        }
        if (arguments.has("fileName") && !arguments.get("fileName").isJsonNull()) {
            fileName = arguments.get("fileName").getAsString();
        }

        String content = arguments.get("content").getAsString();

        boolean append = true;
        if (arguments.has("append") && !arguments.get("append").isJsonNull()) {
            append = arguments.get("append").getAsBoolean();
        }

        // Resolve filename
        String resolved = GuidelinesManager.resolveFileName(objectType, fileName);
        if (resolved == null) {
            if (objectType != null && !objectType.isEmpty()) {
                return ToolResult.error(null,
                        "Unknown object type '" + objectType + "'. Use fileName for custom files.");
            }
            return ToolResult.error(null,
                    "Provide either objectType or fileName to identify the guideline file.");
        }

        if (append) {
            GuidelinesManager.appendToFile(resolved, content);
        } else {
            GuidelinesManager.replaceFile(resolved, content);
        }

        JsonObject output = new JsonObject();
        output.addProperty("status", "success");
        output.addProperty("fileName", resolved);
        output.addProperty("action", append ? "appended" : "replaced");
        output.addProperty("directory", GuidelinesManager.getGuidelinesDir().toString());
        return ToolResult.success(null, output.toString());
    }
}
