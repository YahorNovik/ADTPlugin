package com.sap.ai.assistant.mcp;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * Configuration for a single MCP (Model Context Protocol) server endpoint.
 */
public class McpServerConfig {

    private String name;
    private String url;
    private boolean enabled;

    public McpServerConfig() {
    }

    public McpServerConfig(String name, String url, boolean enabled) {
        this.name = name;
        this.url = url;
        this.enabled = enabled;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    // -- Serialization helpers -----------------------------------------------

    private static final Gson GSON = new Gson();

    /**
     * Deserializes a JSON array string into a list of server configs.
     */
    public static List<McpServerConfig> fromJson(String json) {
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            List<McpServerConfig> list = GSON.fromJson(json,
                    new TypeToken<List<McpServerConfig>>() {}.getType());
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            System.err.println("MCP: Failed to parse server config: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Serializes a list of server configs to a JSON array string.
     */
    public static String toJson(List<McpServerConfig> configs) {
        return GSON.toJson(configs);
    }

    @Override
    public String toString() {
        return "McpServerConfig{name='" + name + "', url='" + url + "', enabled=" + enabled + "}";
    }
}
