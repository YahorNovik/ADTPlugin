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

        // Proxy: use JVM default ProxySelector which picks up system/OS proxy settings.
        // In corporate environments (VPN), the system proxy handles both internal
        // hostname resolution and external API routing.
        // Only override if Java system properties explicitly set a proxy.
        try {
            String httpsProxyHost = System.getProperty("https.proxyHost");
            String httpsProxyPort = System.getProperty("https.proxyPort", "8080");
            String httpProxyHost = System.getProperty("http.proxyHost");
            String httpProxyPort = System.getProperty("http.proxyPort", "8080");

            String proxyHost = httpsProxyHost != null ? httpsProxyHost : httpProxyHost;
            String proxyPort = httpsProxyHost != null ? httpsProxyPort : httpProxyPort;

            if (proxyHost != null && !proxyHost.isEmpty()) {
                int port = Integer.parseInt(proxyPort);
                builder.proxy(ProxySelector.of(new InetSocketAddress(proxyHost, port)));
                System.out.println("LLM HttpClient using explicit proxy: " + proxyHost + ":" + port);
            } else {
                // Use JVM default — don't call builder.proxy() at all
                System.out.println("LLM HttpClient using JVM default proxy settings");
            }
        } catch (Exception e) {
            System.err.println("LLM HttpClient proxy setup note: " + e.getMessage());
            // Fall back to JVM default (don't set proxy at all)
        }

        return builder.build();
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
