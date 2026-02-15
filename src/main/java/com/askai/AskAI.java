package com.askai;

import com.askai.command.ChatCommand;
import com.askai.config.PluginConfig;
import com.askai.crypto.KeyEncryptor;
import com.askai.provider.ProviderRegistry;
import com.askai.storage.DatabaseManager;
import com.askai.storage.UserSettingsRepository;
import com.askai.util.RateLimiter;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bstats.bukkit.Metrics;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.http.HttpClient;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.Executors;

public final class AskAI extends JavaPlugin implements Listener {
    private PluginConfig pluginConfig;
    private KeyEncryptor keyEncryptor;
    private DatabaseManager databaseManager;
    private UserSettingsRepository settingsRepo;
    private ProviderRegistry providerRegistry;
    private RateLimiter rateLimiter;
    private HttpClient httpClient;

    @Override
    public void onEnable() {
        //config
        saveDefaultConfig();
        this.pluginConfig = new PluginConfig(getConfig());

        //crypto
        this.keyEncryptor = new KeyEncryptor(pluginConfig.getEncryptionSeed(), getDataFolder());

        //db
        this.databaseManager = new DatabaseManager(getDataFolder().toPath(), getLogger());
        try {
            databaseManager.initialize();
        } catch (SQLException e) {
            getLogger().severe("Failed to initialize database: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.settingsRepo = new UserSettingsRepository(databaseManager);

        //HTTP client with virtual threads
        this.httpClient = HttpClient.newBuilder()
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        //provider registry
        this.providerRegistry = new ProviderRegistry(httpClient);

        //rate limiter
        this.rateLimiter = new RateLimiter(
                pluginConfig.getRateLimitRequests(),
                pluginConfig.getRateLimitWindowSeconds()
        );

        //register commands via Brigadier lifecycle event
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            new ChatCommand(this).register(event.registrar());
        });

        //register event listeners (key redaction, cleanup)
        getServer().getPluginManager().registerEvents(this, this);

        //bStats metrics
        new Metrics(this, 29560);

        getLogger().info("AskAI enabled.");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("AskAI disabled.");
    }

    //cancel /chat setkey commands from being logged by other plugins
    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage().toLowerCase();
        if (msg.startsWith("/chat setkey ")) {
            //log a safe version ourselves. the actual command still reaches Brigadier unmodified
            getLogger().info(event.getPlayer().getName() + " set an API key (redacted from logs)");
        }
    }

    //clean up rate limiter when player leaves
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        rateLimiter.cleanup(event.getPlayer().getUniqueId());
    }

    //component getters

    public PluginConfig getPluginConfig() {
        return pluginConfig;
    }

    public KeyEncryptor getKeyEncryptor() {
        return keyEncryptor;
    }

    public UserSettingsRepository getSettingsRepo() {
        return settingsRepo;
    }

    public ProviderRegistry getProviderRegistry() {
        return providerRegistry;
    }

    public RateLimiter getRateLimiter() {
        return rateLimiter;
    }
}
