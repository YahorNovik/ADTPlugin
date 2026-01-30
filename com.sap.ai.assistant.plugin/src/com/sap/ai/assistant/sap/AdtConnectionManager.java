package com.sap.ai.assistant.sap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.sap.ai.assistant.model.SapSystemConnection;

/**
 * Discovers SAP systems from the Eclipse workspace (ADT projects) and
 * allows manually registered connections as a fallback.
 * <p>
 * ADT SDK classes are <b>optional</b> at runtime. All ADT-specific calls
 * are wrapped in try/catch blocks so the plug-in works even when the
 * ADT feature is not installed.
 * </p>
 */
public class AdtConnectionManager {

    /** ADT project nature ID used by SAP ABAP projects in Eclipse. */
    private static final String ADT_PROJECT_NATURE = "com.sap.adt.project.nature";

    /** Alternative ADT nature ID (older ADT versions). */
    private static final String ADT_PROJECT_NATURE_ALT = "com.sap.adt.tools.abap.project.nature";

    /** Manually registered SAP systems (for when ADT is not available). */
    private final List<SapSystemConnection> manualSystems = new ArrayList<>();

    // ---------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------

    /**
     * Discover all SAP systems available in the current Eclipse workspace.
     * <p>
     * The method first tries to find ADT projects via the Eclipse
     * Resources API. If the ADT SDK is not on the classpath, or no
     * ADT projects are found, the manual systems list is returned.
     * </p>
     *
     * @return unmodifiable list of discovered connections
     */
    public List<SapSystemConnection> discoverSystems() {
        List<SapSystemConnection> result = new ArrayList<>();

        // Try to discover from Eclipse workspace (ADT projects)
        try {
            List<SapSystemConnection> discovered = discoverFromWorkspace();
            result.addAll(discovered);
        } catch (ClassNotFoundException e) {
            // ADT / Eclipse Resources API not on classpath -- ignore
        } catch (Exception e) {
            // Any other reflection or runtime error -- log and continue
            System.err.println("AdtConnectionManager: failed to discover ADT projects: " + e.getMessage());
        }

        // Always append manual systems
        result.addAll(manualSystems);

        return Collections.unmodifiableList(result);
    }

    /**
     * Register a SAP system manually. Use this when the ADT SDK is not
     * installed or the system is not represented as an Eclipse project.
     *
     * @param name     display name
     * @param host     hostname, e.g. "myhost.sap.corp"
     * @param port     HTTP(S) port
     * @param client   SAP client, e.g. "100"
     * @param user     SAP user name
     * @param password SAP password
     */
    public void addManualSystem(String name, String host, int port,
                                String client, String user, String password) {
        manualSystems.add(new SapSystemConnection(name, host, port, client, user, password, true));
    }

    /**
     * Return the list of manually registered systems.
     *
     * @return unmodifiable list
     */
    public List<SapSystemConnection> getManualSystems() {
        return Collections.unmodifiableList(manualSystems);
    }

    /**
     * Remove all manually registered systems.
     */
    public void clearManualSystems() {
        manualSystems.clear();
    }

    // ---------------------------------------------------------------
    // Eclipse workspace discovery (all ADT calls wrapped)
    // ---------------------------------------------------------------

    /**
     * Iterate over workspace projects and detect ADT (ABAP) projects.
     * <p>
     * This method deliberately uses fully-qualified class names and
     * reflection-safe patterns so that it compiles without the ADT SDK
     * on the build path. All Eclipse resource classes are loaded
     * dynamically.
     * </p>
     *
     * @return list of discovered connections (may be empty)
     * @throws ClassNotFoundException if Eclipse Resources API is absent
     */
    private List<SapSystemConnection> discoverFromWorkspace() throws ClassNotFoundException {
        List<SapSystemConnection> connections = new ArrayList<>();

        // Load Eclipse Resources API -- will throw ClassNotFoundException
        // if org.eclipse.core.resources is not available.
        Class.forName("org.eclipse.core.resources.ResourcesPlugin");

        org.eclipse.core.resources.IWorkspaceRoot root =
                org.eclipse.core.resources.ResourcesPlugin.getWorkspace().getRoot();

        org.eclipse.core.resources.IProject[] projects = root.getProjects();

        for (org.eclipse.core.resources.IProject project : projects) {
            if (!project.isOpen()) {
                continue;
            }

            try {
                if (isAdtProject(project)) {
                    SapSystemConnection conn = extractConnectionFromProject(project);
                    if (conn != null) {
                        connections.add(conn);
                    }
                }
            } catch (Exception e) {
                // Skip this project on any error
                System.err.println("AdtConnectionManager: skipping project '"
                        + project.getName() + "': " + e.getMessage());
            }
        }

        return connections;
    }

