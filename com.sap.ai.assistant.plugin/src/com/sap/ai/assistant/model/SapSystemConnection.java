package com.sap.ai.assistant.model;

/**
 * Holds connection details for an SAP system, including host, port, client,
 * credentials, and SSL configuration.
 */
public class SapSystemConnection {

    private String projectName;
    private String host;
    private int port;
    private String client;
    private String user;
    private String password;
    private boolean useSsl;

    /**
     * Creates a new SAP system connection.
     *
     * @param projectName the Eclipse project name associated with this connection
     * @param host        the SAP system hostname or IP address
     * @param port        the HTTP(S) port
     * @param client      the SAP client number (e.g. "100")
     * @param user        the SAP user name
     * @param password    the SAP user password
     * @param useSsl      {@code true} to use HTTPS, {@code false} for HTTP
     */
    public SapSystemConnection(String projectName, String host, int port, String client,
                               String user, String password, boolean useSsl) {
        this.projectName = projectName;
        this.host = host;
        this.port = port;
        this.client = client;
        this.user = user;
        this.password = password;
        this.useSsl = useSsl;
    }

    /**
     * Builds the full base URL for this SAP system (e.g. {@code https://host:port}).
     *
     * @return the base URL string
     */
    public String getBaseUrl() {
        String scheme = useSsl ? "https" : "http";
        // Defensive: strip any protocol prefix that may have been stored in host
        String cleanHost = host;
        if (cleanHost.startsWith("http://")) {
            cleanHost = cleanHost.substring(7);
        } else if (cleanHost.startsWith("https://")) {
            cleanHost = cleanHost.substring(8);
        }
        // Strip trailing slashes/paths
        int slashIdx = cleanHost.indexOf('/');
        if (slashIdx >= 0) {
            cleanHost = cleanHost.substring(0, slashIdx);
        }
        return scheme + "://" + cleanHost + ":" + port;
    }

    // -- Getters / Setters -------------------------------------------------------

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isUseSsl() {
        return useSsl;
    }

    public void setUseSsl(boolean useSsl) {
        this.useSsl = useSsl;
    }

    @Override
    public String toString() {
        return "SapSystemConnection{projectName='" + projectName + "'"
                + ", host='" + host + "'"
                + ", port=" + port
                + ", client='" + client + "'"
                + ", user='" + user + "'"
                + ", useSsl=" + useSsl
                + "}";
    }
}
