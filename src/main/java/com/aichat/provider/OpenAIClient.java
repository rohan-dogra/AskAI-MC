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

public final class OpenAIClient implements AIProviderClient {
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private final HttpClient httpClient;

    public OpenAIClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public CompletableFuture<AIResponse> chat(AIRequest request, String apiKey) {
        String json = buildRequestJson(request);
        HttpRequest httpReq = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
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
        root.addProperty("temperature", request.temperature());

        JsonArray messages = new JsonArray();

        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            JsonObject sys = new JsonObject();
            sys.addProperty("role", "system");
            sys.addProperty("content", request.systemPrompt());
            messages.add(sys);
        }

        for (ChatMessage msg : request.messages()) {
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
            throw new AIProviderException("Invalid OpenAI API key. Check your key with /chat setkey openai <key>");
        }
        if (response.statusCode() == 429) {
            throw new AIProviderException("OpenAI rate limit exceeded. Please wait and try again.");
        }
        if (response.statusCode() >= 400) {
            throw new AIProviderException("OpenAI returned error " + response.statusCode());
        }

        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonObject choice = root.getAsJsonArray("choices").get(0).getAsJsonObject();
        String text = choice.getAsJsonObject("message").get("content").getAsString();
        String finishReason = choice.has("finish_reason") && !choice.get("finish_reason").isJsonNull()
                ? choice.get("finish_reason").getAsString() : "unknown";

        int promptTokens = 0;
        int completionTokens = 0;
        if (root.has("usage")) {
            JsonObject usage = root.getAsJsonObject("usage");
            promptTokens = usage.get("prompt_tokens").getAsInt();
            completionTokens = usage.get("completion_tokens").getAsInt();
        }

        return new AIResponse(text, promptTokens, completionTokens, finishReason);
    }
}
