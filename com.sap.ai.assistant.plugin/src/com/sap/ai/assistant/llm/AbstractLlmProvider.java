package com.sap.ai.assistant.llm;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.sap.ai.assistant.model.LlmProviderConfig;

/**
 * Base class for LLM providers that communicate with their API over HTTP/JSON.
 * Provides a shared {@link HttpClient}, common request helpers, and hooks for
 * provider-specific authentication headers.
 * <p>
 * Supports proxy configuration via Java system properties
 * ({@code https.proxyHost} / {@code https.proxyPort}) or Eclipse proxy settings.
 * </p>
 */
public abstract class AbstractLlmProvider implements LlmProvider {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(120);

    protected final HttpClient httpClient;
    protected final LlmProviderConfig config;

    protected AbstractLlmProvider(LlmProviderConfig config) {
        this.config = config;
        this.httpClient = buildHttpClient();
    }

    private HttpClient buildHttpClient() {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL);

        // Only configure a proxy if one is explicitly set.
        // Do NOT call builder.proxy() at all when no proxy is needed —
        // Java's default HttpClient uses a direct connection unless told otherwise.
        try {
            // 1. Check Java system properties
            String httpsProxyHost = System.getProperty("https.proxyHost");
            String httpsProxyPort = System.getProperty("https.proxyPort", "8080");
            String httpProxyHost = System.getProperty("http.proxyHost");
            String httpProxyPort = System.getProperty("http.proxyPort", "8080");

            String proxyHost = httpsProxyHost != null ? httpsProxyHost : httpProxyHost;
            String proxyPort = httpsProxyHost != null ? httpsProxyPort : httpProxyPort;

            if (proxyHost != null && !proxyHost.isEmpty()) {
                int port = Integer.parseInt(proxyPort);
                builder.proxy(ProxySelector.of(new InetSocketAddress(proxyHost, port)));
                System.out.println("LLM HttpClient using proxy: " + proxyHost + ":" + port);
            } else {
                // 2. Try Eclipse proxy settings via IProxyService
                if (!configureEclipseProxy(builder)) {
                    // No proxy found — force DIRECT connection.
                    // We MUST explicitly set this because HttpClient's default
                    // uses ProxySelector.getDefault(), which on macOS/Eclipse JVM
                    // may pick up OS-level auto-proxy settings and route through
                    // a non-existent proxy, causing ConnectException.
                    builder.proxy(ProxySelector.of(null));
                    System.out.println("LLM HttpClient using direct connection (no proxy)");
                }
            }
        } catch (Exception e) {
            System.err.println("LLM HttpClient proxy setup failed: " + e.getMessage());
            // Fall back to explicit direct connection
            builder.proxy(ProxySelector.of(null));
        }

        return builder.build();
    }

    /**
     * Attempts to configure proxy from Eclipse's IProxyService.
     *
     * @return {@code true} if an Eclipse proxy was found and configured
     */
    private boolean configureEclipseProxy(HttpClient.Builder builder) {
        try {
            // Use reflection to avoid hard dependency on org.eclipse.core.net
            org.osgi.framework.Bundle netBundle =
                    org.eclipse.core.runtime.Platform.getBundle("org.eclipse.core.net");
            if (netBundle == null) {
                return false;
            }

            org.osgi.framework.BundleContext ctx = netBundle.getBundleContext();
            if (ctx == null) {
                return false;
            }

            Class<?> proxyServiceClass = Class.forName("org.eclipse.core.net.proxy.IProxyService");
            org.osgi.framework.ServiceReference<?> serviceRef = ctx.getServiceReference(proxyServiceClass);
            if (serviceRef == null) {
                return false;
            }

            Object proxyService = ctx.getService(serviceRef);
            if (proxyService == null) {
                return false;
            }

            try {
                // Check if proxies are enabled
                Boolean enabled = (Boolean) proxyServiceClass
                        .getMethod("isProxiesEnabled").invoke(proxyService);
                if (enabled == null || !enabled) {
                    return false;
                }

                // Get proxy data for HTTPS
                Object[] proxyDataArray = (Object[]) proxyServiceClass
                        .getMethod("select", URI.class)
                        .invoke(proxyService, URI.create("https://api.anthropic.com"));

                if (proxyDataArray != null && proxyDataArray.length > 0) {
                    Object proxyData = proxyDataArray[0];
                    Class<?> proxyDataClass = proxyData.getClass();
                    String type = (String) proxyDataClass.getMethod("getType").invoke(proxyData);

                    if ("HTTP".equals(type) || "HTTPS".equals(type)) {
                        String host = (String) proxyDataClass.getMethod("getHost").invoke(proxyData);
                        int port = (int) proxyDataClass.getMethod("getPort").invoke(proxyData);

                        if (host != null && !host.isEmpty() && port > 0) {
                            builder.proxy(ProxySelector.of(new InetSocketAddress(host, port)));
                            System.out.println("LLM HttpClient using Eclipse proxy: " + host + ":" + port);
                            return true;
                        }
                    }
                }
            } finally {
                ctx.ungetService(serviceRef);
            }
        } catch (Exception e) {
            // Eclipse proxy service not available — that's fine
            System.out.println("LLM HttpClient: Eclipse proxy check skipped: " + e.getMessage());
        }
        return false;
    }

    // -- HTTP helpers -------------------------------------------------------

    protected HttpResponse<String> sendRequest(String url, String body) throws LlmException {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));

            addAuthHeaders(builder);

            HttpRequest request = builder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String errorMsg = parseErrorMessage(response.body());
                throw new LlmException(
                        getProviderId() + " API error (" + response.statusCode() + "): " + errorMsg,
                        response.statusCode(),
                        response.body());
            }

            return response;
        } catch (LlmException e) {
            throw e;
        } catch (java.net.ConnectException e) {
            throw new LlmException(
                    "Cannot connect to " + getProviderId() + " API at " + url
                    + ". Check your network connection and proxy settings. "
                    + "(Eclipse: Window > Preferences > General > Network Connections)",
                    e);
        } catch (javax.net.ssl.SSLException e) {
            throw new LlmException(
                    "SSL error connecting to " + getProviderId() + " API at " + url
                    + ". If behind a corporate proxy, configure Eclipse proxy settings. "
                    + "Error: " + e.getMessage(),
                    e);
        } catch (IOException e) {
            throw new LlmException(
                    "Network error calling " + getProviderId() + " API at " + url
                    + ": " + e.getMessage(),
                    e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("Request to " + getProviderId() + " API was interrupted", e);
        }
    }

    protected void addAuthHeaders(HttpRequest.Builder builder) {
        String name = getAuthHeaderName();
        if (name != null) {
            builder.header(name, getAuthHeaderValue());
        }
    }

    protected abstract String getAuthHeaderName();
    protected abstract String getAuthHeaderValue();

    protected String parseErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isEmpty()) {
            return "No response body";
        }
        try {
            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(responseBody).getAsJsonObject();
            if (json.has("error")) {
                com.google.gson.JsonElement errorEl = json.get("error");
                if (errorEl.isJsonObject()) {
                    com.google.gson.JsonObject errorObj = errorEl.getAsJsonObject();
                    if (errorObj.has("message")) {
                        return errorObj.get("message").getAsString();
                    }
                }
                if (errorEl.isJsonPrimitive()) {
                    return errorEl.getAsString();
                }
            }
            if (json.has("message")) {
                return json.get("message").getAsString();
            }
        } catch (Exception ignored) {
        }
        return responseBody.length() > 500 ? responseBody.substring(0, 500) + "..." : responseBody;
    }
}
