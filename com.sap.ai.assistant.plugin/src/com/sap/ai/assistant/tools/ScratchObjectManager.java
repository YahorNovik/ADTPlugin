package com.sap.ai.assistant.tools;

import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import com.sap.ai.assistant.sap.AdtRestClient;

/**
 * Lazily creates and caches scratch/placeholder ABAP objects in {@code $TMP}.
 * <p>
 * These scratch objects exist solely to provide a valid ADT source URL for
 * syntax-checking code that targets objects which do not yet exist in the
 * SAP system. The syntax check API requires a real object URL as context;
 * by substituting a scratch object's URL (and replacing the object name
 * inside the source), we can validate the code without creating the real
 * object first.
 * </p>
 *
 * <p>Supported object types:</p>
 * <ul>
 *   <li>Programs  — {@code ZSAP_AI_SCRATCH_PROG}</li>
 *   <li>Classes   — {@code ZSAP_AI_SCRATCH_CLAS}</li>
 *   <li>Interfaces — {@code ZSAP_AI_SCRATCH_INTF}</li>
 * </ul>
 */
public class ScratchObjectManager {

    private static final String SCRATCH_PROG = "ZSAP_AI_SCRATCH_PROG";
    private static final String SCRATCH_CLAS = "ZSAP_AI_SCRATCH_CLAS";
    private static final String SCRATCH_INTF = "ZSAP_AI_SCRATCH_INTF";

    private static final String SCRATCH_PROG_SOURCE = "/sap/bc/adt/programs/programs/zsap_ai_scratch_prog/source/main";
    private static final String SCRATCH_CLAS_SOURCE = "/sap/bc/adt/oo/classes/zsap_ai_scratch_clas/source/main";
    private static final String SCRATCH_INTF_SOURCE = "/sap/bc/adt/oo/interfaces/zsap_ai_scratch_intf/source/main";

    private static final String PACKAGE_NAME = "$TMP";
    private static final String PACKAGE_PATH = "/sap/bc/adt/packages/%24tmp";

    private final AdtRestClient client;

    private boolean progCreated;
    private boolean clasCreated;
    private boolean intfCreated;

    public ScratchObjectManager(AdtRestClient client) {
        this.client = client;
    }

    /**
     * Detect the object type from the given ADT source URL and return the
     * corresponding scratch object's source URL. Creates the scratch object
     * in {@code $TMP} on first use (lazy).
     *
     * @param originalUrl the ADT source URL for a (possibly non-existent) object
     * @return the scratch source URL, or {@code null} if the type is unsupported
     */
    public String getScratchSourceUrl(String originalUrl) {
        if (originalUrl == null) {
            return null;
        }
        String lower = originalUrl.toLowerCase();

        if (lower.contains("/programs/programs/")) {
            if (ensureProg()) {
                return SCRATCH_PROG_SOURCE;
            }
        } else if (lower.contains("/oo/classes/")) {
            if (ensureClas()) {
                return SCRATCH_CLAS_SOURCE;
            }
        } else if (lower.contains("/oo/interfaces/")) {
            if (ensureIntf()) {
                return SCRATCH_INTF_SOURCE;
            }
        }

        return null; // unsupported type
    }

    // ------------------------------------------------------------------
    // Lazy creation helpers
    // ------------------------------------------------------------------

    private boolean ensureProg() {
        if (progCreated) {
            return true;
        }
        progCreated = createObject(
                "/sap/bc/adt/programs/programs",
                buildProgramXml(SCRATCH_PROG, "AI scratch program for syntax check"));
        return progCreated;
    }

    private boolean ensureClas() {
        if (clasCreated) {
            return true;
        }
        clasCreated = createObject(
                "/sap/bc/adt/oo/classes",
                buildClassXml(SCRATCH_CLAS, "AI scratch class for syntax check"));
        return clasCreated;
    }

    private boolean ensureIntf() {
        if (intfCreated) {
            return true;
        }
        intfCreated = createObject(
                "/sap/bc/adt/oo/interfaces",
                buildInterfaceXml(SCRATCH_INTF, "AI scratch interface for syntax check"));
        return intfCreated;
    }

    /**
     * Try to create the scratch object. Returns {@code true} if creation
     * succeeded or if the object already exists (from a previous session).
     */
    private boolean createObject(String creationUrl, String xmlBody) {
        try {
            client.post(creationUrl, xmlBody, "application/*", "application/*");
            return true;
        } catch (Exception e) {
            // If the object already exists, that's fine — treat as success
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("already exists") || msg.contains("HTTP 500")) {
                return true;
            }
            System.err.println("ScratchObjectManager: failed to create scratch object at "
                    + creationUrl + ": " + msg);
            return false;
        }
    }

    // ------------------------------------------------------------------
    // XML builders (same patterns as CreateObjectTool / WriteAndCheckTool)
    // ------------------------------------------------------------------

    private String buildProgramXml(String name, String description) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<program:abapProgram xmlns:program=\"http://www.sap.com/adt/programs/programs\" ");
        xml.append("xmlns:adtcore=\"http://www.sap.com/adt/core\" ");
        xml.append("adtcore:type=\"PROG/P\" ");
        xml.append("adtcore:description=\"").append(escapeXml(description)).append("\" ");
        xml.append("adtcore:name=\"").append(escapeXml(name)).append("\">");
        xml.append("<adtcore:packageRef adtcore:name=\"").append(escapeXml(PACKAGE_NAME)).append("\" ");
        xml.append("adtcore:uri=\"").append(escapeXml(PACKAGE_PATH)).append("\"/>");
        xml.append("</program:abapProgram>");
        return xml.toString();
    }

    private String buildClassXml(String name, String description) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<class:abapClass xmlns:class=\"http://www.sap.com/adt/oo/classes\" ");
        xml.append("xmlns:adtcore=\"http://www.sap.com/adt/core\" ");
        xml.append("adtcore:type=\"CLAS/OC\" ");
        xml.append("adtcore:description=\"").append(escapeXml(description)).append("\" ");
        xml.append("adtcore:name=\"").append(escapeXml(name)).append("\">");
        xml.append("<adtcore:packageRef adtcore:name=\"").append(escapeXml(PACKAGE_NAME)).append("\" ");
        xml.append("adtcore:uri=\"").append(escapeXml(PACKAGE_PATH)).append("\"/>");
        xml.append("<class:include class:includeType=\"testclasses\" adtcore:type=\"CLAS/OC\"/>");
        xml.append("</class:abapClass>");
        return xml.toString();
    }

    private String buildInterfaceXml(String name, String description) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<intf:abapInterface xmlns:intf=\"http://www.sap.com/adt/oo/interfaces\" ");
        xml.append("xmlns:adtcore=\"http://www.sap.com/adt/core\" ");
        xml.append("adtcore:type=\"INTF/OI\" ");
        xml.append("adtcore:description=\"").append(escapeXml(description)).append("\" ");
        xml.append("adtcore:name=\"").append(escapeXml(name)).append("\">");
        xml.append("<adtcore:packageRef adtcore:name=\"").append(escapeXml(PACKAGE_NAME)).append("\" ");
        xml.append("adtcore:uri=\"").append(escapeXml(PACKAGE_PATH)).append("\"/>");
        xml.append("</intf:abapInterface>");
        return xml.toString();
    }

    private static String escapeXml(String value) {
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
