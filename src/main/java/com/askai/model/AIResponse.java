package com.askai.model;

public record AIResponse(String text, int promptTokens, int completionTokens, String finishReason) {
}
