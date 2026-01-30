package com.sap.ai.assistant.sap;

import java.lang.reflect.Method;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.util.List;

/**
 * Attempts to extract credentials from existing ADT (ABAP Development Tools)
 * connections in the Eclipse workspace.
 * <p>
 * All ADT-specific access is done via reflection so the plug-in continues to
 * work when the ADT feature is not installed. The methods return {@code null}
 * when credentials cannot be obtained.
 * </p>
 * <p>
 * Two strategies are used:
 * <ol>
 *   <li>Read the password from Eclipse Equinox Secure Storage where ADT
 *       persists saved passwords.</li>
 *   <li>Extract the authenticated HTTP cookies / CSRF token from ADT's
 *       internal communication layer via the {@code IAdtCoreProject}
 *       adapter.</li>
 * </ol>
 * </p>
 */
public class AdtCredentialProvider {

    private AdtCredentialProvider() {
        // static utility
    }

    // ------------------------------------------------------------------
    // Strategy 1: Eclipse Secure Storage password lookup
    // ------------------------------------------------------------------

    /**
     * Tries to read the SAP password from Eclipse's Equinox Secure Storage.
     * ADT stores credentials under well-known preference node paths keyed
     * by the destination ID.
     *
     * @param destinationId the ADT destination identifier (from IDestinationData)
     * @return the password, or {@code null} if not found / not accessible
     */
    public static String tryGetPasswordFromSecureStore(String destinationId) {
        if (destinationId == null || destinationId.isEmpty()) {
            return null;
        }
        try {
            // Load SecurePreferencesFactory via reflection (optional bundle)
            Class<?> factoryClass = Class.forName(
                    "org.eclipse.equinox.security.storage.SecurePreferencesFactory");
            Method getDefault = factoryClass.getMethod("getDefault");
            Object securePrefs = getDefault.invoke(null);

            // Try known paths where ADT stores passwords
            String[] paths = {
                "/com.sap.adt.projectmanagement/destinations/" + destinationId,
                "/com.sap.adt.tools.core/destinations/" + destinationId,
                "/com.sap.adt.destinations/" + destinationId,
                "/com.sap.adt.communication/" + destinationId
            };

            Method nodeExists = securePrefs.getClass().getMethod("nodeExists", String.class);
            Method node = securePrefs.getClass().getMethod("node", String.class);

            for (String path : paths) {
                Boolean exists = (Boolean) nodeExists.invoke(securePrefs, path);
                if (!Boolean.TRUE.equals(exists)) {
                    continue;
                }

                Object prefNode = node.invoke(securePrefs, path);
                Method get = prefNode.getClass().getMethod("get", String.class, String.class);

                // Try common key names
                String[] keys = {"password", "pwd", "Password"};
                for (String key : keys) {
                    String password = (String) get.invoke(prefNode, key, null);
                    if (password != null && !password.isEmpty()) {
                        System.out.println("AdtCredentialProvider: found password in secure store at " + path);
                        return password;
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            // Equinox security bundle not available
            System.out.println("AdtCredentialProvider: Secure Storage API not available");
        } catch (Exception e) {
            System.err.println("AdtCredentialProvider: secure store lookup failed: " + e.getMessage());
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Strategy 2: Extract cookies from ADT's HTTP session
    // ------------------------------------------------------------------

    /**
     * Result holder for extracted session data from an ADT connection.
     */
    public static class AdtSessionData {
        private final CookieManager cookieManager;
        private final String csrfToken;

        public AdtSessionData(CookieManager cookieManager, String csrfToken) {
            this.cookieManager = cookieManager;
            this.csrfToken = csrfToken;
        }

        public CookieManager getCookieManager() {
            return cookieManager;
        }

        public String getCsrfToken() {
            return csrfToken;
        }
    }

    /**
     * Attempts to extract authenticated session cookies and CSRF token from
     * the ADT project's internal HTTP client via reflection.
     * <p>
     * Traverses the object graph:
     * {@code IProject -> IAdtCoreProject -> getConnection() -> ...}
     * </p>
     *
     * @param adtProject the Eclipse IProject reference (must be an ADT project)
     * @param baseUrl    the SAP system base URL for cookie domain scoping
     * @return session data with cookies and optional CSRF token, or {@code null}
     */
    public static AdtSessionData tryExtractSessionData(Object adtProject, String baseUrl) {
        if (adtProject == null) {
            return null;
        }

        try {
            // Try to get IAdtCoreProject adapter
            Object coreProject = tryGetAdtCoreProject(adtProject);
            if (coreProject == null) {
                return null;
            }

            // Try to get the destination data for cookie extraction
            Object destData = tryInvoke(coreProject, "getDestinationData");
            if (destData == null) {
                destData = tryInvoke(coreProject, "getDestination");
            }

            // Try to get connection/session from the core project
            Object connection = tryInvoke(coreProject, "getConnection");
            if (connection == null) {
                connection = tryInvoke(coreProject, "getSession");
            }

            if (connection != null) {
                return extractSessionFromConnection(connection, baseUrl);
            }

            // Alternative: try to get the HttpClient directly
            Object httpClient = tryInvoke(coreProject, "getHttpClient");
            if (httpClient != null) {
                return extractSessionFromHttpClient(httpClient, baseUrl);
            }

        } catch (Exception e) {
            System.err.println("AdtCredentialProvider: session extraction failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * Adapts an IProject to IAdtCoreProject via Platform.getAdapterManager().
     */
    private static Object tryGetAdtCoreProject(Object project) {
        try {
            // Use Platform adapter manager with lazy loading
            Class<?> platformClass = Class.forName("org.eclipse.core.runtime.Platform");
            Method getAdapterManager = platformClass.getMethod("getAdapterManager");
            Object adapterManager = getAdapterManager.invoke(null);

            Method loadAdapter = adapterManager.getClass().getMethod(
                    "loadAdapter", Object.class, String.class);

            // Try known adapter type names
            String[] adapterTypes = {
                "com.sap.adt.project.IAdtCoreProject",
                "com.sap.adt.tools.core.project.IAdtCoreProject"
            };

            for (String type : adapterTypes) {
                Object adapted = loadAdapter.invoke(adapterManager, project, type);
                if (adapted != null) {
                    return adapted;
                }
            }
        } catch (Exception e) {
            // ADT SDK not available
        }
        return null;
    }

    /**
     * Extracts cookies from an ADT connection/session object.
     */
    private static AdtSessionData extractSessionFromConnection(Object connection, String baseUrl) {
        try {
            // Try to get cookie store/manager
            Object cookieStore = tryInvoke(connection, "getCookieStore");
            if (cookieStore == null) {
                cookieStore = tryInvoke(connection, "getCookieManager");
            }
            if (cookieStore == null) {
                cookieStore = tryInvoke(connection, "getCookies");
            }

            if (cookieStore != null) {
                CookieManager cm = buildCookieManager(cookieStore, baseUrl);
                if (cm != null) {
                    // Try to get CSRF token
                    String csrf = tryGetCsrfToken(connection);
                    return new AdtSessionData(cm, csrf);
                }
            }
        } catch (Exception e) {
            System.err.println("AdtCredentialProvider: connection extraction failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * Extracts cookies from a Java HttpClient object.
     */
    private static AdtSessionData extractSessionFromHttpClient(Object httpClient, String baseUrl) {
        try {
            Object cookieHandler = tryInvoke(httpClient, "cookieHandler");
            if (cookieHandler == null) return null;

            // cookieHandler returns Optional<CookieHandler>
            if (cookieHandler.getClass().getName().contains("Optional")) {
                Method isPresent = cookieHandler.getClass().getMethod("isPresent");
                if (Boolean.TRUE.equals(isPresent.invoke(cookieHandler))) {
                    Method get = cookieHandler.getClass().getMethod("get");
                    cookieHandler = get.invoke(cookieHandler);
                } else {
                    return null;
                }
            }

            if (cookieHandler instanceof java.net.CookieManager) {
                CookieManager existing = (CookieManager) cookieHandler;
                // Clone cookies into a new CookieManager
                CookieManager cm = new CookieManager();
                cm.setCookiePolicy(java.net.CookiePolicy.ACCEPT_ALL);
                URI uri = URI.create(baseUrl);
                List<HttpCookie> cookies = existing.getCookieStore().get(uri);
                for (HttpCookie cookie : cookies) {
                    cm.getCookieStore().add(uri, cookie);
                }
                if (!cookies.isEmpty()) {
                    System.out.println("AdtCredentialProvider: extracted " + cookies.size()
                            + " cookies from ADT HttpClient");
                    return new AdtSessionData(cm, null);
                }
            }
        } catch (Exception e) {
            System.err.println("AdtCredentialProvider: HttpClient extraction failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * Builds a CookieManager from whatever cookie container we found.
     */
    @SuppressWarnings("unchecked")
    private static CookieManager buildCookieManager(Object cookieSource, String baseUrl) {
        try {
            CookieManager cm = new CookieManager();
            cm.setCookiePolicy(java.net.CookiePolicy.ACCEPT_ALL);
            URI uri = URI.create(baseUrl);

            // If it's already a CookieStore
            if (cookieSource instanceof java.net.CookieStore) {
                List<HttpCookie> cookies = ((java.net.CookieStore) cookieSource).get(uri);
                for (HttpCookie cookie : cookies) {
                    cm.getCookieStore().add(uri, cookie);
                }
                if (!cookies.isEmpty()) {
                    return cm;
                }
            }

            // If it's a CookieManager
            if (cookieSource instanceof CookieManager) {
                List<HttpCookie> cookies = ((CookieManager) cookieSource).getCookieStore().get(uri);
                for (HttpCookie cookie : cookies) {
                    cm.getCookieStore().add(uri, cookie);
                }
                if (!cookies.isEmpty()) {
                    return cm;
                }
            }

            // If it's a List of cookies
            if (cookieSource instanceof List) {
                List<?> list = (List<?>) cookieSource;
                for (Object item : list) {
                    if (item instanceof HttpCookie) {
                        cm.getCookieStore().add(uri, (HttpCookie) item);
                    }
                }
                if (!list.isEmpty()) {
                    return cm;
                }
            }

            // Try getCookies() method on the store
            try {
                Method getCookies = cookieSource.getClass().getMethod("getCookies");
                Object result = getCookies.invoke(cookieSource);
                if (result instanceof List) {
                    for (Object item : (List<?>) result) {
                        if (item instanceof HttpCookie) {
                            cm.getCookieStore().add(uri, (HttpCookie) item);
                        }
                    }
                    return cm;
                }
            } catch (NoSuchMethodException ignored) {
                // not available
            }

        } catch (Exception e) {
            System.err.println("AdtCredentialProvider: cookie manager build failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * Tries to extract a CSRF token from the connection object.
     */
    private static String tryGetCsrfToken(Object connection) {
        // Try common method names
        String[] methods = {"getCsrfToken", "getXCsrfToken", "getToken"};
        for (String method : methods) {
            Object token = tryInvoke(connection, method);
            if (token instanceof String && !((String) token).isEmpty()) {
                return (String) token;
            }
        }
        return null;
    }

    /**
     * Safely invokes a no-arg method on an object via reflection.
     */
    private static Object tryInvoke(Object obj, String methodName) {
        if (obj == null) return null;
        try {
            Method m = obj.getClass().getMethod(methodName);
            return m.invoke(obj);
        } catch (Exception e) {
            return null;
        }
    }
}
