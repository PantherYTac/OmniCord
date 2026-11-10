package com.omnicord.plugin.storage;

import com.omnicord.plugin.OmniCord;

/**
 * Manages active storage provider lifecycle (SQLite or MySQL).
 */
public class DatabaseManager {

    private final OmniCord plugin;
    private StorageProvider storageProvider;

    public DatabaseManager(OmniCord plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        String type = plugin.getConfigManager().getConfig().getString("database.type", "SQLITE");
        if ("MYSQL".equalsIgnoreCase(type)) {
            plugin.getLogger().info("Initializing MySQL Storage Engine...");
            storageProvider = new MySQLStorage(plugin);
        } else {
            plugin.getLogger().info("Initializing SQLite Storage Engine...");
            storageProvider = new SQLiteStorage(plugin);
        }

        try {
            storageProvider.initialize();
            plugin.getLogger().info("Database successfully connected and schema verified.");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void shutdown() {
        if (storageProvider != null) {
            storageProvider.shutdown();
        }
    }

    public StorageProvider getStorage() {
        return storageProvider;
    }

    public java.util.concurrent.CompletableFuture<java.util.Optional<String>> getDiscordIdByUuid(java.util.UUID uuid) {
        if (storageProvider == null || uuid == null) {
            return java.util.concurrent.CompletableFuture.completedFuture(java.util.Optional.empty());
        }
        return storageProvider.getLinkByUuid(uuid).thenApply(opt -> opt.map(com.omnicord.plugin.storage.model.LinkedUser::getDiscordId));
    }

    public java.util.concurrent.CompletableFuture<java.util.Optional<java.util.UUID>> getUuidByDiscordId(String discordId) {
        if (storageProvider == null || discordId == null) {
            return java.util.concurrent.CompletableFuture.completedFuture(java.util.Optional.empty());
        }
        return storageProvider.getLinkByDiscordId(discordId).thenApply(opt -> opt.map(com.omnicord.plugin.storage.model.LinkedUser::getMinecraftUuid));
    }
}
