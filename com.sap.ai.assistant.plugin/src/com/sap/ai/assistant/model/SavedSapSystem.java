package com.sap.ai.assistant.model;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * Persisted SAP system connection details (without password).
 * <p>
 * Stored as a JSON array in the Eclipse preference store. Passwords
 * are intentionally excluded — the user is prompted at connection time.
 * </p>
 */
public class SavedSapSystem {

    private String host;
    private int port;
    private String client;
    private String user;
    private boolean useSsl;

    public SavedSapSystem() {
    }

    public SavedSapSystem(String host, int port, String client, String user, boolean useSsl) {
        this.host = sanitizeHost(host);
        this.port = port;
        this.client = client;
        this.user = user;
        this.useSsl = useSsl;
    }

    /**
     * Returns a display name like {@code "host:44300 [100]"}.
     */
    public String getDisplayName() {
        return host + ":" + port + " [" + client + "]";
    }

    /**
     * Converts this saved system into a {@link SapSystemConnection} with an
     * empty password (the caller must prompt for it).
     */
    public SapSystemConnection toConnection() {
        return new SapSystemConnection(getDisplayName(), host, port, client, user, "", useSsl);
    }

    /**
     * Creates a {@code SavedSapSystem} from a live connection, discarding the password.
     */
    public static SavedSapSystem fromConnection(SapSystemConnection conn) {
        return new SavedSapSystem(
                conn.getHost(), conn.getPort(), conn.getClient(),
                conn.getUser(), conn.isUseSsl());
    }

    // -- Getters / Setters ---------------------------------------------------

    public String getHost() {
        return sanitizeHost(host);
    }

    public void setHost(String host) {
        this.host = sanitizeHost(host);
    }

    /**
     * Strip any protocol prefix and trailing path from a hostname.
     */
    private static String sanitizeHost(String h) {
        if (h == null) return h;
        if (h.startsWith("http://"))  h = h.substring(7);
        if (h.startsWith("https://")) h = h.substring(8);
        int slashIdx = h.indexOf('/');
        if (slashIdx >= 0) h = h.substring(0, slashIdx);
        // Strip port if embedded (host:port) — port is stored separately
        int colonIdx = h.lastIndexOf(':');
        if (colonIdx >= 0) {
            String maybePart = h.substring(colonIdx + 1);
            try {
                Integer.parseInt(maybePart);
                h = h.substring(0, colonIdx);
            } catch (NumberFormatException ignored) {
                // Not a port number, keep as-is
            }
        }
        return h;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getClient() {
        return client;
    }

    public void setClient(String client) {
        this.client = client;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public boolean isUseSsl() {
        return useSsl;
    }

    public void setUseSsl(boolean useSsl) {
        this.useSsl = useSsl;
    }

    // -- JSON serialization --------------------------------------------------

    private static final Gson GSON = new Gson();

    public static List<SavedSapSystem> fromJson(String json) {
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            List<SavedSapSystem> list = GSON.fromJson(json,
                    new TypeToken<List<SavedSapSystem>>() {}.getType());
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            System.err.println("SavedSapSystem: failed to parse JSON: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public static String toJson(List<SavedSapSystem> systems) {
        return GSON.toJson(systems);
    }

    @Override
    public String toString() {
        return "SavedSapSystem{host='" + host + "', port=" + port
                + ", client='" + client + "', user='" + user
                + "', useSsl=" + useSsl + "}";
    }
}
