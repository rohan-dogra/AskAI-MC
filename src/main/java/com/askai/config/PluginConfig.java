package com.askai.config;

import com.askai.model.AIProvider;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class PluginConfig {
    private final FileConfiguration config;

    public PluginConfig(FileConfiguration config) {
        this.config = config;
    }

    public boolean isServerKeyMode() {
        return "server".equalsIgnoreCase(config.getString("key-mode", "player"));
    }

    public String getEncryptionSeed() {
        return config.getString("encryption.seed", "CHANGE-ME-use-a-long-random-string-here");
    }

    public int getRateLimitRequests() {
        return config.getInt("rate-limit.requests", 10);
    }

    public int getRateLimitWindowSeconds() {
        return config.getInt("rate-limit.window-seconds", 60);
    }

    public int getMaxMessageLength() {
        return config.getInt("max-message-length", 2000);
    }

    public int getMaxResponseTokens() {
        return config.getInt("max-response-tokens", 1024);
    }

    public String getSystemPrompt() {
        return config.getString("system-prompt",
                "You are a helpful assistant in a Minecraft server. Keep responses concise and relevant. Responses should be clear and concise, not be overly detailed. At the end of the reponse, don't ask the user for more questions or information, just respond accurately, in short.");
    }

    public Set<AIProvider> getAllowedProviders() {
        List<String> ids = config.getStringList("allowed-providers");
        if (ids.isEmpty()) {
            return Set.of(AIProvider.values());
        }
        return ids.stream()
                .map(AIProvider::fromId)
                .filter(p -> p != null)
                .collect(Collectors.toUnmodifiableSet());
    }
}
