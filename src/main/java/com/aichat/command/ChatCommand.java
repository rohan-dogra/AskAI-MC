package com.aichat.command;

import com.aichat.AIChat;
import com.aichat.model.AIProvider;
import com.aichat.model.AIRequest;
import com.aichat.model.AIResponse;
import com.aichat.model.ChatMessage;
import com.aichat.model.UserSettings;
import com.aichat.provider.AIProviderClient;
import com.aichat.provider.AIProviderException;
import com.aichat.util.TextFormatter;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("UnstableApiUsage")
public final class ChatCommand {
    private final AIChat plugin;

    public ChatCommand(AIChat plugin) {
        this.plugin = plugin;
    }

    public void register(Commands commands) {
        commands.register(
                Commands.literal("chat")
                        // /chat setkey <provider> <key>
                        .then(Commands.literal("setkey")
                                .then(Commands.argument("provider", StringArgumentType.word())
                                        .suggests(this::suggestProviders)
                                        .then(Commands.argument("key", StringArgumentType.greedyString())
                                                .executes(this::handleSetKey))))
                        // /chat setmodel <provider> <model>
                        .then(Commands.literal("setmodel")
                                .then(Commands.argument("provider", StringArgumentType.word())
                                        .suggests(this::suggestProviders)
                                        .then(Commands.argument("model", StringArgumentType.word())
                                                .suggests(this::suggestModels)
                                                .executes(this::handleSetModel))))
                        // /chat provider <provider>
                        .then(Commands.literal("provider")
                                .then(Commands.argument("provider", StringArgumentType.word())
                                        .suggests(this::suggestProviders)
                                        .executes(this::handleSetProvider)))
                        // /chat status
                        .then(Commands.literal("status")
                                .executes(this::handleStatus))
                        // /chat <message> (greedy catch all, should be last here)
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(this::handleSend))
                        .build(),
                "Chat with AI providers",
                List.of()
        );
    }

    //handlers

    private int handleSend(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextFormatter.error("Only players can use this command."));
            return 0;
        }

        String message = StringArgumentType.getString(ctx, "message");
        UUID playerId = player.getUniqueId();

        //rate limit
        if (!plugin.getRateLimiter().tryAcquire(playerId)) {
            player.sendMessage(TextFormatter.error("You are sending messages too fast. Please wait."));
            return 0;
        }

        //input validation
        int maxLen = plugin.getPluginConfig().getMaxMessageLength();
        if (message.length() > maxLen) {
            player.sendMessage(TextFormatter.error("Message too long. Max: " + maxLen + " characters."));
            return 0;
        }

        player.sendMessage(TextFormatter.thinking());

        //run everything async
        CompletableFuture.runAsync(() -> {
            try {
                UserSettings settings = plugin.getSettingsRepo().load(playerId);
                AIProvider provider = settings.activeProvider();

                //check key
                String encryptedKey = settings.getEncryptedKey(provider);
                if (encryptedKey == null) {
                    runSync(() -> player.sendMessage(TextFormatter.error(
                            "No API key set for " + provider.displayName()
                                    + ". Use: /chat setkey " + provider.id() + " <your-key>")));
                    return;
                }

                String apiKey = plugin.getKeyEncryptor().decrypt(encryptedKey);
                String systemPrompt = plugin.getPluginConfig().getSystemPrompt();

                //build request (note that im only supporting single shot requests right now, no chat history, no streaming)
                AIRequest request = new AIRequest(
                        settings.getModel(provider),
                        List.of(new ChatMessage("user", message)),
                        systemPrompt,
                        plugin.getPluginConfig().getMaxResponseTokens(),
                        0.7
                );

                AIProviderClient client = plugin.getProviderRegistry().getClient(provider);
                AIResponse response = client.chat(request, apiKey).join();

                runSync(() -> player.sendMessage(TextFormatter.formatResponse(provider, response)));

            } catch (AIProviderException e) {
                runSync(() -> player.sendMessage(TextFormatter.error(e.getMessage())));
            } catch (Exception e) {
                String safeMsg = sanitize(e.getMessage());
                plugin.getLogger().warning("AI request failed for " + player.getName() + ": " + safeMsg);
                runSync(() -> player.sendMessage(TextFormatter.error("Request failed: " + safeMsg)));
            }
        });

        return Command.SINGLE_SUCCESS;
    }

    private int handleSetKey(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextFormatter.error("Only players can use this command."));
            return 0;
        }

        String providerStr = StringArgumentType.getString(ctx, "provider");
        String key = StringArgumentType.getString(ctx, "key");

        AIProvider provider = AIProvider.fromId(providerStr);
        if (provider == null) {
            player.sendMessage(TextFormatter.error("Unknown provider. Use: openai, anthropic, or gemini"));
            return 0;
        }

        if (!plugin.getPluginConfig().getAllowedProviders().contains(provider)) {
            player.sendMessage(TextFormatter.error(provider.displayName() + " is not enabled on this server."));
            return 0;
        }

        CompletableFuture.runAsync(() -> {
            try {
                String encrypted = plugin.getKeyEncryptor().encrypt(key);
                plugin.getSettingsRepo().setEncryptedKey(player.getUniqueId(), provider, encrypted);

                runSync(() -> {
                    player.sendMessage(TextFormatter.success(
                            "API key for " + provider.displayName() + " saved securely."));
                    //push key off screen
                    for (int i = 0; i < 20; i++) player.sendMessage(Component.empty());
                    player.sendMessage(TextFormatter.info("(Chat cleared for security)"));
                });
            } catch (Exception e) {
                String safeMsg = sanitize(e.getMessage());
                plugin.getLogger().warning("Failed to save key for " + player.getName() + ": " + safeMsg);
                runSync(() -> player.sendMessage(TextFormatter.error("Failed to save key: " + safeMsg)));
            }
        });

        return Command.SINGLE_SUCCESS;
    }

    private int handleSetModel(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextFormatter.error("Only players can use this command."));
            return 0;
        }

        String providerStr = StringArgumentType.getString(ctx, "provider");
        String model = StringArgumentType.getString(ctx, "model");

        AIProvider provider = AIProvider.fromId(providerStr);
        if (provider == null) {
            player.sendMessage(TextFormatter.error("Unknown provider. Use: openai, anthropic, or gemini"));
            return 0;
        }

        CompletableFuture.runAsync(() -> {
            try {
                plugin.getSettingsRepo().setModel(player.getUniqueId(), provider, model);
                runSync(() -> player.sendMessage(TextFormatter.success(
                        "Model for " + provider.displayName() + " set to: " + model)));
            } catch (Exception e) {
                runSync(() -> player.sendMessage(TextFormatter.error("Failed to set model.")));
            }
        });

        return Command.SINGLE_SUCCESS;
    }

    private int handleSetProvider(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextFormatter.error("Only players can use this command."));
            return 0;
        }

        String providerStr = StringArgumentType.getString(ctx, "provider");
        AIProvider provider = AIProvider.fromId(providerStr);
        if (provider == null) {
            player.sendMessage(TextFormatter.error("Unknown provider. Use: openai, anthropic, or gemini"));
            return 0;
        }

        if (!plugin.getPluginConfig().getAllowedProviders().contains(provider)) {
            player.sendMessage(TextFormatter.error(provider.displayName() + " is not enabled on this server."));
            return 0;
        }

        CompletableFuture.runAsync(() -> {
            try {
                plugin.getSettingsRepo().setActiveProvider(player.getUniqueId(), provider);
                runSync(() -> player.sendMessage(TextFormatter.success(
                        "Switched to " + provider.displayName() + " (" + provider.defaultModel() + ")")));
            } catch (Exception e) {
                runSync(() -> player.sendMessage(TextFormatter.error("Failed to switch provider.")));
            }
        });

        return Command.SINGLE_SUCCESS;
    }

    private int handleStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextFormatter.error("Only players can use this command."));
            return 0;
        }

        CompletableFuture.runAsync(() -> {
            try {
                UserSettings settings = plugin.getSettingsRepo().load(player.getUniqueId());
                runSync(() -> player.sendMessage(TextFormatter.formatStatus(settings)));
            } catch (Exception e) {
                runSync(() -> player.sendMessage(TextFormatter.error("Failed to load settings.")));
            }
        });

        return Command.SINGLE_SUCCESS;
    }

    //suggestions

    private CompletableFuture<Suggestions> suggestProviders(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (AIProvider p : plugin.getPluginConfig().getAllowedProviders()) {
            if (p.id().startsWith(builder.getRemainingLowerCase())) {
                builder.suggest(p.id());
            }
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestModels(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String providerStr = StringArgumentType.getString(ctx, "provider");
        AIProvider provider = AIProvider.fromId(providerStr);
        if (provider != null) {
            for (String model : provider.suggestedModels()) {
                if (model.toLowerCase().startsWith(builder.getRemainingLowerCase())) {
                    builder.suggest(model);
                }
            }
        }
        return builder.buildFuture();
    }

    //helpers

    private void runSync(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    private static String sanitize(String message) {
        if (message == null) return "Unknown error";
        //strip anything that looks like an API key (long alphanumeric strings)
        return message.replaceAll("[A-Za-z0-9_-]{20,}", "***");
    }
}
