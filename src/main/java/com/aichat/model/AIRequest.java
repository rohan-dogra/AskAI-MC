package com.aichat.model;

import java.util.List;

public record AIRequest(
        String model,
        List<ChatMessage> messages,
        String systemPrompt,
        int maxTokens,
        double temperature
) {
}
