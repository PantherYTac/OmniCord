package com.omnicord.plugin.storage;

import com.omnicord.plugin.storage.model.LinkedUser;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Storage contract for account links and persistent data.
 */
public interface StorageProvider {

    void initialize() throws Exception;

    void shutdown();

    CompletableFuture<Void> saveLink(UUID uuid, String discordId, String discordTag);

    CompletableFuture<Void> removeLink(UUID uuid);

    CompletableFuture<Optional<LinkedUser>> getLinkByUuid(UUID uuid);

    CompletableFuture<Optional<LinkedUser>> getLinkByDiscordId(String discordId);

    CompletableFuture<Void> setRewardClaimed(UUID uuid, boolean claimed);

    CompletableFuture<Void> savePendingCode(UUID uuid, String code, long expiresAt);

    CompletableFuture<Optional<UUID>> getUuidByPendingCode(String code);

    CompletableFuture<Void> removePendingCode(String code);
}
