package com.askai.storage;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

public final class DatabaseManager {
    private final Path dataFolder;
    private final Logger logger;
    private Connection connection;

    public DatabaseManager(Path dataFolder, Logger logger) {
        this.dataFolder = dataFolder;
        this.logger = logger;
    }

    public void initialize() throws SQLException {
        dataFolder.toFile().mkdirs();
        String url = "jdbc:sqlite:" + dataFolder.resolve("askai.db");
        this.connection = DriverManager.getConnection(url);

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA foreign_keys=ON");
        }

        createTables();
        logger.info("Database initialized.");
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS user_settings (
                        player_uuid   TEXT NOT NULL,
                        provider      TEXT NOT NULL,
                        setting_key   TEXT NOT NULL,
                        setting_value TEXT NOT NULL,
                        updated_at    INTEGER NOT NULL DEFAULT (strftime('%s', 'now')),
                        PRIMARY KEY (player_uuid, provider, setting_key)
                    )
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS user_active_provider (
                        player_uuid TEXT PRIMARY KEY,
                        provider    TEXT NOT NULL DEFAULT 'openai',
                        updated_at  INTEGER NOT NULL DEFAULT (strftime('%s', 'now'))
                    )
                    """);
        }
    }

    public Connection getConnection() {
        return connection;
    }

    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                logger.warning("Failed to close database: " + e.getMessage());
            }
        }
    }
}
