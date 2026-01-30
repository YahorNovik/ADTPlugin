package com.sap.ai.assistant.mcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * MCP (Model Context Protocol) client using the Streamable HTTP transport.
 * <p>
 * Communicates with an MCP server via JSON-RPC over HTTP POST. The server
 * returns responses as Server-Sent Events (SSE). Session state is maintained
 * via the {@code mcp-session-id} header.
 * </p>
 */
public class McpClient {

    private static final String MCP_PROTOCOL_VERSION = "2024-11-05";
    private static final String CLIENT_NAME = "sap-ai-assistant-eclipse";
    private static final String CLIENT_VERSION = "1.0.0";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(120);

    private final HttpClient httpClient;
    private final String serverUrl;

    private String sessionId;
    private final AtomicInteger requestIdCounter = new AtomicInteger(1);

    /**
     * Creates an MCP client for the given server URL.
     *
     * @param serverUrl the MCP server endpoint (e.g. "https://example.com/mcp")
     */
    public McpClient(String serverUrl) {
        this.serverUrl = serverUrl;
        this.httpClient = buildHttpClient();
    }

    // -- Public API -----------------------------------------------------------

    /**
     * Connects to the MCP server by performing the initialize handshake.
     *
     * @throws McpException if connection fails
     */
    public void connect() throws McpException {
        // Step 1: Initialize
        JsonObject params = new JsonObject();
        params.addProperty("protocolVersion", MCP_PROTOCOL_VERSION);
        params.add("capabilities", new JsonObject());

        JsonObject clientInfo = new JsonObject();
        clientInfo.addProperty("name", CLIENT_NAME);
        clientInfo.addProperty("version", CLIENT_VERSION);
        params.add("clientInfo", clientInfo);

        HttpResponse<String> response = sendRpc("initialize", params, CONNECT_TIMEOUT, true);

        // Extract session ID from response headers
        sessionId = response.headers()
                .firstValue("mcp-session-id")
                .orElse(null);

        if (sessionId == null) {
            // Try case-insensitive lookup
            sessionId = response.headers().map().entrySet().stream()
                    .filter(e -> e.getKey().equalsIgnoreCase("mcp-session-id"))
                    .flatMap(e -> e.getValue().stream())
                    .findFirst()
                    .orElse(null);
        }

        // Step 2: Send initialized notification (no response expected)
        JsonObject notifBody = new JsonObject();
        notifBody.addProperty("jsonrpc", "2.0");
        notifBody.addProperty("method", "notifications/initialized");
        notifBody.add("params", new JsonObject());

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl))
                    .timeout(CONNECT_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/event-stream")
                    .header("mcp-session-id", sessionId != null ? sessionId : "")
                    .POST(HttpRequest.BodyPublishers.ofString(notifBody.toString()))
                    .build();
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            // Notification failures are non-fatal
            System.err.println("MCP: initialized notification failed: " + e.getMessage());
        }

        System.out.println("MCP: Connected to " + serverUrl
                + " (session=" + (sessionId != null ? sessionId : "none") + ")");
    }

    /**
     * Lists all tools available on the MCP server.
     *
     * @return list of tool definitions as JSON objects
     * @throws McpException if the request fails
     */
    public List<JsonObject> listTools() throws McpException {
        HttpResponse<String> response = sendRpc("tools/list", new JsonObject(), CONNECT_TIMEOUT, false);
        JsonObject result = parseResult(response.body());

        List<JsonObject> tools = new ArrayList<>();
        if (result.has("tools") && result.get("tools").isJsonArray()) {
            JsonArray toolsArray = result.getAsJsonArray("tools");
            for (JsonElement el : toolsArray) {
                if (el.isJsonObject()) {
                    tools.add(el.getAsJsonObject());
                }
            }
        }
        return tools;
    }

    /**
     * Calls a tool on the MCP server.
     *
     * @param toolName  the tool name (as returned by listTools)
     * @param arguments the tool arguments
     * @return the tool result content as a string
     * @throws McpException if the call fails
     */
    public String callTool(String toolName, JsonObject arguments) throws McpException {
        JsonObject params = new JsonObject();
        params.addProperty("name", toolName);
        params.add("arguments", arguments);

        HttpResponse<String> response = sendRpc("tools/call", params, CALL_TIMEOUT, false);
        JsonObject result = parseResult(response.body());

        // Extract text content from result
        if (result.has("content") && result.get("content").isJsonArray()) {
            JsonArray content = result.getAsJsonArray("content");
            StringBuilder sb = new StringBuilder();
            for (JsonElement el : content) {
                if (el.isJsonObject()) {
                    JsonObject block = el.getAsJsonObject();
                    if (block.has("text")) {
                        if (sb.length() > 0) sb.append("\n");
                        sb.append(block.get("text").getAsString());
                    }
                }
            }
            return sb.toString();
        }

        return result.toString();
    }

    /**
     * Disconnects from the MCP server (cleans up session state).
     */
    public void disconnect() {
        sessionId = null;
        System.out.println("MCP: Disconnected from " + serverUrl);
    }

    /**
     * Returns the server URL this client is connected to.
     */
    public String getServerUrl() {
        return serverUrl;
    }

    /**
     * Returns whether the client has an active session.
     */
    public boolean isConnected() {
        return sessionId != null;
    }

    // -- Internal helpers -----------------------------------------------------

    /**
     * Sends a JSON-RPC request to the MCP server.
     */
    private HttpResponse<String> sendRpc(String method, JsonObject params,
            Duration timeout, boolean captureHeaders) throws McpException {
        int id = requestIdCounter.getAndIncrement();

        JsonObject body = new JsonObject();
        body.addProperty("jsonrpc", "2.0");
        body.addProperty("id", id);
        body.addProperty("method", method);
        body.add("params", params);

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()));

            if (sessionId != null) {
                builder.header("mcp-session-id", sessionId);
            }

            HttpRequest request = builder.build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new McpException("MCP server returned HTTP " + response.statusCode()
                        + " for " + method + ": " + response.body());
            }

            return response;

        } catch (McpException e) {
            throw e;
        } catch (java.net.ConnectException e) {
            throw new McpException(
                    "Cannot connect to MCP server at " + serverUrl
                    + ". Check your network connection and proxy settings.", e);
        } catch (IOException e) {
            throw new McpException(
                    "Network error calling MCP server at " + serverUrl
                    + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new McpException("MCP request was interrupted", e);
        }
    }

    /**
     * Parses the JSON-RPC result from an SSE or plain JSON response.
     * The MCP Streamable HTTP transport returns SSE with {@code data:} lines.
     */
    private JsonObject parseResult(String responseBody) throws McpException {
        if (responseBody == null || responseBody.isEmpty()) {
            throw new McpException("Empty response from MCP server");
        }

        // Try to extract data from SSE format (lines starting with "data:")
        String jsonPayload = null;
        String[] lines = responseBody.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("data:")) {
                jsonPayload = trimmed.substring(5).trim();
            }
        }

        // Fall back to treating entire body as JSON
        if (jsonPayload == null) {
            jsonPayload = responseBody.trim();
        }

        try {
            JsonObject rpcResponse = JsonParser.parseString(jsonPayload).getAsJsonObject();

            // Check for JSON-RPC error
            if (rpcResponse.has("error")) {
                JsonObject error = rpcResponse.getAsJsonObject("error");
                String message = error.has("message") ? error.get("message").getAsString() : "Unknown MCP error";
                throw new McpException("MCP error: " + message);
            }

            if (rpcResponse.has("result")) {
                return rpcResponse.getAsJsonObject("result");
            }

            throw new McpException("MCP response missing 'result' field: " + jsonPayload);

        } catch (McpException e) {
            throw e;
        } catch (Exception e) {
            throw new McpException("Failed to parse MCP response: " + e.getMessage(), e);
        }
    }

    /**
     * Builds the HttpClient with proxy support, following the same pattern
     * as AbstractLlmProvider.
     */
    private HttpClient buildHttpClient() {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL);

        try {
            // Check Java system properties for proxy
            String httpsProxyHost = System.getProperty("https.proxyHost");
            String httpsProxyPort = System.getProperty("https.proxyPort", "8080");
            String httpProxyHost = System.getProperty("http.proxyHost");
            String httpProxyPort = System.getProperty("http.proxyPort", "8080");

            String proxyHost = httpsProxyHost != null ? httpsProxyHost : httpProxyHost;
            String proxyPort = httpsProxyHost != null ? httpsProxyPort : httpProxyPort;

            if (proxyHost != null && !proxyHost.isEmpty()) {
                int port = Integer.parseInt(proxyPort);
                builder.proxy(ProxySelector.of(new InetSocketAddress(proxyHost, port)));
                System.out.println("MCP HttpClient using proxy: " + proxyHost + ":" + port);
            } else {
                // Try Eclipse proxy settings
                if (!configureEclipseProxy(builder)) {
                    System.out.println("MCP HttpClient using direct connection (no proxy)");
                }
            }
        } catch (Exception e) {
            System.err.println("MCP HttpClient proxy setup failed: " + e.getMessage());
        }

        return builder.build();
    }

    /**
     * Attempts to configure proxy from Eclipse's IProxyService.
     */
    private boolean configureEclipseProxy(HttpClient.Builder builder) {
        try {
            org.osgi.framework.Bundle netBundle =
                    org.eclipse.core.runtime.Platform.getBundle("org.eclipse.core.net");
            if (netBundle == null) return false;

            org.osgi.framework.BundleContext ctx = netBundle.getBundleContext();
            if (ctx == null) return false;

            Class<?> proxyServiceClass = Class.forName("org.eclipse.core.net.proxy.IProxyService");
            org.osgi.framework.ServiceReference<?> serviceRef = ctx.getServiceReference(proxyServiceClass);
            if (serviceRef == null) return false;

            Object proxyService = ctx.getService(serviceRef);
            if (proxyService == null) return false;

            try {
                Boolean enabled = (Boolean) proxyServiceClass
                        .getMethod("isProxiesEnabled").invoke(proxyService);
                if (enabled == null || !enabled) return false;

                Object[] proxyDataArray = (Object[]) proxyServiceClass
                        .getMethod("select", URI.class)
                        .invoke(proxyService, URI.create(serverUrl));

                if (proxyDataArray != null && proxyDataArray.length > 0) {
                    Object proxyData = proxyDataArray[0];
                    Class<?> proxyDataClass = proxyData.getClass();
                    String type = (String) proxyDataClass.getMethod("getType").invoke(proxyData);

                    if ("HTTP".equals(type) || "HTTPS".equals(type)) {
                        String host = (String) proxyDataClass.getMethod("getHost").invoke(proxyData);
                        int port = (int) proxyDataClass.getMethod("getPort").invoke(proxyData);

                        if (host != null && !host.isEmpty() && port > 0) {
                            builder.proxy(ProxySelector.of(new InetSocketAddress(host, port)));
                            System.out.println("MCP HttpClient using Eclipse proxy: " + host + ":" + port);
                            return true;
                        }
                    }
                }
            } finally {
                ctx.ungetService(serviceRef);
            }
        } catch (Exception e) {
            // Eclipse proxy service not available
        }
        return false;
    }
}
