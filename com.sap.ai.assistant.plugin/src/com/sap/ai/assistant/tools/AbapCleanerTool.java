package com.sap.ai.assistant.tools;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

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
                    "ABAP Cleaner is not installed. To enable automated code cleanup:\n\n"
                    + "1. In Eclipse: Help → Install New Software\n"
                    + "2. Add update site: https://sap.github.io/abap-cleaner/updatesite\n"
                    + "3. Select 'ABAP Cleaner' and complete installation\n"
                    + "4. Restart Eclipse\n\n"
                    + "More info: https://github.com/SAP/abap-cleaner");
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

        // Get or create profile
        Object profile = getOrCreateProfile(profileClass, profileName);

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

    /**
     * Gets or creates a profile by name.
     * For "default" and "essential", uses built-in factory methods.
     * For custom names, tries to load from ABAP Cleaner's profile directory.
     */
    private Object getOrCreateProfile(Class<?> profileClass, String profileName) throws Exception {
        // Handle built-in profiles
        if (PROFILE_DEFAULT.equalsIgnoreCase(profileName)) {
            Method createDefault = profileClass.getMethod("createDefault");
            return createDefault.invoke(null);
        }
        if (PROFILE_ESSENTIAL.equalsIgnoreCase(profileName)) {
            Method createEssential = profileClass.getMethod("createEssential");
            return createEssential.invoke(null);
        }

        // Try to load custom profile from ABAP Cleaner's profile directory
        Object customProfile = loadProfileByName(profileClass, profileName);
        if (customProfile != null) {
            return customProfile;
        }

        // Fall back to default if custom profile not found
        Method createDefault = profileClass.getMethod("createDefault");
        return createDefault.invoke(null);
    }

    /**
     * Loads a profile by name from ABAP Cleaner's profile directories.
     * Returns null if the profile is not found.
     */
    @SuppressWarnings("unchecked")
    private Object loadProfileByName(Class<?> profileClass, String profileName) {
        try {
            // Get Persistency class and instance
            Class<?> persistencyClass = getCleanerClass("com.sap.adt.abapcleaner.base.Persistency");
            Method getInstance = persistencyClass.getMethod("get");
            Object persistency = getInstance.invoke(null);

            // Get profile directory from Persistency
            Class<?> fileTypeClass = getCleanerClass("com.sap.adt.abapcleaner.base.FileType");
            Object profileTextType = fileTypeClass.getField("PROFILE_TEXT").get(null);
            Method getDirectoryPath = persistencyClass.getMethod("getDirectoryPath", fileTypeClass);
            String profileDir = (String) getDirectoryPath.invoke(persistency, profileTextType);

            // Get read-only profile directories (team profiles)
            Class<?> profileDirClass = getCleanerClass("com.sap.adt.abapcleaner.rulebase.ProfileDir");
            Method getReadOnlyDirs = persistencyClass.getMethod("getReadOnlyProfileDirs");
            Object readOnlyDirs = getReadOnlyDirs.invoke(persistency);

            // Load all profiles
            StringBuilder errors = new StringBuilder();
            Method loadProfiles = profileClass.getMethod("loadProfiles",
                    String.class, ArrayList.class, StringBuilder.class);
            List<?> profiles = (List<?>) loadProfiles.invoke(null, profileDir, readOnlyDirs, errors);

            // Find profile by name (case-insensitive)
            Method getName = profileClass.getMethod("getName");
            for (Object profile : profiles) {
                String name = (String) getName.invoke(profile);
                if (profileName.equalsIgnoreCase(name)) {
                    return profile;
                }
            }
        } catch (Exception e) {
            // Could not load profiles, will fall back to default
        }
        return null;
    }

    /**
     * Lists all available profile names from ABAP Cleaner.
     * Useful for providing suggestions to the user.
     */
    @SuppressWarnings("unchecked")
    public static List<String> getAvailableProfiles() {
        List<String> names = new ArrayList<>();
        names.add(PROFILE_DEFAULT);
        names.add(PROFILE_ESSENTIAL);

        try {
            Class<?> profileClass = getCleanerClass("com.sap.adt.abapcleaner.rulebase.Profile");
            Class<?> persistencyClass = getCleanerClass("com.sap.adt.abapcleaner.base.Persistency");

            Method getInstance = persistencyClass.getMethod("get");
            Object persistency = getInstance.invoke(null);

            Class<?> fileTypeClass = getCleanerClass("com.sap.adt.abapcleaner.base.FileType");
            Object profileTextType = fileTypeClass.getField("PROFILE_TEXT").get(null);
            Method getDirectoryPath = persistencyClass.getMethod("getDirectoryPath", fileTypeClass);
            String profileDir = (String) getDirectoryPath.invoke(persistency, profileTextType);

            Method getReadOnlyDirs = persistencyClass.getMethod("getReadOnlyProfileDirs");
            Object readOnlyDirs = getReadOnlyDirs.invoke(persistency);

            StringBuilder errors = new StringBuilder();
            Method loadProfiles = profileClass.getMethod("loadProfiles",
                    String.class, ArrayList.class, StringBuilder.class);
            List<?> profiles = (List<?>) loadProfiles.invoke(null, profileDir, readOnlyDirs, errors);

            Method getName = profileClass.getMethod("getName");
            for (Object profile : profiles) {
                String name = (String) getName.invoke(profile);
                if (!names.contains(name)) {
                    names.add(name);
                }
            }
        } catch (Exception e) {
            // Could not load profiles
        }
        return names;
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
        profileProp.addProperty("description",
                "The cleanup profile name. Built-in: 'default' (all ~100 rules), "
                + "'essential' (~40% core rules). Can also use custom profile names "
                + "configured in ABAP Cleaner (e.g., 'team A: myprofile').");
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
