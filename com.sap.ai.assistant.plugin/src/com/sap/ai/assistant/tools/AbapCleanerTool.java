package com.sap.ai.assistant.tools;

import java.lang.reflect.Method;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.ai.assistant.model.ToolDefinition;
import com.sap.ai.assistant.model.ToolResult;

/**
 * A tool that invokes ABAP Cleaner to format/clean ABAP source code.
 * <p>
 * This tool requires the ABAP Cleaner Eclipse plugin to be installed.
 * If ABAP Cleaner is not available, the tool will return an error message.
 * </p>
 * <p>
 * ABAP Cleaner applies 100+ cleanup rules aligned with the Clean ABAP
 * style guide, including formatting, modern syntax, naming, and more.
 * </p>
 */
public class AbapCleanerTool implements SapTool {

    public static final String NAME = "abap_cleaner";

    private static final String PROFILE_DEFAULT = "default";
    private static final String PROFILE_ESSENTIAL = "essential";

    private final ToolDefinition definition;
    private final boolean isAvailable;

    public AbapCleanerTool() {
        this.definition = buildDefinition();
        this.isAvailable = checkAvailability();
    }

    private static final String ABAP_CLEANER_BUNDLE = "com.sap.adt.abapcleaner";

    /**
     * Checks if ABAP Cleaner is installed and available.
     */
    public static boolean isAbapCleanerAvailable() {
        return checkAvailability();
    }

    private static boolean checkAvailability() {
        Bundle cleanerBundle = findAbapCleanerBundle();
        return cleanerBundle != null && cleanerBundle.getState() == Bundle.ACTIVE;
    }

    /**
     * Finds the ABAP Cleaner bundle in the OSGi runtime.
     */
    private static Bundle findAbapCleanerBundle() {
        try {
            Bundle thisBundle = FrameworkUtil.getBundle(AbapCleanerTool.class);
            if (thisBundle == null) return null;

            BundleContext context = thisBundle.getBundleContext();
            if (context == null) return null;

            for (Bundle bundle : context.getBundles()) {
                if (ABAP_CLEANER_BUNDLE.equals(bundle.getSymbolicName())) {
                    return bundle;
                }
            }
        } catch (Exception e) {
            // OSGi not available or error
        }
        return null;
    }

