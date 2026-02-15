package com.askai.provider;

import com.askai.model.AIRequest;
import com.askai.model.AIResponse;
import com.askai.model.ChatMessage;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public final class GeminiClient implements AIProviderClient {
    private static final String API_URL_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";
    private final HttpClient httpClient;

    public GeminiClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public CompletableFuture<AIResponse> chat(AIRequest request, String apiKey) {
        String url = String.format(API_URL_TEMPLATE, request.model());
        String json = buildRequestJson(request);
        HttpRequest httpReq = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return httpClient.sendAsync(httpReq, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::parseResponse);
    }

    private String buildRequestJson(AIRequest request) {
        JsonObject root = new JsonObject();

        //system instruction
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            JsonObject sysInstruction = new JsonObject();
            JsonArray sysParts = new JsonArray();
            JsonObject sysPart = new JsonObject();
            sysPart.addProperty("text", request.systemPrompt());
            sysParts.add(sysPart);
            sysInstruction.add("parts", sysParts);
            root.add("systemInstruction", sysInstruction);
        }

        //contents (messages). gemini uses "user" and "model" roles
        JsonArray contents = new JsonArray();
        for (ChatMessage msg : request.messages()) {
            if ("system".equals(msg.role())) continue;
            JsonObject content = new JsonObject();
            content.addProperty("role", "assistant".equals(msg.role()) ? "model" : msg.role());
            JsonArray parts = new JsonArray();
            JsonObject part = new JsonObject();
            part.addProperty("text", msg.content());
            parts.add(part);
            content.add("parts", parts);
            contents.add(content);
        }
        root.add("contents", contents);

        //generation config
        JsonObject genConfig = new JsonObject();
        genConfig.addProperty("maxOutputTokens", request.maxTokens());
        genConfig.addProperty("temperature", request.temperature());
        root.add("generationConfig", genConfig);

        return root.toString();
    }

    private AIResponse parseResponse(HttpResponse<String> response) {
        if (response.statusCode() == 400) {
            throw new AIProviderException("Gemini rejected the request. Check your model name and API key.");
        }
        if (response.statusCode() == 403) {
            throw new AIProviderException("Invalid Gemini API key. Check your key with /chat setkey gemini <key>");
        }
        if (response.statusCode() == 429) {
            throw new AIProviderException("Gemini rate limit exceeded. Please wait and try again.");
        }
        if (response.statusCode() >= 400) {
            throw new AIProviderException("Gemini returned error " + response.statusCode());
        }

        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonObject candidate = root.getAsJsonArray("candidates").get(0).getAsJsonObject();
        JsonArray parts = candidate.getAsJsonObject("content").getAsJsonArray("parts");

        StringBuilder text = new StringBuilder();
        for (var element : parts) {
            JsonObject part = element.getAsJsonObject();
            if (part.has("text")) {
                text.append(part.get("text").getAsString());
            }
        }

        String finishReason = candidate.has("finishReason")
                ? candidate.get("finishReason").getAsString() : "UNKNOWN";

        int promptTokens = 0;
        int completionTokens = 0;
        if (root.has("usageMetadata")) {
            JsonObject usage = root.getAsJsonObject("usageMetadata");
            if (usage.has("promptTokenCount")) promptTokens = usage.get("promptTokenCount").getAsInt();
            if (usage.has("candidatesTokenCount")) completionTokens = usage.get("candidatesTokenCount").getAsInt();
        }

        return new AIResponse(text.toString(), promptTokens, completionTokens, finishReason);
    }
}
