package com.devbox.ai;

import com.google.gson.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

/**
 * Anthropic (Claude) API provider.
 */
public class AnthropicProvider implements AiProvider {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-sonnet-4-6-20250514";

    private final String apiKey;
    private final HttpClient httpClient;
    private final Gson gson;

    public AnthropicProvider(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newHttpClient();
        this.gson = new Gson();
    }

    @Override
    public String chat(String systemPrompt, String userPrompt, int maxTokens) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("model", MODEL);
        body.addProperty("max_tokens", maxTokens);

        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            body.addProperty("system", systemPrompt);
        }

        body.add("messages", gson.toJsonTree(List.of(
                Map.of("role", "user", "content", userPrompt)
        )));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Anthropic API error " + response.statusCode() + ": " + response.body());
        }

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        return json.getAsJsonArray("content")
                .get(0).getAsJsonObject()
                .get("text").getAsString();
    }

    @Override
    public String providerName() {
        return "Claude (Anthropic)";
    }
}