    /**
     * Gets a class from the ABAP Cleaner bundle.
     */
    private static Class<?> getCleanerClass(String className) throws ClassNotFoundException {
        Bundle bundle = findAbapCleanerBundle();
        if (bundle == null) {
            throw new ClassNotFoundException("ABAP Cleaner bundle not found");
        }
        return bundle.loadClass(className);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ToolDefinition getDefinition() {
        return definition;
    }

    @Override
    public ToolResult execute(JsonObject arguments) throws Exception {
        if (!isAvailable) {
            return ToolResult.error(null,
                    "ABAP Cleaner is not installed. Please install it from: "
                    + "https://github.com/SAP/abap-cleaner");
        }

        String sourceCode = null;
        String profileName = PROFILE_DEFAULT;
        String abapRelease = "757"; // Default to recent release

        if (arguments != null) {
            if (arguments.has("sourceCode")) {
                sourceCode = arguments.get("sourceCode").getAsString();
            }
            if (arguments.has("profile")) {
                profileName = arguments.get("profile").getAsString();
            }
            if (arguments.has("abapRelease")) {
                abapRelease = arguments.get("abapRelease").getAsString();
            }
        }

        if (sourceCode == null || sourceCode.trim().isEmpty()) {
            return ToolResult.error(null, "Missing required parameter 'sourceCode'.");
        }

        try {
            String cleanedCode = runCleaner(sourceCode, profileName, abapRelease);
            return ToolResult.success(null, cleanedCode);
        } catch (Exception e) {
            return ToolResult.error(null, "ABAP Cleaner error: " + e.getMessage());
        }
    }

    /**
     * Runs ABAP Cleaner on the provided source code using reflection.
     * This allows the code to compile even when ABAP Cleaner is not available.
     */
    private String runCleaner(String sourceCode, String profileName, String abapRelease)
            throws Exception {
        // Load classes via OSGi bundle lookup
        Class<?> codeClass = getCleanerClass("com.sap.adt.abapcleaner.parser.Code");
        Class<?> parseParamsClass = getCleanerClass("com.sap.adt.abapcleaner.parser.ParseParams");
        Class<?> profileClass = getCleanerClass("com.sap.adt.abapcleaner.rulebase.Profile");
        Class<?> ruleClass = getCleanerClass("com.sap.adt.abapcleaner.rulebase.Rule");
        Class<?> abapClass = getCleanerClass("com.sap.adt.abapcleaner.programbase.ABAP");

        // Get ABAP.NO_RELEASE_RESTRICTION constant
        int noReleaseRestriction = abapClass.getField("NO_RELEASE_RESTRICTION").getInt(null);

        // Create ParseParams: ParseParams.createForWholeCode(sourceName, sourceCode, abapRelease)
        Method createParseParams = parseParamsClass.getMethod(
                "createForWholeCode", String.class, String.class, String.class);
        Object parseParams = createParseParams.invoke(null, "source", sourceCode, abapRelease);

        // Parse code: Code.parse(null, parseParams)
        Class<?> iProgressClass = getCleanerClass("com.sap.adt.abapcleaner.base.IProgress");
        Method parseMethod = codeClass.getMethod("parse", iProgressClass, parseParamsClass);
        Object code = parseMethod.invoke(null, null, parseParams);

        // Create profile: Profile.createDefault() or Profile.createEssential()
        Object profile;
        if (PROFILE_ESSENTIAL.equalsIgnoreCase(profileName)) {
            Method createEssential = profileClass.getMethod("createEssential");
            profile = createEssential.invoke(null);
        } else {
            Method createDefault = profileClass.getMethod("createDefault");
            profile = createDefault.invoke(null);
        }

        // Get all rules from profile
        Method getRules = profileClass.getMethod("getAllRules");
        Object[] rules = (Object[]) getRules.invoke(profile);

        // Apply each active rule
        Method isActive = ruleClass.getMethod("isActive");
        Method executeIfAllowed = ruleClass.getMethod("executeIfAllowedOn", codeClass, int.class);

        for (Object rule : rules) {
            boolean active = (Boolean) isActive.invoke(rule);
            if (active) {
                executeIfAllowed.invoke(rule, code, noReleaseRestriction);
            }
        }

        // Get cleaned source code
        Method toStringMethod = codeClass.getMethod("toString");
        return (String) toStringMethod.invoke(code);
    }

    private static ToolDefinition buildDefinition() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();

        // sourceCode parameter
        JsonObject sourceCodeProp = new JsonObject();
        sourceCodeProp.addProperty("type", "string");
        sourceCodeProp.addProperty("description",
                "The ABAP source code to clean. The entire method/class/report content.");
        properties.add("sourceCode", sourceCodeProp);

        // profile parameter
        JsonObject profileProp = new JsonObject();
        profileProp.addProperty("type", "string");
        JsonArray profileEnum = new JsonArray();
        profileEnum.add("default");
        profileEnum.add("essential");
        profileProp.add("enum", profileEnum);
        profileProp.addProperty("description",
                "The cleanup profile to use. 'default' applies all ~100 rules. "
                + "'essential' applies ~40% of rules from Clean ABAP Styleguide only.");
        properties.add("profile", profileProp);

        // abapRelease parameter
        JsonObject releaseProp = new JsonObject();
        releaseProp.addProperty("type", "string");
        releaseProp.addProperty("description",
                "The ABAP release version (e.g., '757', '756'). "
                + "Affects which modern syntax features can be used.");
        properties.add("abapRelease", releaseProp);

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("sourceCode");
        schema.add("required", required);

        return new ToolDefinition(
                NAME,
                "Clean and format ABAP source code using SAP's ABAP Cleaner. "
                + "Applies 100+ cleanup rules aligned with Clean ABAP Styleguide, including: "
                + "modern syntax (inline declarations, NEW, VALUE), formatting, spacing, "
                + "empty lines, comments, and naming. Returns the cleaned source code. "
                + "Requires ABAP Cleaner Eclipse plugin to be installed.",
                schema);
    }
}
