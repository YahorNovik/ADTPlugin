package com.sap.ai.assistant.llm;

/**
 * Exception thrown when an LLM provider request fails.
 * Carries the HTTP status code and the raw response body (when available)
 * so that callers can inspect the provider's error details.
 */
public class LlmException extends Exception {

    private static final long serialVersionUID = 1L;

    private final int statusCode;
    private final String responseBody;

    /**
     * Creates an exception with a message only (no HTTP context).
     *
     * @param message a human-readable error description
     */
    public LlmException(String message) {
        super(message);
        this.statusCode = -1;
        this.responseBody = null;
    }

    /**
     * Creates an exception with a message and a root cause.
     *
     * @param message a human-readable error description
     * @param cause   the underlying cause
     */
    public LlmException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
        this.responseBody = null;
    }

    /**
     * Creates an exception that captures the HTTP status code and the raw
     * response body returned by the provider.
     *
     * @param message      a human-readable error description
     * @param statusCode   the HTTP status code (e.g. 400, 401, 429, 500)
     * @param responseBody the raw response body (may be {@code null})
     */
    public LlmException(String message, int statusCode, String responseBody) {
        super(message);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    /**
     * Returns the HTTP status code, or {@code -1} if not available.
     *
     * @return the HTTP status code
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * Returns the raw response body from the provider, or {@code null} if not available.
     *
     * @return the response body
     */
    public String getResponseBody() {
        return responseBody;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("LlmException{message='");
        sb.append(getMessage()).append("'");
        if (statusCode > 0) {
            sb.append(", statusCode=").append(statusCode);
        }
        if (responseBody != null) {
            sb.append(", responseBody='")
              .append(responseBody.length() > 200 ? responseBody.substring(0, 200) + "..." : responseBody)
              .append("'");
        }
        sb.append("}");
        return sb.toString();
    }
}
