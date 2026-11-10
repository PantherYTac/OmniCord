package com.omnicord.plugin.linking;

import com.omnicord.plugin.OmniCord;
import com.omnicord.plugin.api.event.OmniCordLinkEvent;
import com.omnicord.plugin.storage.model.LinkedUser;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Handles account linking verification, one-time codes, and reward dispatching.
 */
public class AccountLinkingManager {

    private final OmniCord plugin;
    private final SecureRandom random = new SecureRandom();

    public AccountLinkingManager(OmniCord plugin) {
        this.plugin = plugin;
    }

    /**
     * Generates a unique 6-digit linking code for an in-game player.
     */
    public CompletableFuture<String> generateLinkCode(UUID uuid) {
        int codeInt = 100000 + random.nextInt(900000);
        String code = String.valueOf(codeInt);
        int expireMinutes = plugin.getConfigManager().getConfig().getInt("general.link_code_expire_minutes", 5);
        long expiresAt = System.currentTimeMillis() + (expireMinutes * 60L * 1000L);

        return plugin.getDatabaseManager().getStorage().savePendingCode(uuid, code, expiresAt)
                .thenApply(v -> code);
    }

    /**
     * Complete the link using a code entered in Discord.
     */
    public CompletableFuture<Boolean> completeLink(String code, String discordId, String discordTag) {
        return plugin.getDatabaseManager().getStorage().getUuidByPendingCode(code)
                .thenCompose(optUuid -> {
                    if (!optUuid.isPresent()) {
                        return CompletableFuture.completedFuture(false);
                    }

                    UUID uuid = optUuid.get();

                    // Check if this Discord ID is already linked to another Minecraft account
                    return plugin.getDatabaseManager().getStorage().getLinkByDiscordId(discordId)
                            .thenCompose(existingLink -> {
                                CompletableFuture<Void> prepare = CompletableFuture.completedFuture(null);
                                if (existingLink.isPresent() && !existingLink.get().getMinecraftUuid().equals(uuid)) {
                                    prepare = plugin.getDatabaseManager().getStorage().removeLink(existingLink.get().getMinecraftUuid())
                                            .exceptionally(ex -> {
                                                plugin.getLogger().warning("[OmniCord] Failed to remove old link for " +
                                                        existingLink.get().getMinecraftUuid() + ": " + ex.getMessage());
                                                return null;
                                            });
                                }

                                return prepare.thenCompose(ignored -> plugin.getDatabaseManager().getStorage().saveLink(uuid, discordId, discordTag))
                                        .thenCompose(v -> plugin.getDatabaseManager().getStorage().removePendingCode(code))
                                        .thenApply(v -> {
                                            // Trigger link reward
                                            processLinkRewards(uuid, discordId, discordTag);

                                            // Trigger role and nickname synchronization
                                            plugin.getRoleSyncManager().syncUser(uuid, discordId);

                                            // Fire Bukkit Event on Main Thread
                                            Bukkit.getScheduler().runTask(plugin, () -> {
                                                Player player = Bukkit.getPlayer(uuid);
                                                if (player != null && player.isOnline()) {
                                                    String msg = plugin.getConfigManager().getMessage("linking.link_success")
                                                            .replace("%discord_tag%", discordTag);
                                                    player.sendMessage(msg);
                                                }
                                                Bukkit.getPluginManager().callEvent(new OmniCordLinkEvent(uuid, discordId, discordTag));
                                            });

                                            return true;
                                        })
                                        // FIX Bug #21: Log exceptions from the save/removePendingCode chain
                                        .exceptionally(ex -> {
                                            plugin.getLogger().severe("[OmniCord] Failed to complete account link for UUID " + uuid + ": " + ex.getMessage());
                                            return false;
                                        });
                            });
                })
                // FIX Bug #21: Log exceptions from the outer chain (code lookup / discord ID check)
                .exceptionally(ex -> {
                    plugin.getLogger().severe("[OmniCord] Unexpected error in completeLink(): " + ex.getMessage());
                    return false;
                });
    }

    /**
     * Executes configured console commands and reward payouts upon initial link.
     */
    private void processLinkRewards(UUID uuid, String discordId, String discordTag) {
        boolean rewardsEnabled = plugin.getConfigManager().getRoleSync().getBoolean("linked_settings.link_rewards.enabled", true);
        if (!rewardsEnabled) return;

        plugin.getDatabaseManager().getStorage().getLinkByUuid(uuid).thenAccept(opt -> {
            if (opt.isPresent()) {
                LinkedUser user = opt.get();
                if (!user.isRewardClaimed()) {
                    List<String> commands = plugin.getConfigManager().getRoleSync().getStringList("linked_settings.link_rewards.commands");
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        String playerName = Bukkit.getOfflinePlayer(uuid).getName();
                        if (playerName == null) playerName = "Player";

                        for (String cmd : commands) {
                            String formatted = cmd.replace("%player%", playerName)
                                    .replace("%uuid%", uuid.toString())
                                    .replace("%discord_id%", discordId)
                                    .replace("%discord_tag%", discordTag);
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), formatted);
                        }

                        Player player = Bukkit.getPlayer(uuid);
                        if (player != null && player.isOnline()) {
                            player.sendMessage(plugin.getConfigManager().getMessage("linking.reward_claimed"));
                        }
                    });

                    plugin.getDatabaseManager().getStorage().setRewardClaimed(uuid, true);
                }
            }
        // FIX Bug #21: Log exceptions silently swallowed by thenAccept
        }).exceptionally(ex -> {
            plugin.getLogger().warning("[OmniCord] Failed to process link rewards for " + uuid + ": " + ex.getMessage());
            return null;
        });
    }

    /**
     * Unlinks a player account.
     */
    public CompletableFuture<Void> unlink(UUID uuid) {
        return plugin.getDatabaseManager().getStorage().removeLink(uuid);
    }

    public CompletableFuture<Optional<LinkedUser>> getLink(UUID uuid) {
        return plugin.getDatabaseManager().getStorage().getLinkByUuid(uuid);
    }

    public CompletableFuture<Optional<LinkedUser>> getLinkByDiscord(String discordId) {
        return plugin.getDatabaseManager().getStorage().getLinkByDiscordId(discordId);
    }
}
