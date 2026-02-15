package com.aichat.model;

import java.util.List;

public enum AIProvider {
    OPENAI("openai", "OpenAI", "gpt-4o-mini",
            List.of("gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "o1-mini")),
    ANTHROPIC("anthropic", "Anthropic", "claude-haiku-4-5",
            List.of("claude-sonnet-4-5", "claude-haiku-4-5")),
    GEMINI("gemini", "Google Gemini", "gemini-2.0-flash",
            List.of("gemini-2.0-flash", "gemini-1.5-pro", "gemini-1.5-flash"));

    private final String id;
    private final String displayName;
    private final String defaultModel;
    private final List<String> suggestedModels;

    AIProvider(String id, String displayName, String defaultModel, List<String> suggestedModels) {
        this.id = id;
        this.displayName = displayName;
        this.defaultModel = defaultModel;
        this.suggestedModels = suggestedModels;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String defaultModel() {
        return defaultModel;
    }

    public List<String> suggestedModels() {
        return suggestedModels;
    }

    public static AIProvider fromId(String id) {
        if (id == null) return null;
        for (AIProvider p : values()) {
            if (p.id.equalsIgnoreCase(id)) return p;
        }
        return null;
    }
}
