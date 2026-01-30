package com.sap.ai.assistant.llm;

import java.io.IOException;
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
 */
public abstract class AbstractLlmProvider implements LlmProvider {

    /** Maximum time to wait for an HTTP response (120 seconds). */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(120);

    protected final HttpClient httpClient;
    protected final LlmProviderConfig config;

    /**
     * Creates a new provider with the given configuration.
     *
     * @param config the provider configuration (API key, model, base URL, etc.)
     */
    protected AbstractLlmProvider(LlmProviderConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    // -- HTTP helpers -------------------------------------------------------

    /**
     * Sends a POST request with a JSON body to the given URL.
     * The request automatically includes {@code Content-Type: application/json}
     * and the provider-specific authentication header(s).
     *
     * @param url  the fully-qualified endpoint URL
     * @param body the JSON request body
     * @return the HTTP response
     * @throws LlmException if a network error occurs or the response indicates failure
     */
    protected HttpResponse<String> sendRequest(String url, String body) throws LlmException {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));

            // Let subclasses add authentication headers
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
        } catch (IOException e) {
            throw new LlmException("Network error calling " + getProviderId() + " API: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("Request to " + getProviderId() + " API was interrupted", e);
        }
    }

    /**
     * Adds provider-specific authentication headers to the request.
     * The default implementation adds a single header using
     * {@link #getAuthHeaderName()} / {@link #getAuthHeaderValue()}.
     * Subclasses may override this to add multiple headers or skip it entirely.
     *
     * @param builder the HTTP request builder
     */
    protected void addAuthHeaders(HttpRequest.Builder builder) {
        String name = getAuthHeaderName();
        if (name != null) {
            builder.header(name, getAuthHeaderValue());
        }
    }

    /**
     * Returns the name of the authentication header (e.g. "Authorization", "x-api-key").
     * Return {@code null} if no auth header is needed (e.g. API key in URL).
     *
     * @return the header name, or {@code null}
     */
    protected abstract String getAuthHeaderName();

    /**
     * Returns the value for the authentication header.
     *
     * @return the header value
     */
    protected abstract String getAuthHeaderValue();

    /**
     * Attempts to extract a human-readable error message from the provider's
     * JSON error response body.  Falls back to the raw body if parsing fails.
     *
     * @param responseBody the raw response body
     * @return the extracted or raw error message
     */
    protected String parseErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isEmpty()) {
            return "No response body";
        }
        try {
            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(responseBody).getAsJsonObject();
            // Anthropic format: { "error": { "message": "..." } }
            if (json.has("error")) {
                com.google.gson.JsonElement errorEl = json.get("error");
                if (errorEl.isJsonObject()) {
                    com.google.gson.JsonObject errorObj = errorEl.getAsJsonObject();
                    if (errorObj.has("message")) {
                        return errorObj.get("message").getAsString();
                    }
                }
                // OpenAI may also use { "error": { "message": "..." } }
                if (errorEl.isJsonPrimitive()) {
                    return errorEl.getAsString();
                }
            }
            // Google format: { "error": { "message": "...", "status": "..." } }
            // Already handled above
            // Mistral / other: { "message": "..." }
            if (json.has("message")) {
                return json.get("message").getAsString();
            }
        } catch (Exception ignored) {
            // Fall through to raw body
        }
        return responseBody.length() > 500 ? responseBody.substring(0, 500) + "..." : responseBody;
    }
}