    /**
     * Determine whether the given project is an ADT (ABAP) project.
     * Tries the project nature first; falls back to checking persistent
     * properties.
     */
    private boolean isAdtProject(org.eclipse.core.resources.IProject project) {
        try {
            // Primary check: project nature
            if (project.hasNature(ADT_PROJECT_NATURE)) {
                return true;
            }
            if (project.hasNature(ADT_PROJECT_NATURE_ALT)) {
                return true;
            }
        } catch (Exception e) {
            // Nature check failed -- try property fallback below
        }

        // Fallback: check for a well-known ADT project property
        try {
            String adtDest = project.getPersistentProperty(
                    new org.eclipse.core.runtime.QualifiedName(
                            "com.sap.adt.project", "destination"));
            return adtDest != null && !adtDest.isEmpty();
        } catch (Exception e) {
            // Property check failed as well
        }

        return false;
    }

    /**
     * Attempt to extract SAP connection information from an ADT project.
     * <p>
     * Since the ADT SDK provides adapter interfaces
     * (e.g. {@code IDestinationData}) that may or may not be present,
     * this method wraps every access in try/catch.
     * </p>
     *
     * @return a connection descriptor, or null if extraction fails
     */
    private SapSystemConnection extractConnectionFromProject(
            org.eclipse.core.resources.IProject project) {
        String projectName = project.getName();

        // Try to read destination properties stored by ADT
        try {
            String destination = project.getPersistentProperty(
                    new org.eclipse.core.runtime.QualifiedName(
                            "com.sap.adt.project", "destination"));

            String host = project.getPersistentProperty(
                    new org.eclipse.core.runtime.QualifiedName(
                            "com.sap.adt.project", "host"));

            String portStr = project.getPersistentProperty(
                    new org.eclipse.core.runtime.QualifiedName(
                            "com.sap.adt.project", "port"));

            String client = project.getPersistentProperty(
                    new org.eclipse.core.runtime.QualifiedName(
                            "com.sap.adt.project", "client"));

            String user = project.getPersistentProperty(
                    new org.eclipse.core.runtime.QualifiedName(
                            "com.sap.adt.project", "user"));

            if (host != null && !host.isEmpty()) {
                int port = 443;
                if (portStr != null && !portStr.isEmpty()) {
                    try {
                        port = Integer.parseInt(portStr);
                    } catch (NumberFormatException ignored) {
                        // keep default
                    }
                }

                String displayName = (destination != null && !destination.isEmpty())
                        ? destination : projectName;

                // Password is not stored as a project property for security.
                // The caller must supply it or prompt the user.
                return new SapSystemConnection(
                        displayName, host, port,
                        client != null ? client : "000",
                        user != null ? user : "",
                        "" /* password must be provided separately */,
                        true /* useSsl */
                );
            }
        } catch (Exception e) {
            System.err.println("AdtConnectionManager: could not read properties for '"
                    + projectName + "': " + e.getMessage());
        }

        // Try ADT SDK adapter as a last resort
        try {
            return extractViaAdtSdkAdapter(project);
        } catch (ClassNotFoundException e) {
            // ADT SDK not installed
        } catch (Exception e) {
            System.err.println("AdtConnectionManager: ADT SDK adapter failed for '"
                    + projectName + "': " + e.getMessage());
        }

        return null;
    }

    /**
     * Attempt to use the ADT SDK adapter API to get connection info.
     * This method loads ADT SDK classes by name so it compiles without
     * them on the build path.
     *
     * @throws ClassNotFoundException if the ADT SDK is not installed
     */
    private SapSystemConnection extractViaAdtSdkAdapter(
            org.eclipse.core.resources.IProject project) throws ClassNotFoundException {
        // Verify ADT SDK is available
        Class.forName("com.sap.adt.destinations.model.IDestinationData");

        // Use reflection-safe access via Eclipse adapter framework
        Object adapted = project.getAdapter(
                Class.forName("com.sap.adt.destinations.model.IDestinationData"));

        if (adapted == null) {
            return null;
        }

        try {
            // IDestinationData has getHost(), getPort(), getClient(), getUser()
            java.lang.reflect.Method getHost = adapted.getClass().getMethod("getHost");
            java.lang.reflect.Method getPort = adapted.getClass().getMethod("getPort");
            java.lang.reflect.Method getClient = adapted.getClass().getMethod("getClient");
            java.lang.reflect.Method getUser = adapted.getClass().getMethod("getUser");

            String host = (String) getHost.invoke(adapted);
            Object portObj = getPort.invoke(adapted);
            String client = (String) getClient.invoke(adapted);
            String user = (String) getUser.invoke(adapted);

            int port = 443;
            if (portObj instanceof Number) {
                port = ((Number) portObj).intValue();
            } else if (portObj instanceof String) {
                port = Integer.parseInt((String) portObj);
            }

            return new SapSystemConnection(
                    project.getName(), host, port,
                    client != null ? client : "000",
                    user != null ? user : "",
                    "" /* password not available through adapter */,
                    true /* useSsl */
            );
        } catch (Exception e) {
            System.err.println("AdtConnectionManager: reflection on IDestinationData failed: "
                    + e.getMessage());
            return null;
        }
    }
}
