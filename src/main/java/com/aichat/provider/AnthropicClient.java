package com.aichat.provider;

import com.aichat.model.AIRequest;
import com.aichat.model.AIResponse;
import com.aichat.model.ChatMessage;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public final class AnthropicClient implements AIProviderClient {
    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION = "2023-06-01";
    private final HttpClient httpClient;

    public AnthropicClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public CompletableFuture<AIResponse> chat(AIRequest request, String apiKey) {
        String json = buildRequestJson(request);
        HttpRequest httpReq = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", API_VERSION)
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return httpClient.sendAsync(httpReq, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::parseResponse);
    }

    private String buildRequestJson(AIRequest request) {
        JsonObject root = new JsonObject();
        root.addProperty("model", request.model());
        root.addProperty("max_tokens", request.maxTokens());

        //anthropic: system message goes in top-level "system" field
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            root.addProperty("system", request.systemPrompt());
        }

        JsonArray messages = new JsonArray();
        for (ChatMessage msg : request.messages()) {
            //skip system messages, its already being handled above
            if ("system".equals(msg.role())) continue;
            JsonObject m = new JsonObject();
            m.addProperty("role", msg.role());
            m.addProperty("content", msg.content());
            messages.add(m);
        }

        root.add("messages", messages);
        return root.toString();
    }

    private AIResponse parseResponse(HttpResponse<String> response) {
        if (response.statusCode() == 401) {
            throw new AIProviderException("Invalid Anthropic API key. Check your key with /chat setkey anthropic <key>");
        }
        if (response.statusCode() == 429) {
            throw new AIProviderException("Anthropic rate limit exceeded. Please wait and try again.");
        }
        if (response.statusCode() >= 400) {
            throw new AIProviderException("Anthropic returned error " + response.statusCode());
        }

        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();

        //anthropic returns content as array of blocks
        JsonArray content = root.getAsJsonArray("content");
        StringBuilder text = new StringBuilder();
        for (var element : content) {
            JsonObject block = element.getAsJsonObject();
            if ("text".equals(block.get("type").getAsString())) {
                text.append(block.get("text").getAsString());
            }
        }

        String stopReason = root.has("stop_reason") && !root.get("stop_reason").isJsonNull()
                ? root.get("stop_reason").getAsString() : "unknown";

        int inputTokens = 0;
        int outputTokens = 0;
        if (root.has("usage")) {
            JsonObject usage = root.getAsJsonObject("usage");
            inputTokens = usage.get("input_tokens").getAsInt();
            outputTokens = usage.get("output_tokens").getAsInt();
        }

        return new AIResponse(text.toString(), inputTokens, outputTokens, stopReason);
    }
}
