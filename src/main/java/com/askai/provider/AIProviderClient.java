package com.askai.provider;

import com.askai.model.AIRequest;
import com.askai.model.AIResponse;

import java.util.concurrent.CompletableFuture;

public interface AIProviderClient {
    CompletableFuture<AIResponse> chat(AIRequest request, String apiKey);
}
