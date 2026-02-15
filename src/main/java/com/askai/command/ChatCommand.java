package com.askai.command;

import com.askai.AskAI;
import com.askai.model.AIProvider;
import com.askai.model.AIRequest;
import com.askai.model.AIResponse;
import com.askai.model.ChatMessage;
import com.askai.model.UserSettings;
import com.askai.provider.AIProviderClient;
import com.askai.provider.AIProviderException;
import com.askai.util.TextFormatter;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ChatCommand {
    private static final UUID SERVER_UUID = new UUID(0L, 0L);
    private final AskAI plugin;

    public ChatCommand(AskAI plugin) {
        this.plugin = plugin;
    }

    public void register(Commands commands) {
        commands.register(
                Commands.literal("chat")
                        .requires(src -> src.getSender().hasPermission("askai.use"))
                        // /chat setkey <provider> <key>
                        .then(Commands.literal("setkey")
                                .requires(src -> plugin.getPluginConfig().isServerKeyMode()
                                        ? src.getSender().hasPermission("askai.admin")
                                        : src.getSender().hasPermission("askai.setkey"))
                                .then(Commands.argument("provider", StringArgumentType.word())
                                        .suggests(this::suggestProviders)
                                        .then(Commands.argument("key", StringArgumentType.greedyString())
                                                .executes(this::handleSetKey))))
                        // /chat setmodel <provider> <model>
                        .then(Commands.literal("setmodel")
                                .requires(src -> src.getSender().hasPermission("askai.setkey"))
                                .then(Commands.argument("provider", StringArgumentType.word())
                                        .suggests(this::suggestProviders)
                                        .then(Commands.argument("model", StringArgumentType.word())
                                                .suggests(this::suggestModels)
                                                .executes(this::handleSetModel))))
                        // /chat provider <provider>
                        .then(Commands.literal("provider")
                                .requires(src -> src.getSender().hasPermission("askai.setkey"))
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

        boolean serverMode = plugin.getPluginConfig().isServerKeyMode();

        //run everything async
        CompletableFuture.runAsync(() -> {
            try {
                UserSettings settings = plugin.getSettingsRepo().load(playerId);
                AIProvider provider = settings.activeProvider();

                //resolve key based on mode
                String encryptedKey;
                if (serverMode) {
                    UserSettings serverSettings = plugin.getSettingsRepo().load(SERVER_UUID);
                    encryptedKey = serverSettings.getEncryptedKey(provider);
                    if (encryptedKey == null) {
                        runSync(() -> player.sendMessage(TextFormatter.error(
                                "No server API key set for " + provider.displayName()
                                        + ". Ask an admin to set it.")));
                        return;
                    }
                } else {
                    encryptedKey = settings.getEncryptedKey(provider);
                    if (encryptedKey == null) {
                        runSync(() -> player.sendMessage(TextFormatter.error(
                                "No API key set for " + provider.displayName()
                                        + ". Use: /chat setkey " + provider.id() + " <your-key>")));
                        return;
                    }
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

            } catch (Exception e) {
                //CompletableFuture.join() wraps exceptions in CompletionException
                Throwable cause = e;
                while (cause.getCause() != null && cause instanceof java.util.concurrent.CompletionException) {
                    cause = cause.getCause();
                }
                String msg = cause instanceof AIProviderException
                        ? cause.getMessage()
                        : "Request failed: " + sanitize(cause.getMessage());
                plugin.getLogger().warning("AI request failed for " + player.getName() + ": " + sanitize(cause.getMessage()));
                runSync(() -> player.sendMessage(TextFormatter.error(msg)));
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

        boolean serverMode = plugin.getPluginConfig().isServerKeyMode();
        if (serverMode && !player.hasPermission("askai.admin")) {
            player.sendMessage(TextFormatter.error("Server-key mode is active. Only admins can set API keys."));
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

        UUID targetId = serverMode ? SERVER_UUID : player.getUniqueId();

        CompletableFuture.runAsync(() -> {
            try {
                String encrypted = plugin.getKeyEncryptor().encrypt(key);
                plugin.getSettingsRepo().setEncryptedKey(targetId, provider, encrypted);

                String successMsg = serverMode
                        ? provider.displayName() + " server API key set."
                        : provider.displayName() + " API key set.";
                runSync(() -> player.sendMessage(TextFormatter.success(successMsg)));
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

        boolean serverMode = plugin.getPluginConfig().isServerKeyMode();

        CompletableFuture.runAsync(() -> {
            try {
                UserSettings playerSettings = plugin.getSettingsRepo().load(player.getUniqueId());
                if (serverMode) {
                    UserSettings serverSettings = plugin.getSettingsRepo().load(SERVER_UUID);
                    runSync(() -> player.sendMessage(
                            TextFormatter.formatServerStatus(playerSettings, serverSettings)));
                } else {
                    runSync(() -> player.sendMessage(TextFormatter.formatStatus(playerSettings)));
                }
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
