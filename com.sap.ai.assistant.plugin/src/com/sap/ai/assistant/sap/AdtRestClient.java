package com.sap.ai.assistant.sap;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Core HTTP client for SAP ADT (ABAP Development Tools) REST APIs.
 * <p>
 * Handles CSRF token management, session cookies, Basic authentication,
 * and automatic retry on 403 (stale CSRF token).
 * </p>
 * <p>
 * Usage:
 * <pre>
 *   AdtRestClient client = new AdtRestClient(
 *       "https://host:44300", "USER", "PASS", "100", "EN", false);
 *   client.login();
 *   HttpResponse&lt;String&gt; resp = client.get("/sap/bc/adt/programs/programs/ztest", "application/xml");
 *   client.logout();
 * </pre>
 * </p>
 */
public class AdtRestClient {

    private static final String CSRF_TOKEN_HEADER = "x-csrf-token";
    private static final String SESSION_TYPE_HEADER = "X-sap-adt-sessiontype";
    private static final String DISCOVERY_PATH = "/sap/bc/adt/core/discovery";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private final String baseUrl;
    private final String username;
    private final String password;
    private final String sapClient;
    private final String language;
    private final HttpClient httpClient;
    private final CookieManager cookieManager;

    private String csrfToken;
    private boolean loggedIn;

    /** When true, login() was pre-authenticated and should only fetch CSRF token if needed. */
    private boolean preAuthenticated;

    /**
     * Create a new ADT REST client.
     *
     * @param baseUrl          SAP system base URL, e.g. "https://host:44300"
     * @param username         SAP user name
     * @param password         SAP password
     * @param sapClient        SAP client number, e.g. "100"
     * @param language         Logon language, e.g. "EN"
     * @param allowInsecureSsl if true, accept all SSL certificates (dev only)
     */
    public AdtRestClient(String baseUrl, String username, String password,
                         String sapClient, String language, boolean allowInsecureSsl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.username = username;
        this.password = password;
        this.sapClient = sapClient;
        this.language = language;
        this.loggedIn = false;

        this.cookieManager = new CookieManager();
        this.cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);

        HttpClient.Builder builder = HttpClient.newBuilder()
                .cookieHandler(this.cookieManager)
                .connectTimeout(Duration.ofSeconds(30))
                .version(HttpClient.Version.HTTP_1_1);
                // Use JVM default ProxySelector — corporate proxies resolve internal hostnames

        if (allowInsecureSsl) {
            try {
                SSLContext sslContext = createTrustAllSslContext();
                builder.sslContext(sslContext);
            } catch (NoSuchAlgorithmException | KeyManagementException e) {
                throw new RuntimeException("Failed to create trust-all SSL context", e);
            }
        }

