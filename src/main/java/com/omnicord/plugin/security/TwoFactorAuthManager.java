package com.omnicord.plugin.security;

import com.omnicord.plugin.OmniCord;
import com.omnicord.plugin.storage.model.LinkedUser;
import com.omnicord.plugin.utils.ColorUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.awt.Color;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles Discord-based Two-Factor Authentication (2FA) for server staff and players.
 */
public class TwoFactorAuthManager {

    private final OmniCord plugin;
    private final Map<UUID, BukkitTask> pendingTasks = new ConcurrentHashMap<>();

    public TwoFactorAuthManager(OmniCord plugin) {
        this.plugin = plugin;
    }

    public boolean isPending(UUID uuid) {
        return pendingTasks.containsKey(uuid);
    }

    public boolean isDiscordConfigured() {
        if (plugin.getDiscordBotManager() == null || !plugin.getDiscordBotManager().isConnected()) return false;
        String token = plugin.getConfigManager().getConfig().getString("bot.token", "");
        String guildId = plugin.getConfigManager().getConfig().getString("bot.guild_id", "0");
        if (token == null || token.isEmpty() || token.equals("PASTE_YOUR_DISCORD_BOT_TOKEN_HERE")) return false;
        if (guildId == null || guildId.equals("0") || guildId.equals("000000000000000000")) return false;
        return true;
    }

    public boolean requires2FA(Player player) {
        if (!isDiscordConfigured()) return false;

        if (!plugin.getConfigManager().getConfig().getBoolean("modules.two_factor_auth", true) ||
            !plugin.getConfigManager().getTwoFactor().getBoolean("enabled", true)) {
            return false;
        }

        boolean requireForOp = plugin.getConfigManager().getTwoFactor().getBoolean("require_for_op", true);
        boolean requirePerm = plugin.getConfigManager().getTwoFactor().getBoolean("require_by_permission", true);

        return (requireForOp && player.isOp()) || (requirePerm && player.hasPermission("omnicord.2fa"));
    }

