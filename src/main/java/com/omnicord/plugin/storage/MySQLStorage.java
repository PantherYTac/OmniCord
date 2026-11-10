package com.omnicord.plugin.storage;

import com.omnicord.plugin.OmniCord;
import com.omnicord.plugin.storage.model.LinkedUser;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * MySQL implementation of StorageProvider using HikariCP for cross-server networks.
 */
public class MySQLStorage implements StorageProvider {

    private final OmniCord plugin;
    private HikariDataSource dataSource;

    public MySQLStorage(OmniCord plugin) {
        this.plugin = plugin;
    }

    @Override
    public void initialize() throws Exception {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        String host = config.getString("database.mysql.host", "127.0.0.1");
        int port = config.getInt("database.mysql.port", 3306);
        String database = config.getString("database.mysql.database", "omnicord");
        String username = config.getString("database.mysql.username", "root");
        String password = config.getString("database.mysql.password", "");
        boolean useSSL = config.getBoolean("database.mysql.use_ssl", false);
        int maxPool = config.getInt("database.mysql.maximum_pool_size", 10);
        long timeout = config.getLong("database.mysql.connection_timeout_ms", 5000);

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName("OmniCord-MySQL-Pool");
        hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
        hikariConfig.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database +
                "?useSSL=" + useSSL + "&characterEncoding=utf8&autoReconnect=true");
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        hikariConfig.setMaximumPoolSize(maxPool);
        hikariConfig.setConnectionTimeout(timeout);
        hikariConfig.setMaxLifetime(1800000);

        this.dataSource = new HikariDataSource(hikariConfig);

        createTables();
    }

    private void createTables() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS omnicord_linked_users (" +
                            "uuid VARCHAR(36) PRIMARY KEY, " +
                            "discord_id VARCHAR(32) NOT NULL UNIQUE, " +
                            "discord_tag VARCHAR(64), " +
                            "linked_timestamp BIGINT NOT NULL, " +
                            "reward_claimed TINYINT DEFAULT 0" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;"
            )) {
                stmt.execute();
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS omnicord_pending_codes (" +
                            "code VARCHAR(10) PRIMARY KEY, " +
                            "uuid VARCHAR(36) NOT NULL, " +
                            "expires_at BIGINT NOT NULL" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;"
            )) {
                stmt.execute();
            }
        }
    }

    @Override
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @Override
    public CompletableFuture<Void> saveLink(UUID uuid, String discordId, String discordTag) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO omnicord_linked_users (uuid, discord_id, discord_tag, linked_timestamp, reward_claimed) " +
                                 "VALUES (?, ?, ?, ?, ?) " +
                                 "ON DUPLICATE KEY UPDATE discord_id = VALUES(discord_id), discord_tag = VALUES(discord_tag), " +
                                 "linked_timestamp = VALUES(linked_timestamp);"
                 )) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, discordId);
                stmt.setString(3, discordTag);
                stmt.setLong(4, System.currentTimeMillis());
                stmt.setInt(5, 0);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to save account link: " + e.getMessage());
            }
        });
    }

    @Override
    public CompletableFuture<Void> removeLink(UUID uuid) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "DELETE FROM omnicord_linked_users WHERE uuid = ?;"
                 )) {
                stmt.setString(1, uuid.toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to remove account link: " + e.getMessage());
            }
        });
    }

    @Override
    public CompletableFuture<Optional<LinkedUser>> getLinkByUuid(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT * FROM omnicord_linked_users WHERE uuid = ?;"
                 )) {
                stmt.setString(1, uuid.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        LinkedUser user = new LinkedUser(
                                UUID.fromString(rs.getString("uuid")),
                                rs.getString("discord_id"),
                                rs.getString("discord_tag"),
                                rs.getLong("linked_timestamp"),
                                rs.getInt("reward_claimed") == 1
                        );
                        return Optional.of(user);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to query link by UUID: " + e.getMessage());
            }
            return Optional.empty();
        });
    }

    @Override
    public CompletableFuture<Optional<LinkedUser>> getLinkByDiscordId(String discordId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT * FROM omnicord_linked_users WHERE discord_id = ?;"
                 )) {
                stmt.setString(1, discordId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        LinkedUser user = new LinkedUser(
                                UUID.fromString(rs.getString("uuid")),
                                rs.getString("discord_id"),
                                rs.getString("discord_tag"),
                                rs.getLong("linked_timestamp"),
                                rs.getInt("reward_claimed") == 1
                        );
                        return Optional.of(user);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to query link by Discord ID: " + e.getMessage());
            }
            return Optional.empty();
        });
    }

    @Override
    public CompletableFuture<Void> setRewardClaimed(UUID uuid, boolean claimed) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "UPDATE omnicord_linked_users SET reward_claimed = ? WHERE uuid = ?;"
                 )) {
                stmt.setInt(1, claimed ? 1 : 0);
                stmt.setString(2, uuid.toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to update reward status: " + e.getMessage());
            }
        });
    }

    @Override
    public CompletableFuture<Void> savePendingCode(UUID uuid, String code, long expiresAt) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO omnicord_pending_codes (code, uuid, expires_at) VALUES (?, ?, ?) " +
                                 "ON DUPLICATE KEY UPDATE uuid = VALUES(uuid), expires_at = VALUES(expires_at);"
                 )) {
                stmt.setString(1, code);
                stmt.setString(2, uuid.toString());
                stmt.setLong(3, expiresAt);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to save pending code: " + e.getMessage());
            }
        });
    }

    @Override
    public CompletableFuture<Optional<UUID>> getUuidByPendingCode(String code) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT uuid, expires_at FROM omnicord_pending_codes WHERE code = ?;"
                 )) {
                stmt.setString(1, code);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        long expiresAt = rs.getLong("expires_at");
                        if (System.currentTimeMillis() <= expiresAt) {
                            return Optional.of(UUID.fromString(rs.getString("uuid")));
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to query pending code: " + e.getMessage());
            }
            return Optional.empty();
        });
    }

    @Override
    public CompletableFuture<Void> removePendingCode(String code) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "DELETE FROM omnicord_pending_codes WHERE code = ?;"
                 )) {
                stmt.setString(1, code);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to remove pending code: " + e.getMessage());
            }
        });
    }
}
