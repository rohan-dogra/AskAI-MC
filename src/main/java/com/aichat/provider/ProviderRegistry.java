package com.aichat.provider;

import com.aichat.model.AIProvider;

import java.net.http.HttpClient;
import java.util.Map;

public final class ProviderRegistry {
    private final Map<AIProvider, AIProviderClient> clients;

    public ProviderRegistry(HttpClient httpClient) {
        this.clients = Map.of(
                AIProvider.OPENAI, new OpenAIClient(httpClient),
                AIProvider.ANTHROPIC, new AnthropicClient(httpClient),
                AIProvider.GEMINI, new GeminiClient(httpClient)
        );
    }

    public AIProviderClient getClient(AIProvider provider) {
        AIProviderClient client = clients.get(provider);
        if (client == null) {
            throw new AIProviderException("No client registered for provider: " + provider.displayName());
        }
        return client;
    }
}
