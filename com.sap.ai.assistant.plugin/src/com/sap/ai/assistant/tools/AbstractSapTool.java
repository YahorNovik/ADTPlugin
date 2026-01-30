package com.sap.ai.assistant.tools;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

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
}
