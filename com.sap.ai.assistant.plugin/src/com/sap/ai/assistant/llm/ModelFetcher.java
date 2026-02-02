package com.sap.ai.assistant.llm;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sap.ai.assistant.model.LlmProviderConfig;

/**
 * Fetches the list of available models from an LLM provider's API.
 * Each provider exposes a models endpoint that returns the models
 * accessible with the given API key.
 */
public class ModelFetcher {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private ModelFetcher() {}

    /**
     * Fetches available chat model IDs from the given provider.
     *
     * @param provider the LLM provider
     * @param apiKey   the API key for authentication
     * @param baseUrl  the base URL (used for CUSTOM provider; ignored for built-in providers)
     * @return sorted list of model ID strings suitable for chat completions
     * @throws LlmException if the request fails or the response cannot be parsed
     */
    public static List<String> fetchModels(LlmProviderConfig.Provider provider,
                                            String apiKey, String baseUrl) throws LlmException {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new LlmException("API key is required to fetch models");
        }

        switch (provider) {
            case ANTHROPIC:
                return fetchAnthropicModels(apiKey, provider.getDefaultBaseUrl());
            case OPENAI:
                return fetchOpenAiModels(apiKey, provider.getDefaultBaseUrl());
            case GOOGLE:
                return fetchGeminiModels(apiKey, provider.getDefaultBaseUrl());
            case MISTRAL:
                return fetchMistralModels(apiKey, provider.getDefaultBaseUrl());
            case CUSTOM:
                return fetchOpenAiModels(apiKey,
                        baseUrl != null && !baseUrl.isEmpty() ? baseUrl : "");
            default:
                return Collections.emptyList();
        }
    }

    // -- Anthropic: GET /v1/models -------------------------------------------

    private static List<String> fetchAnthropicModels(String apiKey, String baseUrl) throws LlmException {
        List<String> models = new ArrayList<>();
        String afterId = null;

        // Paginate through all results
        while (true) {
            StringBuilder url = new StringBuilder(baseUrl).append("/v1/models?limit=100");
            if (afterId != null) {
                url.append("&after_id=").append(afterId);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url.toString()))
                    .timeout(TIMEOUT)
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .GET()
                    .build();

            String body = executeRequest(request);
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            JsonArray data = json.getAsJsonArray("data");

            if (data != null) {
                for (JsonElement el : data) {
                    JsonObject model = el.getAsJsonObject();
                    if (model.has("id")) {
                        models.add(model.get("id").getAsString());
                    }
                }
            }

            boolean hasMore = json.has("has_more") && json.get("has_more").getAsBoolean();
            if (hasMore && json.has("last_id")) {
                afterId = json.get("last_id").getAsString();
            } else {
                break;
            }
        }

        return models;
    }

    // -- OpenAI: GET /v1/models ----------------------------------------------

    private static List<String> fetchOpenAiModels(String apiKey, String baseUrl) throws LlmException {
        String url = baseUrl + "/v1/models";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + apiKey)
                .GET()
                .build();

        String body = executeRequest(request);
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        JsonArray data = json.getAsJsonArray("data");

        List<String> models = new ArrayList<>();
        if (data != null) {
            for (JsonElement el : data) {
                JsonObject model = el.getAsJsonObject();
                String id = model.get("id").getAsString();

                // Filter: only include chat-capable models
                // Exclude embeddings, audio, image, moderation, fine-tuned models
                if (id.startsWith("ft:")) continue;
                if (id.contains("embedding")) continue;
                if (id.contains("dall-e")) continue;
                if (id.contains("whisper")) continue;
                if (id.contains("tts")) continue;
                if (id.contains("moderation")) continue;
                if (id.contains("babbage")) continue;
                if (id.contains("davinci") && !id.contains("gpt")) continue;

                models.add(id);
            }
        }

        Collections.sort(models);
        return models;
    }

    // -- Gemini: GET /v1beta/models ------------------------------------------

    private static List<String> fetchGeminiModels(String apiKey, String baseUrl) throws LlmException {
        List<String> models = new ArrayList<>();
        String pageToken = null;

        while (true) {
            StringBuilder url = new StringBuilder(baseUrl)
                    .append("/v1beta/models?pageSize=100&key=").append(apiKey);
            if (pageToken != null) {
                url.append("&pageToken=").append(pageToken);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url.toString()))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();

            String body = executeRequest(request);
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            JsonArray modelsArr = json.getAsJsonArray("models");

            if (modelsArr != null) {
                for (JsonElement el : modelsArr) {
                    JsonObject model = el.getAsJsonObject();

                    // Filter: only models that support generateContent
                    if (model.has("supportedGenerationMethods")) {
                        JsonArray methods = model.getAsJsonArray("supportedGenerationMethods");
                        boolean supportsChat = false;
                        for (JsonElement m : methods) {
                            if ("generateContent".equals(m.getAsString())) {
                                supportsChat = true;
                                break;
                            }
                        }
                        if (!supportsChat) continue;
                    } else {
                        continue;
                    }

                    // Model name is "models/gemini-2.5-flash" — strip prefix
                    String name = model.get("name").getAsString();
                    if (name.startsWith("models/")) {
                        name = name.substring(7);
                    }
                    models.add(name);
                }
            }

            if (json.has("nextPageToken") && !json.get("nextPageToken").isJsonNull()) {
                pageToken = json.get("nextPageToken").getAsString();
            } else {
                break;
            }
        }

        Collections.sort(models);
        return models;
    }

    // -- Mistral: GET /v1/models ---------------------------------------------

    private static List<String> fetchMistralModels(String apiKey, String baseUrl) throws LlmException {
        String url = baseUrl + "/v1/models";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + apiKey)
                .GET()
                .build();

        String body = executeRequest(request);
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        JsonArray data = json.getAsJsonArray("data");

        List<String> models = new ArrayList<>();
        if (data != null) {
            for (JsonElement el : data) {
                JsonObject model = el.getAsJsonObject();
                String id = model.get("id").getAsString();

                // Filter: skip fine-tuned and archived models
                if (id.startsWith("ft:")) continue;
                if (model.has("archived") && model.get("archived").getAsBoolean()) continue;

                // Filter: only chat-capable models
                if (model.has("capabilities") && model.get("capabilities").isJsonObject()) {
                    JsonObject caps = model.getAsJsonObject("capabilities");
                    if (caps.has("completion_chat") && !caps.get("completion_chat").getAsBoolean()) {
                        continue;
                    }
                }

                models.add(id);
            }
        }

        Collections.sort(models);
        return models;
    }

    // -- HTTP helper ---------------------------------------------------------

    private static String executeRequest(HttpRequest request) throws LlmException {
        try {
            HttpClient client = buildHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new LlmException("Models API error (" + response.statusCode() + "): "
                        + truncate(response.body(), 500));
            }

            return response.body();
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("Failed to fetch models: " + e.getMessage(), e);
        }
    }

    private static HttpClient buildHttpClient() {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL);

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
            }
        } catch (Exception ignored) {
        }

        return builder.build();
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
