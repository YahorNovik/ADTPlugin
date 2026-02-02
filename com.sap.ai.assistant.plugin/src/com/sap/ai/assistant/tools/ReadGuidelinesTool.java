package com.sap.ai.assistant.tools;

import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.ai.assistant.model.ToolDefinition;
import com.sap.ai.assistant.model.ToolResult;

/**
 * Tool: <b>guidelines_read</b> -- Read guideline files for ABAP object types.
 * <p>
 * Guideline files contain naming conventions, best practices, and notes
 * accumulated over time. They are stored in {@code ~/.sap-ai-assistant/guidelines/}.
 * </p>
 * <p>
 * When called with no parameters, lists all available guideline files.
 * When called with an objectType or fileName, returns the file content.
 * </p>
 */
public class ReadGuidelinesTool implements SapTool {

    public static final String NAME = "guidelines_read";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ToolDefinition getDefinition() {
        JsonObject typeProp = AdtUrlResolver.buildTypeProperty();
        typeProp.addProperty("description",
                "Object type to read guidelines for. Maps to a file: "
                + "CLAS→class.md, INTF→interface.md, PROG→report.md, "
                + "FUGR→functionmodule.md, TABL→table.md, STRU→structure.md, "
                + "DDLS/CDS→cdsview.md, DTEL→dataelement.md, DOMA→domain.md, etc.");

        // Add FUGR to the enum since it's relevant for guidelines but not in ADT URL resolver
        JsonArray typeEnum = AdtUrlResolver.buildTypeEnumArray();
        typeEnum.add("FUGR");
        typeProp.add("enum", typeEnum);

        JsonObject fileProp = new JsonObject();
        fileProp.addProperty("type", "string");
        fileProp.addProperty("description",
                "Direct filename to read (e.g. 'class.md', 'general.md'). "
                + "Use this for custom guideline files not tied to a specific object type.");

        JsonObject properties = new JsonObject();
        properties.add("objectType", typeProp);
        properties.add("fileName", fileProp);

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);

        return new ToolDefinition(NAME,
                "Read guideline files for ABAP object types. Contains naming conventions, "
                + "best practices, and notes. Call with no params to list all files, "
                + "or with objectType/fileName to read a specific file.",
                schema);
    }

    @Override
    public ToolResult execute(JsonObject arguments) throws Exception {
        String objectType = null;
        String fileName = null;

        if (arguments != null) {
            if (arguments.has("objectType") && !arguments.get("objectType").isJsonNull()) {
                objectType = arguments.get("objectType").getAsString();
            }
            if (arguments.has("fileName") && !arguments.get("fileName").isJsonNull()) {
                fileName = arguments.get("fileName").getAsString();
            }
        }

        // If no params, list available files
        if ((objectType == null || objectType.isEmpty())
                && (fileName == null || fileName.isEmpty())) {
            return listFiles();
        }

        // Resolve filename
        String resolved = GuidelinesManager.resolveFileName(objectType, fileName);
        if (resolved == null) {
            return ToolResult.error(null,
                    "Unknown object type '" + objectType + "'. Use fileName for custom files.");
        }

        String content = GuidelinesManager.readFile(resolved);
        if (content == null || content.isEmpty()) {
            JsonObject output = new JsonObject();
            output.addProperty("fileName", resolved);
            output.addProperty("exists", false);
            output.addProperty("message",
                    "No guidelines file '" + resolved + "' exists yet. "
                    + "Use guidelines_update to create one.");
            return ToolResult.success(null, output.toString());
        }

        JsonObject output = new JsonObject();
        output.addProperty("fileName", resolved);
        output.addProperty("exists", true);
        output.addProperty("content", content);
        return ToolResult.success(null, output.toString());
    }

    private ToolResult listFiles() throws Exception {
        List<String> files = GuidelinesManager.listFiles();

        JsonObject output = new JsonObject();
        output.addProperty("directory", GuidelinesManager.getGuidelinesDir().toString());

        if (files.isEmpty()) {
            output.addProperty("message",
                    "No guideline files exist yet. Use guidelines_update to create them.");
            output.add("files", new JsonArray());
        } else {
            JsonArray arr = new JsonArray();
            for (String f : files) {
                arr.add(f);
            }
            output.add("files", arr);
        }

        return ToolResult.success(null, output.toString());
    }
}
