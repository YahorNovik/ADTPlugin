package com.sap.ai.assistant.tools;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.JsonObject;
import com.sap.ai.assistant.sap.AdtRestClient;

/**
 * Base class for all SAP ADT tools. Provides the shared
 * {@link AdtRestClient} instance and common helper methods for
 * reading optional parameters and URL-encoding values.
 */
public abstract class AbstractSapTool implements SapTool {

    /** The ADT REST client used for all HTTP calls to the SAP system. */
    protected final AdtRestClient client;

    /**
     * Creates a new tool backed by the given ADT REST client.
     *
     * @param client the authenticated ADT REST client
     */
    protected AbstractSapTool(AdtRestClient client) {
        this.client = client;
    }

    /**
     * Headers that enable a stateful ADT session. Required for lock, write,
     * and unlock operations so that the lock handle is recognised across
     * requests within the same HTTP session.
     */
    protected static final Map<String, String> STATEFUL_HEADERS =
            Map.of(AdtRestClient.SESSION_TYPE_HEADER, "stateful");

    // ------------------------------------------------------------------
    // Helper methods
    // ------------------------------------------------------------------

    /**
     * Returns the string value of the given key from a {@link JsonObject},
     * or {@code null} if the key is missing or the value is
     * {@link com.google.gson.JsonNull}.
     *
     * @param obj the JSON object
     * @param key the property key
     * @return the string value, or {@code null}
     */
    protected String optString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        return obj.get(key).getAsString();
    }

    /**
     * Returns the int value of the given key from a {@link JsonObject},
     * or the supplied default if the key is missing or not a number.
     *
     * @param obj          the JSON object
     * @param key          the property key
     * @param defaultValue the fallback value
     * @return the int value, or {@code defaultValue}
     */
    protected int optInt(JsonObject obj, String key, int defaultValue) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return defaultValue;
        }
        try {
            return obj.get(key).getAsInt();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Resolve a source URL from tool arguments.
     * Tries {@code objectType} + {@code objectName} first (via {@link AdtUrlResolver}),
     * then falls back to the named URL parameter.
     *
     * @param arguments    the tool arguments JSON
     * @param urlParamName the fallback URL parameter name (e.g. "url", "objectSourceUrl")
     * @return the resolved source URL, or {@code null} if neither is provided
     */
    protected String resolveSourceUrlArg(JsonObject arguments, String urlParamName) {
        String type = optString(arguments, "objectType");
        String name = optString(arguments, "objectName");
        String resolved = AdtUrlResolver.resolveSourceUrl(type, name);
        if (resolved != null) {
            return resolved;
        }
        return optString(arguments, urlParamName);
    }

    /**
     * Resolve an object URL from tool arguments.
     * Tries {@code objectType} + {@code objectName} first (via {@link AdtUrlResolver}),
     * then falls back to the named URL parameter.
     *
     * @param arguments    the tool arguments JSON
     * @param urlParamName the fallback URL parameter name (e.g. "objectUrl")
     * @return the resolved object URL, or {@code null} if neither is provided
     */
    protected String resolveObjectUrlArg(JsonObject arguments, String urlParamName) {
        String type = optString(arguments, "objectType");
        String name = optString(arguments, "objectName");
        String resolved = AdtUrlResolver.resolveObjectUrl(type, name);
        if (resolved != null) {
            return resolved;
        }
        return optString(arguments, urlParamName);
    }

    /**
     * URL-encode a string using UTF-8.
     *
     * @param value the raw value
     * @return the URL-encoded value
     */
    protected String urlEncode(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Sanitize function module source by removing {@code *"} parameter comment
     * lines that SAP rejects with "Parameter comment blocks are not allowed".
     *
     * @param source the raw ABAP source code
     * @return the sanitized source with {@code *"} lines removed
     */
    public static String sanitizeFmSource(String source) {
        if (source == null) return null;
        return source.lines()
                .filter(line -> !line.stripLeading().startsWith("*\""))
                .collect(Collectors.joining("\n"));
    }

    /**
     * Returns {@code true} if the given ADT URL points to a function module
     * (contains {@code /fmodules/}).
     *
     * @param url the ADT source or object URL
     * @return whether this is a function module URL
     */
    public static boolean isFunctionModuleUrl(String url) {
        return url != null && url.toLowerCase().contains("/fmodules/");
    }

    /**
     * Ensure a URL points to the source endpoint ({@code /source/main}),
     * not just the object itself.
     * <p>
     * When the LLM passes an Eclipse workspace URI (e.g.
     * {@code E19_100/.adt/programs/programs/ztest/ztest.asprog})
     * or an object URL like {@code /sap/bc/adt/programs/programs/ztest},
     * SAP APIs for source operations (syntax check, PUT source, lock/unlock)
     * require the full source URL
     * {@code /sap/bc/adt/programs/programs/ztest/source/main}.
     * </p>
     *
     * @param url the ADT URL (may be Eclipse workspace URI, object URL, or source URL)
     * @return the source URL with {@code /source/main} appended if needed
     */
    public static String ensureSourceUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        // First, normalize Eclipse workspace URIs to standard ADT REST paths
        String normalized = normalizeEclipseUri(url);

        // Strip query string for pattern matching
        String path = normalized;
        String query = "";
        int qIdx = path.indexOf('?');
        if (qIdx >= 0) {
            query = path.substring(qIdx);
            path = path.substring(0, qIdx);
        }
        // Already a source URL
        if (path.contains("/source/")) {
            return normalized;
        }
        // Known object types: append /source/main
        if (path.matches(".*/programs/programs/[^/]+")
                || path.matches(".*/oo/classes/[^/]+")
                || path.matches(".*/oo/interfaces/[^/]+")
                || path.matches(".*/fmodules/[^/]+")) {
            return path + "/source/main" + query;
        }
        return normalized;
    }

    /**
     * Determine the correct URL for lock/unlock operations.
     * <p>
     * All object types (programs, classes, interfaces) are locked at their
     * <b>object URL</b> (without {@code /source/main}). This matches the
     * approach used by all known working ADT client implementations
     * (abap-adt-api, abap-adt-py, adt-mcp-server).
     * </p>
     * <p>
     * <b>Important:</b> Lock/write/unlock must happen within a
     * <b>stateful session</b> (HTTP header {@code X-sap-adt-sessiontype: stateful}).
     * Without stateful sessions, the lock handle is not recognized on
     * subsequent requests and the server returns HTTP 423.
     * </p>
     *
     * @param sourceUrl the full source URL
     * @return the object URL to use for LOCK/UNLOCK operations
     */
    public static String toLockUrl(String sourceUrl) {
        return toObjectUrl(sourceUrl);
    }

    /**
     * Derive the object URL from a source URL by stripping the
     * {@code /source/...} suffix.
     *
     * @param sourceUrl the full source URL
     * @return the object URL (without {@code /source/...})
     */
    public static String toObjectUrl(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isEmpty()) {
            return sourceUrl;
        }
        // Strip query string first
        String path = sourceUrl;
        String query = "";
        int qIdx = path.indexOf('?');
        if (qIdx >= 0) {
            query = path.substring(qIdx);
            path = path.substring(0, qIdx);
        }
        int sourceIdx = path.indexOf("/source/");
        if (sourceIdx >= 0) {
            return path.substring(0, sourceIdx) + query;
        }
        return sourceUrl;
    }

    /**
     * Normalize an Eclipse workspace URI to a standard ADT REST path.
     * <p>
     * Converts URIs like
     * {@code E19_100/.adt/programs/programs/ztest/ztest.asprog}
     * to {@code /sap/bc/adt/programs/programs/ztest}.
     * </p>
     * <p>
     * Standard paths starting with {@code /sap/} pass through unchanged.
     * </p>
     *
     * @param url the raw URL (may be Eclipse workspace URI or standard ADT path)
     * @return the normalized ADT REST path
     */
    private static String normalizeEclipseUri(String url) {
        if (url == null || url.isEmpty() || url.startsWith("/sap/")) {
            return url;
        }
        int adtIdx = url.indexOf("/.adt/");
        if (adtIdx < 0) {
            return url; // not an Eclipse workspace URI
        }

        String rest = url.substring(adtIdx + "/.adt/".length());

        // Separate query string from path
        String query = "";
        int queryIdx = rest.indexOf('?');
        if (queryIdx >= 0) {
            query = rest.substring(queryIdx);
            rest = rest.substring(0, queryIdx);
        }

        // Remove Eclipse file extensions (.asprog, .aclas, .aint, .fugr, .func, .ddls, etc.)
        rest = rest.replaceFirst("\\.[a-zA-Z]+$", "");

        // Remove duplicate trailing path segment (Eclipse pattern: .../ztest/ztest)
        int lastSlash = rest.lastIndexOf('/');
        if (lastSlash > 0) {
            String lastName = rest.substring(lastSlash + 1);
            int prevSlash = rest.lastIndexOf('/', lastSlash - 1);
            if (prevSlash >= 0) {
                String prevName = rest.substring(prevSlash + 1, lastSlash);
                if (prevName.equalsIgnoreCase(lastName)) {
                    rest = rest.substring(0, lastSlash);
                }
            }
        }

        return "/sap/bc/adt/" + rest + query;
    }
}