        this.httpClient = builder.build();
    }

    /**
     * Creates a pre-authenticated ADT REST client using an existing session
     * (cookies and optional CSRF token) obtained from ADT's connection.
     * <p>
     * When a CSRF token is provided, {@link #login()} will skip the discovery
     * request entirely. When only cookies are provided, login() will still
     * fetch a CSRF token but will use the existing session cookies for
     * authentication instead of Basic Auth.
     * </p>
     *
     * @param baseUrl           SAP system base URL
     * @param username          SAP user name
     * @param sapClient         SAP client number
     * @param language          Logon language
     * @param allowInsecureSsl  accept all SSL certificates (dev only)
     * @param existingCookies   pre-authenticated cookie manager from ADT session
     * @param existingCsrfToken existing CSRF token (may be {@code null})
     */
    public AdtRestClient(String baseUrl, String username, String sapClient,
                         String language, boolean allowInsecureSsl,
                         java.net.CookieManager existingCookies, String existingCsrfToken) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.username = username;
        this.password = "";  // not needed for pre-authenticated session
        this.sapClient = sapClient;
        this.language = language;
        this.loggedIn = false;
        this.preAuthenticated = true;

        // Use existing cookies if provided, otherwise create new manager
        if (existingCookies != null) {
            this.cookieManager = existingCookies;
        } else {
            this.cookieManager = new CookieManager();
            this.cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        }

        // Use existing CSRF token if provided
        if (existingCsrfToken != null && !existingCsrfToken.isEmpty()) {
            this.csrfToken = existingCsrfToken;
            this.loggedIn = true;
        }

        HttpClient.Builder builder = HttpClient.newBuilder()
                .cookieHandler(this.cookieManager)
                .connectTimeout(Duration.ofSeconds(30))
                .version(HttpClient.Version.HTTP_1_1);

        if (allowInsecureSsl) {
            try {
                SSLContext sslContext = createTrustAllSslContext();
                builder.sslContext(sslContext);
            } catch (NoSuchAlgorithmException | KeyManagementException e) {
                throw new RuntimeException("Failed to create trust-all SSL context", e);
            }
        }

        this.httpClient = builder.build();
    }

    // ---------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------

    /**
     * Authenticate against the SAP system and fetch the initial CSRF token.
     * Sends GET /sap/bc/adt/core/discovery with Basic auth and
     * {@code x-csrf-token: Fetch}.
     *
     * @throws Exception if the login request fails or returns non-2xx
     */
    public void login() throws Exception {
        // If already logged in from pre-authenticated session, skip login
        if (loggedIn && preAuthenticated) {
            System.out.println("AdtRestClient: using pre-authenticated ADT session for " + baseUrl);
            return;
        }

        // If pre-authenticated but no CSRF token yet, just fetch the token
        // using existing cookies (no Basic Auth needed)
        if (preAuthenticated && csrfToken == null) {
            System.out.println("AdtRestClient: fetching CSRF token using ADT session cookies for " + baseUrl);
            refreshCsrfToken();
            if (csrfToken != null && !csrfToken.isEmpty()) {
                loggedIn = true;
                return;
            }
            // If CSRF fetch failed, fall through to full login
            System.out.println("AdtRestClient: CSRF fetch with cookies failed, falling back to full login");
        }

        String url = buildUrl(DISCOVERY_PATH);
        System.out.println("AdtRestClient: logging in to " + baseUrl + " ...");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", basicAuthHeader())
                .header(CSRF_TOKEN_HEADER, "Fetch")
                .header(SESSION_TYPE_HEADER, "stateful")
                .header("Accept", "application/atomsvc+xml")
                .header("Accept-Language", language)
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (java.net.ConnectException e) {
            throw new java.net.ConnectException(
                    "Cannot connect to SAP system at " + baseUrl
                    + ". Verify the URL is correct and the system is reachable. "
                    + "Original error: " + e.getMessage());
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Login failed with HTTP " + response.statusCode()
                    + ": " + response.body());
        }

        // Extract CSRF token from response headers
        csrfToken = response.headers()
                .firstValue(CSRF_TOKEN_HEADER)
                .orElse(null);

        if (csrfToken == null || csrfToken.isEmpty()) {
            throw new IOException("Login succeeded but no CSRF token was returned");
        }

        loggedIn = true;
    }

    /**
     * Perform a GET request.
     *
     * @param path   ADT path, e.g. "/sap/bc/adt/programs/programs/ztest"
     * @param accept MIME type for the Accept header
     * @return the HTTP response
     * @throws Exception on network or HTTP errors
     */
    public HttpResponse<String> get(String path, String accept) throws Exception {
        String url = buildUrl(path);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", basicAuthHeader())
                .header("Accept", accept)
                .header("Accept-Language", language)
                .header(SESSION_TYPE_HEADER, "stateful")
                .timeout(REQUEST_TIMEOUT)
                .GET();

        if (csrfToken != null) {
            builder.header(CSRF_TOKEN_HEADER, csrfToken);
        }

        return executeWithCsrfRetry(builder);
    }

    /**
     * Perform a POST request.
     *
     * @param path        ADT path
     * @param body        request body (may be empty string for body-less POSTs)
     * @param contentType Content-Type header value
     * @param accept      Accept header value
     * @return the HTTP response
     * @throws Exception on network or HTTP errors
     */
    public HttpResponse<String> post(String path, String body,
                                     String contentType, String accept) throws Exception {
        String url = buildUrl(path);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", basicAuthHeader())
                .header("Content-Type", contentType)
                .header("Accept", accept)
                .header("Accept-Language", language)
                .header(SESSION_TYPE_HEADER, "stateful")
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(body));

        if (csrfToken != null) {
            builder.header(CSRF_TOKEN_HEADER, csrfToken);
        }

        return executeWithCsrfRetry(builder);
    }

    /**
     * Perform a PUT request.
     *
     * @param path        ADT path
     * @param body        request body
     * @param contentType Content-Type header value
     * @return the HTTP response
     * @throws Exception on network or HTTP errors
     */
    public HttpResponse<String> put(String path, String body,
                                    String contentType) throws Exception {
        String url = buildUrl(path);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", basicAuthHeader())
                .header("Content-Type", contentType)
                .header("Accept-Language", language)
                .header(SESSION_TYPE_HEADER, "stateful")
                .timeout(REQUEST_TIMEOUT)
                .PUT(HttpRequest.BodyPublishers.ofString(body));

        if (csrfToken != null) {
            builder.header(CSRF_TOKEN_HEADER, csrfToken);
        }

        return executeWithCsrfRetry(builder);
    }

    /**
     * Perform a PUT request with additional custom headers.
     * Typically used to pass the Lock-Handle header when saving objects.
     *
     * @param path         ADT path
     * @param body         request body
     * @param contentType  Content-Type header value
     * @param extraHeaders additional headers as key-value pairs
     * @return the HTTP response
     * @throws Exception on network or HTTP errors
     */
    public HttpResponse<String> putWithHeaders(String path, String body,
                                               String contentType,
                                               Map<String, String> extraHeaders) throws Exception {
        String url = buildUrl(path);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", basicAuthHeader())
                .header("Content-Type", contentType)
                .header("Accept-Language", language)
                .header(SESSION_TYPE_HEADER, "stateful")
                .timeout(REQUEST_TIMEOUT)
                .PUT(HttpRequest.BodyPublishers.ofString(body));

        if (csrfToken != null) {
            builder.header(CSRF_TOKEN_HEADER, csrfToken);
        }

        if (extraHeaders != null) {
            for (Map.Entry<String, String> entry : extraHeaders.entrySet()) {
                builder.header(entry.getKey(), entry.getValue());
            }
        }

        return executeWithCsrfRetry(builder);
    }

    /**
     * Perform a DELETE request.
     *
     * @param path ADT path
     * @return the HTTP response
     * @throws Exception on network or HTTP errors
     */
    public HttpResponse<String> delete(String path) throws Exception {
        String url = buildUrl(path);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", basicAuthHeader())
                .header("Accept-Language", language)
                .header(SESSION_TYPE_HEADER, "stateful")
                .timeout(REQUEST_TIMEOUT)
                .DELETE();

        if (csrfToken != null) {
            builder.header(CSRF_TOKEN_HEADER, csrfToken);
        }

        return executeWithCsrfRetry(builder);
    }

    /**
     * Logout and clean up resources. Clears the CSRF token and cookies.
     */
    public void logout() {
        csrfToken = null;
        loggedIn = false;
        cookieManager.getCookieStore().removeAll();
    }

    /**
     * Check whether the client has successfully logged in and holds
     * a valid CSRF token.
     *
     * @return true if logged in
     */
    public boolean isLoggedIn() {
        return loggedIn;
    }

    // ---------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------

    /**
     * Build the full URL by appending sap-client and sap-language query parameters.
     * If the path already contains a query string, the parameters are appended with &amp;.
     * <p>
     * Automatically normalises Eclipse ADT workspace URIs
     * (e.g. {@code /ProjectName/.adt/programs/programs/ztest/ztest.asprog})
     * to standard REST API paths ({@code /sap/bc/adt/programs/programs/ztest}).
     * </p>
     */
    private String buildUrl(String path) {
        String fullPath = path.startsWith("/") ? path : "/" + path;
        fullPath = normalizeAdtPath(fullPath);
        String separator = fullPath.contains("?") ? "&" : "?";
        return baseUrl + fullPath + separator
                + "sap-client=" + sapClient
                + "&sap-language=" + language;
    }

    /**
     * Normalise an ADT path that may be in Eclipse workspace URI format.
     * <ul>
     *   <li>Converts {@code /<project>/.adt/...} to {@code /sap/bc/adt/...}</li>
     *   <li>Removes Eclipse-specific file extensions ({@code .asprog}, {@code .clas}, etc.)</li>
     *   <li>Removes duplicate trailing path segment ({@code .../name/name} → {@code .../name})</li>
     * </ul>
     * Standard paths starting with {@code /sap/} pass through unchanged.
     */
    static String normalizeAdtPath(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        // Already a standard ADT REST path
        if (path.startsWith("/sap/")) {
            return path;
        }

        // Convert Eclipse workspace URI: /<project>/.adt/... → /sap/bc/adt/...
        int adtIdx = path.indexOf("/.adt/");
        if (adtIdx < 0) {
            return path; // not an Eclipse workspace URI
        }

        String rest = path.substring(adtIdx + "/.adt/".length());

        // Separate query string from path
        String query = "";
        int queryIdx = rest.indexOf('?');
        if (queryIdx >= 0) {
            query = rest.substring(queryIdx);
            rest = rest.substring(0, queryIdx);
        }

        // Remove Eclipse file extensions (.asprog, .clas, .intf, .fugr, .func, .ddls, etc.)
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

    /**
     * Return the Basic authorization header value.
     */
    private String basicAuthHeader() {
        String credentials = username + ":" + password;
        String encoded = Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    /**
     * Execute the request built by the given builder. If the server responds
     * with HTTP 403 (typically due to a stale CSRF token), re-fetch the
     * CSRF token and retry the request once.
     *
     * @param requestBuilder pre-configured request builder (method already set)
     * @return the HTTP response
     * @throws Exception if both attempts fail or on I/O errors
     */
    private HttpResponse<String> executeWithCsrfRetry(HttpRequest.Builder requestBuilder)
            throws Exception {
        HttpRequest request = requestBuilder.build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 403) {
            // CSRF token may have expired -- re-fetch and retry
            refreshCsrfToken();

            // Rebuild the request with the new token.
            // HttpRequest is immutable so we must reconstruct from the builder.
            requestBuilder.header(CSRF_TOKEN_HEADER, csrfToken);
            HttpRequest retryRequest = requestBuilder.build();
            response = httpClient.send(retryRequest, HttpResponse.BodyHandlers.ofString());
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode()
                    + " " + request.method() + " " + request.uri()
                    + " -- " + response.body());
        }

        return response;
    }

    /**
     * Re-fetch a fresh CSRF token from the discovery endpoint.
     */
    private void refreshCsrfToken() throws Exception {
        String url = buildUrl(DISCOVERY_PATH);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", basicAuthHeader())
                .header(CSRF_TOKEN_HEADER, "Fetch")
                .header(SESSION_TYPE_HEADER, "stateful")
                .header("Accept", "application/atomsvc+xml")
                .header("Accept-Language", language)
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        String newToken = response.headers()
                .firstValue(CSRF_TOKEN_HEADER)
                .orElse(null);

        if (newToken != null && !newToken.isEmpty()) {
            csrfToken = newToken;
        }
    }

    /**
     * Create an SSLContext that accepts all certificates.
     * Only for development/testing -- never use in production.
     */
    private static SSLContext createTrustAllSslContext()
            throws NoSuchAlgorithmException, KeyManagementException {
        TrustManager[] trustAll = new TrustManager[]{
            new X509TrustManager() {
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }

                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    // trust all
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    // trust all
                }
            }
        };

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, trustAll, new SecureRandom());
        return ctx;
    }
}
