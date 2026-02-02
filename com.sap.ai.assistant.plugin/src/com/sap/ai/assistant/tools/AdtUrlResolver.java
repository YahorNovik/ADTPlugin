package com.sap.ai.assistant.tools;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Shared utility for resolving ABAP object types and names to ADT REST URLs.
 * <p>
 * Provides two URL flavors:
 * <ul>
 *   <li><b>Object URLs</b>: e.g. {@code /sap/bc/adt/oo/classes/zcl_test} — used by
 *       activate, ATC, unit tests, object structure, usage references</li>
 *   <li><b>Source URLs</b>: e.g. {@code /sap/bc/adt/oo/classes/zcl_test/source/main} — used by
 *       get source, set source, syntax check, lock/unlock, find definition</li>
 * </ul>
 */
public final class AdtUrlResolver {

    private AdtUrlResolver() {}

    /**
     * Maps object type keywords to ADT object URL templates.
     * The placeholder {@code {name}} is replaced with the lowercase object name.
     */
    private static final Map<String, String> OBJECT_URL_TEMPLATES = new LinkedHashMap<>();
    static {
        // OO
        OBJECT_URL_TEMPLATES.put("CLAS", "/sap/bc/adt/oo/classes/{name}");
        OBJECT_URL_TEMPLATES.put("CLASS", "/sap/bc/adt/oo/classes/{name}");
        OBJECT_URL_TEMPLATES.put("INTF", "/sap/bc/adt/oo/interfaces/{name}");
        OBJECT_URL_TEMPLATES.put("INTERFACE", "/sap/bc/adt/oo/interfaces/{name}");
        // Programs
        OBJECT_URL_TEMPLATES.put("PROG", "/sap/bc/adt/programs/programs/{name}");
        OBJECT_URL_TEMPLATES.put("PROGRAM", "/sap/bc/adt/programs/programs/{name}");
        // DDIC
        OBJECT_URL_TEMPLATES.put("TABL", "/sap/bc/adt/ddic/tables/{name}");
        OBJECT_URL_TEMPLATES.put("TABLE", "/sap/bc/adt/ddic/tables/{name}");
        OBJECT_URL_TEMPLATES.put("STRU", "/sap/bc/adt/ddic/structures/{name}");
        OBJECT_URL_TEMPLATES.put("STRUCTURE", "/sap/bc/adt/ddic/structures/{name}");
        OBJECT_URL_TEMPLATES.put("DDLS", "/sap/bc/adt/ddic/ddl/sources/{name}");
        OBJECT_URL_TEMPLATES.put("CDS", "/sap/bc/adt/ddic/ddl/sources/{name}");
        OBJECT_URL_TEMPLATES.put("DTEL", "/sap/bc/adt/ddic/dataelements/{name}");
        OBJECT_URL_TEMPLATES.put("DATAELEMENT", "/sap/bc/adt/ddic/dataelements/{name}");
        OBJECT_URL_TEMPLATES.put("DOMA", "/sap/bc/adt/ddic/domains/{name}");
        OBJECT_URL_TEMPLATES.put("DOMAIN", "/sap/bc/adt/ddic/domains/{name}");
        // Service / metadata
        OBJECT_URL_TEMPLATES.put("SRVD", "/sap/bc/adt/ddic/srvd/sources/{name}");
        OBJECT_URL_TEMPLATES.put("DDLX", "/sap/bc/adt/ddic/ddlx/sources/{name}");
        OBJECT_URL_TEMPLATES.put("BDEF", "/sap/bc/adt/bopf/bdef/sources/{name}");
    }

    /**
     * Resolves an object URL from type + name.
     *
     * @param objectType the ABAP object type (e.g. "CLAS", "PROG")
     * @param objectName the object name (e.g. "ZCL_MY_CLASS")
     * @return the ADT object URL, or {@code null} if type is unknown or args are empty
     */
    public static String resolveObjectUrl(String objectType, String objectName) {
        if (objectType == null || objectType.isEmpty()
                || objectName == null || objectName.isEmpty()) {
            return null;
        }
        String template = OBJECT_URL_TEMPLATES.get(objectType.toUpperCase());
        if (template != null) {
            return template.replace("{name}", objectName.toLowerCase());
        }
        return null;
    }

    /**
     * Resolves a source URL (object URL + {@code /source/main}) from type + name.
     *
     * @param objectType the ABAP object type (e.g. "CLAS", "PROG")
     * @param objectName the object name (e.g. "ZCL_MY_CLASS")
     * @return the ADT source URL, or {@code null} if type is unknown or args are empty
     */
    public static String resolveSourceUrl(String objectType, String objectName) {
        String objectUrl = resolveObjectUrl(objectType, objectName);
        if (objectUrl != null) {
            return objectUrl + "/source/main";
        }
        return null;
    }

    /**
     * Returns a {@link JsonArray} of the canonical (short) type keywords
     * for use in tool schema enum definitions.
     */
    public static JsonArray buildTypeEnumArray() {
        JsonArray arr = new JsonArray();
        arr.add("CLAS"); arr.add("INTF"); arr.add("PROG");
        arr.add("TABL"); arr.add("STRU"); arr.add("DDLS");
        arr.add("CDS");  arr.add("DTEL"); arr.add("DOMA");
        arr.add("SRVD"); arr.add("DDLX"); arr.add("BDEF");
        return arr;
    }

    /** Description string for the {@code objectType} parameter in tool schemas. */
    public static final String TYPE_DESCRIPTION =
            "Object type: CLAS (class), INTF (interface), PROG (program), "
            + "TABL (table), STRU (structure), DDLS/CDS (CDS view), "
            + "DTEL (data element), DOMA (domain), SRVD (service definition), "
            + "DDLX (metadata extension), BDEF (behavior definition)";

    /**
     * Builds the {@code objectType} JSON schema property for tool definitions.
     */
    public static JsonObject buildTypeProperty() {
        JsonObject prop = new JsonObject();
        prop.addProperty("type", "string");
        prop.addProperty("description", TYPE_DESCRIPTION);
        prop.add("enum", buildTypeEnumArray());
        return prop;
    }

    /**
     * Builds the {@code objectName} JSON schema property for tool definitions.
     */
    public static JsonObject buildNameProperty() {
        JsonObject prop = new JsonObject();
        prop.addProperty("type", "string");
        prop.addProperty("description",
                "Object name (e.g. 'ZCL_MY_CLASS', 'MARA'). Case-insensitive.");
        return prop;
    }

    /**
     * Returns a comma-separated list of supported type keywords.
     */
    public static String getSupportedTypes() {
        return "CLAS, INTF, PROG, TABL, STRU, DDLS/CDS, DTEL, DOMA, SRVD, DDLX, BDEF";
    }
}
