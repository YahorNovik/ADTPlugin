package com.sap.ai.assistant.sap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.sap.ai.assistant.model.SavedSapSystem;
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

    /** ADT project natures used by SAP ABAP projects in Eclipse. */
    private static final String[] ADT_NATURES = {
        "com.sap.adt.project.adtnature",
        "com.sap.adt.project.nature",
        "com.sap.adt.tools.abap.project.nature"
    };

    /** Manually registered SAP systems (for when ADT is not available). */
    private final List<SapSystemConnection> manualSystems = new ArrayList<>();

    // ---------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------

    /**
     * Discover all SAP systems available in the current Eclipse workspace.
     */
    public List<SapSystemConnection> discoverSystems() {
        List<SapSystemConnection> result = new ArrayList<>();

        // Try to discover from Eclipse workspace (ADT projects)
        try {
            List<SapSystemConnection> discovered = discoverFromWorkspace();
            result.addAll(discovered);
        } catch (ClassNotFoundException e) {
            // ADT / Eclipse Resources API not on classpath -- ignore
            System.out.println("AdtConnectionManager: Resources API not available, skipping workspace discovery");
        } catch (Exception e) {
            System.err.println("AdtConnectionManager: failed to discover ADT projects: " + e.getMessage());
        }

        // Always append manual systems
        result.addAll(manualSystems);

        return Collections.unmodifiableList(result);
    }

    /**
     * Register a SAP system manually.
     */
    public void addManualSystem(String name, String host, int port,
                                String client, String user, String password, boolean useSsl) {
        manualSystems.add(new SapSystemConnection(name, host, port, client, user, password, useSsl));
    }

    /**
     * Infer SSL from port number.
     * Ports 443, 8443, 44300-44399 → SSL; everything else → no SSL.
     */
    public static boolean inferSsl(int port) {
        if (port == 443 || port == 8443) return true;
        if (port >= 44300 && port <= 44399) return true;
        return false;
    }

    public List<SapSystemConnection> getManualSystems() {
        return Collections.unmodifiableList(manualSystems);
    }

    public void clearManualSystems() {
        manualSystems.clear();
    }

    /**
     * Loads previously saved systems from a JSON string and adds them to
     * the manual systems list, skipping duplicates (same host+port+client).
     */
    public void loadSavedSystems(String json) {
        List<SavedSapSystem> saved = SavedSapSystem.fromJson(json);
        for (SavedSapSystem s : saved) {
            boolean duplicate = false;
            for (SapSystemConnection m : manualSystems) {
                if (m.getHost().equals(s.getHost())
                        && m.getPort() == s.getPort()
                        && m.getClient().equals(s.getClient())) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                manualSystems.add(s.toConnection());
            }
        }
    }

    /**
     * Removes a manual system by its index in the manual systems list.
     */
    public void removeManualSystem(int index) {
        if (index >= 0 && index < manualSystems.size()) {
            manualSystems.remove(index);
        }
    }

    // ---------------------------------------------------------------
    // Eclipse workspace discovery
    // ---------------------------------------------------------------

    private List<SapSystemConnection> discoverFromWorkspace() throws ClassNotFoundException {
        List<SapSystemConnection> connections = new ArrayList<>();

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
                System.err.println("AdtConnectionManager: skipping project '"
                        + project.getName() + "': " + e.getMessage());
            }
        }

        return connections;
    }

    private boolean isAdtProject(org.eclipse.core.resources.IProject project) {
        // Check all known ADT nature IDs
        for (String nature : ADT_NATURES) {
            try {
                if (project.hasNature(nature)) {
                    return true;
                }
            } catch (Exception e) {
                // Nature check failed
            }
        }

        // Fallback: check for well-known ADT project properties
        try {
            String adtDest = project.getPersistentProperty(
                    new org.eclipse.core.runtime.QualifiedName(
                            "com.sap.adt.project", "destination"));
            if (adtDest != null && !adtDest.isEmpty()) {
                return true;
            }
        } catch (Exception e) {
            // ignore
        }

        // Fallback: check for ADT nature descriptor in project description
        try {
            String[] natures = project.getDescription().getNatureIds();
            if (natures != null) {
                for (String n : natures) {
                    if (n != null && n.contains("com.sap.adt")) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }

        return false;
    }

    private SapSystemConnection extractConnectionFromProject(
            org.eclipse.core.resources.IProject project) {
        String projectName = project.getName();

        // 1. Try ADT SDK adapter (preferred approach)
        try {
            SapSystemConnection conn = extractViaAdtAdapter(project);
            if (conn != null) {
                return conn;
            }
        } catch (ClassNotFoundException e) {
            // ADT SDK not installed
        } catch (Exception e) {
            System.err.println("AdtConnectionManager: adapter extraction failed for '"
                    + projectName + "': " + e.getMessage());
        }

        // 2. Try Platform adapter manager with lazy loading
        try {
            SapSystemConnection conn = extractViaPlatformAdapter(project);
            if (conn != null) {
                return conn;
            }
        } catch (Exception e) {
            // ignore
        }

        // 3. Try reading persistent properties
        try {
            SapSystemConnection conn = extractFromProperties(project);
            if (conn != null) {
                return conn;
            }
        } catch (Exception e) {
            // ignore
        }

        // Could not extract real connection data — skip this project.
        // User should use "Add Manual..." to register with proper host/credentials.
        System.out.println("AdtConnectionManager: no connection data found for '"
                + projectName + "' — use Add Manual to register this system");
        return null;
    }

    /**
     * Use the ADT SDK IDestinationData adapter via reflection.
     */
    private SapSystemConnection extractViaAdtAdapter(
            org.eclipse.core.resources.IProject project) throws ClassNotFoundException {
        Class.forName("com.sap.adt.destinations.model.IDestinationData");

        Object adapted = project.getAdapter(
                Class.forName("com.sap.adt.destinations.model.IDestinationData"));

        if (adapted == null) {
            return null;
        }

        SapSystemConnection conn = extractFromAdaptedObject(project.getName(), adapted);
        if (conn != null) {
            conn.setAdtProject(project);
            // Extract destination ID
            String destId = invokeStringMethod(adapted, "getId");
            if (destId == null || destId.isEmpty()) {
                destId = invokeStringMethod(adapted, "getDestinationId");
            }
            if (destId == null || destId.isEmpty()) {
                // Fall back to project name as destination ID
                destId = project.getName();
            }
            conn.setDestinationId(destId);
        }
        return conn;
    }

    /**
     * Use Platform.getAdapterManager().loadAdapter() which triggers lazy adapter factory loading.
     */
    private SapSystemConnection extractViaPlatformAdapter(
            org.eclipse.core.resources.IProject project) {
        try {
            Object adapted = org.eclipse.core.runtime.Platform.getAdapterManager()
                    .loadAdapter(project, "com.sap.adt.destinations.model.IDestinationData");
            if (adapted != null) {
                SapSystemConnection conn = extractFromAdaptedObject(project.getName(), adapted);
                if (conn != null) {
                    conn.setAdtProject(project);
                    String destId = invokeStringMethod(adapted, "getId");
                    if (destId == null || destId.isEmpty()) {
                        destId = invokeStringMethod(adapted, "getDestinationId");
                    }
                    if (destId == null || destId.isEmpty()) {
                        destId = project.getName();
                    }
                    conn.setDestinationId(destId);
                }
                return conn;
            }
        } catch (Exception e) {
            // Adapter not available
        }

        // Also try IAdtCoreProject adapter
        try {
            Object coreProject = org.eclipse.core.runtime.Platform.getAdapterManager()
                    .loadAdapter(project, "com.sap.adt.project.IAdtCoreProject");
            if (coreProject != null) {
                java.lang.reflect.Method getDestData = coreProject.getClass().getMethod("getDestinationData");
                Object destData = getDestData.invoke(coreProject);
                if (destData != null) {
                    SapSystemConnection conn = extractFromAdaptedObject(project.getName(), destData);
                    if (conn != null) {
                        conn.setAdtProject(project);
                        String destId = invokeStringMethod(destData, "getId");
                        if (destId == null || destId.isEmpty()) {
                            destId = invokeStringMethod(destData, "getDestinationId");
                        }
                        if (destId == null || destId.isEmpty()) {
                            destId = project.getName();
                        }
                        conn.setDestinationId(destId);
                    }
                    return conn;
                }
            }
        } catch (Exception e) {
            // ignore
        }

        return null;
    }

    /**
     * Extract connection info from an IDestinationData object using reflection.
     */
    private SapSystemConnection extractFromAdaptedObject(String projectName, Object adapted) {
        try {
            String host = invokeStringMethod(adapted, "getHost");
            if (host == null || host.isEmpty()) {
                // Try alternative method names
                host = invokeStringMethod(adapted, "getSystemHost");
            }
            if (host == null || host.isEmpty()) {
                return null;
            }

            int port = 443;
            try {
                Object portObj = adapted.getClass().getMethod("getPort").invoke(adapted);
                if (portObj instanceof Number) {
                    port = ((Number) portObj).intValue();
                } else if (portObj instanceof String && !((String) portObj).isEmpty()) {
                    port = Integer.parseInt((String) portObj);
                }
            } catch (Exception e) {
                // keep default
            }

            String client = invokeStringMethod(adapted, "getClient");
            String user = invokeStringMethod(adapted, "getUser");
            String sysId = invokeStringMethod(adapted, "getSystemId");

            String displayName = (sysId != null && !sysId.isEmpty())
                    ? sysId + " [" + projectName + "]"
                    : projectName;

            return new SapSystemConnection(
                    displayName, host, port,
                    client != null ? client : "000",
                    user != null ? user : "",
                    "" /* password not available through adapter */,
                    inferSsl(port));
        } catch (Exception e) {
            System.err.println("AdtConnectionManager: reflection failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Try to read destination properties stored as persistent project properties.
     */
    private SapSystemConnection extractFromProperties(
            org.eclipse.core.resources.IProject project) {
        String projectName = project.getName();

        try {
            // Try multiple qualifier patterns used by different ADT versions
            String[] qualifiers = {
                "com.sap.adt.project",
                "com.sap.adt.destinations",
                "com.sap.adt.tools.core"
            };

            for (String qualifier : qualifiers) {
                String host = project.getPersistentProperty(
                        new org.eclipse.core.runtime.QualifiedName(qualifier, "host"));
                if (host != null && !host.isEmpty()) {
                    String portStr = project.getPersistentProperty(
                            new org.eclipse.core.runtime.QualifiedName(qualifier, "port"));
                    String client = project.getPersistentProperty(
                            new org.eclipse.core.runtime.QualifiedName(qualifier, "client"));
                    String user = project.getPersistentProperty(
                            new org.eclipse.core.runtime.QualifiedName(qualifier, "user"));

                    int port = 443;
                    if (portStr != null && !portStr.isEmpty()) {
                        try { port = Integer.parseInt(portStr); } catch (NumberFormatException ignored) {}
                    }

                    return new SapSystemConnection(
                            projectName, host, port,
                            client != null ? client : "000",
                            user != null ? user : "",
                            "", inferSsl(port));
                }
            }
        } catch (Exception e) {
            // ignore
        }

        return null;
    }

    private static String invokeStringMethod(Object obj, String methodName) {
        try {
            java.lang.reflect.Method m = obj.getClass().getMethod(methodName);
            Object result = m.invoke(obj);
            return result instanceof String ? (String) result : null;
        } catch (Exception e) {
            return null;
        }
    }
}
