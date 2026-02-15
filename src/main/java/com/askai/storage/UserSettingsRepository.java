package com.askai.storage;

import com.askai.model.AIProvider;
import com.askai.model.UserSettings;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public final class UserSettingsRepository {
    private final DatabaseManager db;

    public UserSettingsRepository(DatabaseManager db) {
        this.db = db;
    }

    public UserSettings load(UUID playerId) throws SQLException {
        UserSettings settings = new UserSettings(playerId);
        Connection conn = db.getConnection();

        //load active provider
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT provider FROM user_active_provider WHERE player_uuid = ?")) {
            ps.setString(1, playerId.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                AIProvider provider = AIProvider.fromId(rs.getString("provider"));
                if (provider != null) {
                    settings.setActiveProvider(provider);
                }
            }
        }

        //load all settings (keys and models)
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT provider, setting_key, setting_value FROM user_settings WHERE player_uuid = ?")) {
            ps.setString(1, playerId.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                AIProvider provider = AIProvider.fromId(rs.getString("provider"));
                if (provider == null) continue;

                String key = rs.getString("setting_key");
                String value = rs.getString("setting_value");
                switch (key) {
                    case "encrypted_api_key" -> settings.setEncryptedKey(provider, value);
                    case "model" -> settings.setModel(provider, value);
                }
            }
        }

        return settings;
    }

    public void setEncryptedKey(UUID playerId, AIProvider provider, String encryptedKey) throws SQLException {
        upsertSetting(playerId, provider, "encrypted_api_key", encryptedKey);
    }

    public void setModel(UUID playerId, AIProvider provider, String model) throws SQLException {
        upsertSetting(playerId, provider, "model", model);
    }

    public void setActiveProvider(UUID playerId, AIProvider provider) throws SQLException {
        Connection conn = db.getConnection();
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO user_active_provider (player_uuid, provider, updated_at)
                VALUES (?, ?, strftime('%s', 'now'))
                ON CONFLICT(player_uuid) DO UPDATE SET provider = ?, updated_at = strftime('%s', 'now')
                """)) {
            ps.setString(1, playerId.toString());
            ps.setString(2, provider.id());
            ps.setString(3, provider.id());
            ps.executeUpdate();
        }
    }

    private void upsertSetting(UUID playerId, AIProvider provider, String key, String value) throws SQLException {
        Connection conn = db.getConnection();
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO user_settings (player_uuid, provider, setting_key, setting_value, updated_at)
                VALUES (?, ?, ?, ?, strftime('%s', 'now'))
                ON CONFLICT(player_uuid, provider, setting_key)
                DO UPDATE SET setting_value = ?, updated_at = strftime('%s', 'now')
                """)) {
            ps.setString(1, playerId.toString());
            ps.setString(2, provider.id());
            ps.setString(3, key);
            ps.setString(4, value);
            ps.setString(5, value);
            ps.executeUpdate();
        }
    }
}
