package com.aichat.provider;

import com.aichat.model.AIRequest;
import com.aichat.model.AIResponse;

import java.util.concurrent.CompletableFuture;

public interface AIProviderClient {
    CompletableFuture<AIResponse> chat(AIRequest request, String apiKey);
}
