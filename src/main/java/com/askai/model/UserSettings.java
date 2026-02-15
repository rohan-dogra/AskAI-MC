package com.askai.model;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public final class UserSettings {
    private final UUID playerId;
    private AIProvider activeProvider;
    private final Map<AIProvider, String> encryptedKeys;
    private final Map<AIProvider, String> models;

    public UserSettings(UUID playerId) {
        this.playerId = playerId;
        this.activeProvider = AIProvider.OPENAI;
        this.encryptedKeys = new EnumMap<>(AIProvider.class);
        this.models = new EnumMap<>(AIProvider.class);
    }

    public UUID playerId() {
        return playerId;
    }

    public AIProvider activeProvider() {
        return activeProvider;
    }

    public void setActiveProvider(AIProvider provider) {
        this.activeProvider = provider;
    }

    public String getEncryptedKey(AIProvider provider) {
        return encryptedKeys.get(provider);
    }

    public void setEncryptedKey(AIProvider provider, String encryptedKey) {
        encryptedKeys.put(provider, encryptedKey);
    }

    public boolean hasKey(AIProvider provider) {
        return encryptedKeys.containsKey(provider);
    }

    public String getModel(AIProvider provider) {
        return models.getOrDefault(provider, provider.defaultModel());
    }

    public void setModel(AIProvider provider, String model) {
        models.put(provider, model);
    }
}