    public void initiate2FA(Player player) {
        if (!isDiscordConfigured()) return;

        UUID uuid = player.getUniqueId();
        String ip = player.getAddress() != null ? player.getAddress().getHostString() : "Unknown";

        plugin.getAccountLinkingManager().getLink(uuid).thenAccept(opt -> {
            if (!opt.isPresent()) {
                // If not linked and 2FA is required
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;

                    boolean forceLink = plugin.getConfigManager().getTwoFactor().getBoolean("force_link_for_2fa", false);
                    if (forceLink) {
                        player.sendMessage(ColorUtil.colorize("&c&l[2FA SECURITY] &cYour account requires 2FA protection, but is not linked to Discord!"));
                        player.sendMessage(ColorUtil.colorize("&c&l[2FA SECURITY] &ePlease run &b/discord link &eto link your account before you can play."));
                        
                        int timeoutSeconds = plugin.getConfigManager().getTwoFactor().getInt("timeout_seconds", 60);
                        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (pendingTasks.containsKey(uuid)) {
                                pendingTasks.remove(uuid);
                                if (player.isOnline()) {
                                    String kickMsg = ColorUtil.colorize(plugin.getConfigManager().getTwoFactor().getString("kick_message_timeout", "&c[2FA] Verification timed out! Please link via Discord."));
                                    player.kickPlayer(kickMsg);
                                }
                            }
                        }, timeoutSeconds * 20L);
                        pendingTasks.put(uuid, task);
                    } else {
                        player.sendMessage(ColorUtil.colorize("&e❖ &6[2FA Security] &fYour account is not linked to Discord. Link your account with &b/discord link &fto enable 2FA protection!"));
                    }
                });
                return;
            }

            LinkedUser linkedUser = opt.get();
            int timeoutSeconds = plugin.getConfigManager().getTwoFactor().getInt("timeout_seconds", 60);

            // Schedule timeout kick on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;

                BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (pendingTasks.containsKey(uuid)) {
                        pendingTasks.remove(uuid);
                        if (player.isOnline()) {
                            String kickMsg = ColorUtil.colorize(plugin.getConfigManager().getTwoFactor().getString("kick_message_timeout", "&c[2FA] Login timed out."));
                            player.kickPlayer(kickMsg);
                        }
                    }
                }, timeoutSeconds * 20L);

                pendingTasks.put(uuid, task);
            });

            // Send Discord DM
            if (plugin.getDiscordBotManager().getJda() != null) {
                plugin.getDiscordBotManager().getJda().retrieveUserById(linkedUser.getDiscordId()).queue(user -> {
                    if (user != null) {
                        sendVerificationDm(user, player, uuid, ip);
                    }
                }, err -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) {
                            player.sendMessage(ColorUtil.colorize("&c[2FA Error] Could not retrieve your Discord user account (" + linkedUser.getDiscordId() + ")."));
                        }
                    });
                });
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    player.sendMessage(ColorUtil.colorize("&8&m----------------------------------------"));
                    player.sendMessage(plugin.getConfigManager().getGlobalPrefix() + ColorUtil.colorize("&e&l2FA Verification Required!"));
                    player.sendMessage(ColorUtil.colorize("  &7A login authorization prompt was sent to your Discord."));
                    player.sendMessage(ColorUtil.colorize("  &7Click &#57F287[Approve Login] &7in your Discord DMs to proceed."));
                    player.sendMessage(ColorUtil.colorize("  &cYou have &e" + timeoutSeconds + " seconds &cbefore being kicked."));
                    player.sendMessage(ColorUtil.colorize("&8&m----------------------------------------"));
                }
            });
        });
    }

    private void sendVerificationDm(User user, Player player, UUID uuid, String ip) {
        String serverName = plugin.getConfigManager().getConfig().getString("general.server_name", "Minecraft Server");
        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(new Color(237, 66, 69));
        eb.setTitle("🛡️ 2FA Login Authorization Request");
        eb.setDescription("A login attempt was detected on **" + serverName + "** for your linked account: **" + player.getName() + "**");

        eb.addField("🌐 IP Address", "`" + ip + "`", true);
        eb.addField("⏰ Time", java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss").format(java.time.LocalTime.now()), true);
        eb.addField("⚠️ Security Alert", "If this was you, click **Approve Login** below. If this wasn't you, click **Deny & Kick** immediately!", false);

        eb.setFooter("OmniCord Security Shield", null);
        eb.setTimestamp(Instant.now());

        user.openPrivateChannel().queue(channel -> {
            channel.sendMessageEmbeds(eb.build())
                    .addActionRow(
                            Button.success("2fa_approve:" + uuid, "✅ Approve Login"),
                            Button.danger("2fa_deny:" + uuid, "❌ Deny & Kick")
                    ).queue(null, err -> {
                        // DM failed (DMs closed)
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (player.isOnline()) {
                                player.sendMessage(ColorUtil.colorize("&c[2FA Error] Could not send Discord DM! Please enable 'Allow Direct Messages from server members' in your Discord Privacy Settings."));
                            }
                        });
                    });
        }, err -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    player.sendMessage(ColorUtil.colorize("&c[2FA Error] Could not open private message channel with your Discord user."));
                }
            });
        });
    }

    public void approve(UUID uuid) {
        BukkitTask task = pendingTasks.remove(uuid);
        if (task != null) task.cancel();

        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.sendMessage(plugin.getConfigManager().getGlobalPrefix() + ColorUtil.colorize("&#57F287&l2FA Verified! &aYou may now play freely."));
                try {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                } catch (Throwable ignored) {}
            }
        });
    }

    public void deny(UUID uuid) {
        BukkitTask task = pendingTasks.remove(uuid);
        if (task != null) task.cancel();

        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                String kickMsg = ColorUtil.colorize(plugin.getConfigManager().getTwoFactor().getString("kick_message_denied", "&c[2FA] Login denied from Discord."));
                player.kickPlayer(kickMsg);
            }
        });
    }

    public void clear(UUID uuid) {
        BukkitTask task = pendingTasks.remove(uuid);
        if (task != null) task.cancel();
    }
}
