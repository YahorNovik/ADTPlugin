package com.sap.ai.assistant.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.ai.assistant.model.ToolDefinition;
import com.sap.ai.assistant.model.ToolResult;
import com.sap.ai.assistant.sap.AdtRestClient;
import com.sap.ai.assistant.sap.AdtXmlParser;

/**
 * Tool: <b>sap_run_unit_test</b> — Run ABAP Unit (AUnit) tests on a
 * repository object and return the test results including pass/fail
 * status per test class and method.
 */
public class RunUnitTestTool extends AbstractSapTool {

    public static final String NAME = "sap_run_unit_test";

    public RunUnitTestTool(AdtRestClient client) {
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
                "The ADT object URL to test (e.g. '/sap/bc/adt/oo/classes/ZCL_MY_CLASS' "
                        + "or '/sap/bc/adt/programs/programs/ZPROGRAM')");

        JsonObject riskProp = new JsonObject();
        riskProp.addProperty("type", "string");
        riskProp.addProperty("description",
                "Comma-separated risk levels to include: harmless, dangerous, critical "
                        + "(default: 'harmless')");

        JsonObject durationProp = new JsonObject();
        durationProp.addProperty("type", "string");
        durationProp.addProperty("description",
                "Comma-separated durations to include: short, medium, long "
                        + "(default: 'short')");

        JsonObject properties = new JsonObject();
        properties.add("objectUrl", urlProp);
        properties.add("riskLevel", riskProp);
        properties.add("duration", durationProp);

        JsonArray required = new JsonArray();
        required.add("objectUrl");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);

        return new ToolDefinition(NAME,
                "Run ABAP Unit (AUnit) tests on a repository object. Returns test results with "
                        + "pass/fail status, execution time, and failure details per test class and method.",
                schema);
    }

    @Override
    public ToolResult execute(JsonObject arguments) throws Exception {
        String objectUrl = arguments.get("objectUrl").getAsString();

        // Parse risk level flags
        String riskLevelStr = optString(arguments, "riskLevel");
        boolean harmless = true, dangerous = false, critical = false;
        if (riskLevelStr != null && !riskLevelStr.isEmpty()) {
            String lower = riskLevelStr.toLowerCase();
            harmless = lower.contains("harmless");
            dangerous = lower.contains("dangerous");
            critical = lower.contains("critical");
            if (!harmless && !dangerous && !critical) {
                harmless = true; // fallback
            }
        }

        // Parse duration flags
        String durationStr = optString(arguments, "duration");
        boolean shortDur = true, mediumDur = false, longDur = false;
        if (durationStr != null && !durationStr.isEmpty()) {
            String lower = durationStr.toLowerCase();
            shortDur = lower.contains("short");
            mediumDur = lower.contains("medium");
            longDur = lower.contains("long");
            if (!shortDur && !mediumDur && !longDur) {
                shortDur = true; // fallback
            }
        }

        // Build XML request body
        String xmlBody = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<aunit:runConfiguration xmlns:aunit=\"http://www.sap.com/adt/aunit\">"
                + "<external>"
                + "<coverage active=\"false\"/>"
                + "</external>"
                + "<options>"
                + "<uriType value=\"semantic\"/>"
                + "<testDeterminationStrategy sameProgram=\"true\" assignedTests=\"false\"/>"
                + "<testRiskLevels harmless=\"" + harmless + "\" dangerous=\"" + dangerous
                + "\" critical=\"" + critical + "\"/>"
                + "<testDurations short=\"" + shortDur + "\" medium=\"" + mediumDur
                + "\" long=\"" + longDur + "\"/>"
                + "<withNavigationUri enabled=\"false\"/>"
                + "</options>"
                + "<adtcore:objectSets xmlns:adtcore=\"http://www.sap.com/adt/core\">"
                + "<objectSet kind=\"inclusive\">"
                + "<adtcore:objectReferences>"
                + "<adtcore:objectReference adtcore:uri=\"" + escapeXml(objectUrl) + "\"/>"
                + "</adtcore:objectReferences>"
                + "</objectSet>"
                + "</adtcore:objectSets>"
                + "</aunit:runConfiguration>";

        HttpResponse<String> response = client.post(
                "/sap/bc/adt/abapunit/testruns",
                xmlBody,
                "application/xml",
                "application/xml");

        if (response.statusCode() >= 400) {
            return ToolResult.error(null,
                    "AUnit run failed (HTTP " + response.statusCode() + "): " + response.body());
        }

        JsonObject result = AdtXmlParser.parseUnitTestResults(response.body());
        return ToolResult.success(null, result.toString());
    }

    private String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
