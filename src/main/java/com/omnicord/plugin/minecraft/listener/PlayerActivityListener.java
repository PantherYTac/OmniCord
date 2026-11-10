package com.omnicord.plugin.minecraft.listener;

import com.omnicord.plugin.OmniCord;
import com.omnicord.plugin.utils.ColorUtil;
import com.omnicord.plugin.utils.SkinUtil;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;

/**
 * Listens for in-game player events (Join, Quit, Death, PvP Kill, Advancements)
 * and broadcasts aesthetic in-game chat messages and Discord embeds.
 */
public class PlayerActivityListener implements Listener {

    private final OmniCord plugin;

    public PlayerActivityListener(OmniCord plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (plugin.getConfigManager().getConfig().getBoolean("linking.require_link_to_join", false)) {
            boolean isLinked;
            try {
                // AsyncPlayerPreLoginEvent fires on async thread — blocking .get() is acceptable here
                // but we must handle InterruptedException and ExecutionException explicitly
                isLinked = plugin.getDatabaseManager().getStorage().getLinkByUuid(event.getUniqueId()).get().isPresent();
            } catch (java.util.concurrent.ExecutionException ex) {
                // DB failure — log and fail open to avoid false-positive kicks on lag spikes
                plugin.getLogger().warning("[OmniCord] Failed to check linking status for " + event.getName() +
                        " during pre-login: " + ex.getCause().getMessage() + " — allowing join (fail-open).");
                return;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                plugin.getLogger().warning("[OmniCord] Pre-login check interrupted for " + event.getName() + " — allowing join.");
                return;
            }

            if (!isLinked) {
                String invite = plugin.getConfigManager().getConfig().getString("linking.discord_invite_url", "https://discord.gg/yourserver");
                String kickMsg = plugin.getConfigManager().getConfig().getString("linking.kick_message_not_linked",
                        "&#ED4245✘ [OmniCord] You must link your Discord account before joining!\n&#7289DAJoin our Discord: &#5865F2%discord_invite% &7and run &#8A9CFE/link")
                        .replace("%discord_invite%", invite)
                        .replace("%player%", event.getName());
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST, ColorUtil.colorize(kickMsg));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        boolean isFirstJoin = !player.hasPlayedBefore();

        // 1. In-game aesthetic join broadcast
        if (plugin.getConfigManager().getMessages().getBoolean("events.custom_in_game_messages", true)) {
            if (isFirstJoin) {
                event.setJoinMessage(null); // Replace vanilla message
                for (String line : plugin.getConfigManager().getMessageList("events.first_join_minecraft")) {
                    String formatted = line.replace("%player%", player.getName())
                            .replace("%unique_joins%", String.valueOf(Bukkit.getOfflinePlayers().length));
                    Bukkit.broadcastMessage(formatted);
                }
                playServerSound(Sound.UI_TOAST_CHALLENGE_COMPLETE);
            } else {
                String joinMsg = plugin.getConfigManager().getMessage("events.join_minecraft", "")
                        .replace("%player%", player.getName())
                        .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                        .replace("%max%", String.valueOf(Bukkit.getMaxPlayers()));
                if (!joinMsg.isEmpty()) {
                    event.setJoinMessage(joinMsg);
                }
            }
        }

        // 2. Discord Embed
        if (plugin.getDiscordBotManager().isConnected()) {
            String channelId = plugin.getConfigManager().getConfig().getString("channels.chat", "0");
            TextChannel channel = plugin.getDiscordBotManager().getChannel(channelId);
            if (channel != null) {
                String embedKey = isFirstJoin ? "first_join" : "join";

                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("%player%", player.getName());
                placeholders.put("%displayname%", ColorUtil.stripColor(player.getDisplayName()));
                placeholders.put("%uuid%", player.getUniqueId().toString());
                placeholders.put("%unique_joins%", String.valueOf(Bukkit.getOfflinePlayers().length));
                placeholders.put("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()));
                placeholders.put("%max%", String.valueOf(Bukkit.getMaxPlayers()));

                MessageEmbed embed = plugin.getDiscordBotManager().getEmbedFactory().createEmbed(
                        embedKey, placeholders, player.getUniqueId(), player.getName()
                );

                if (embed != null) {
                    channel.sendMessageEmbeds(embed).queue();
                }
            }
        }

        // Auto-sync roles on join
        if (plugin.getConfigManager().getRoleSync().getBoolean("sync.sync_on_join", true)) {
            plugin.getAccountLinkingManager().getLink(player.getUniqueId()).thenAccept(opt -> {
                opt.ifPresent(user -> plugin.getRoleSyncManager().syncUser(player.getUniqueId(), user.getDiscordId()));
            });
        }

        // Admin / OP setup notice if Discord is unconfigured
        if (player.isOp() || player.hasPermission("omnicord.admin")) {
            String token = plugin.getConfigManager().getConfig().getString("bot.token", "");
            String guildId = plugin.getConfigManager().getConfig().getString("bot.guild_id", "0");
            String invite = plugin.getConfigManager().getConfig().getString("linking.discord_invite_url", "https://discord.gg/yourserver");

            boolean unconfiguredBot = token.isEmpty() || token.equals("PASTE_YOUR_DISCORD_BOT_TOKEN_HERE") || "0".equals(guildId) || "000000000000000000".equals(guildId);
            boolean unconfiguredInvite = invite.isEmpty() || invite.equals("https://discord.gg/yourserver");

            if (unconfiguredBot || unconfiguredInvite) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        player.sendMessage(ColorUtil.colorize("&#ED4245╭──────────────────────────────────────────────────╮"));
                        player.sendMessage(ColorUtil.colorize("&#ED4245│  &#ED4245⚠️ &#FFA8A8&lOmniCord Setup Reminder &8(Admin/OP)"));
                        if (unconfiguredBot) {
                            player.sendMessage(ColorUtil.colorize("&#ED4245│  &c• Discord Bot Token is not set in &econfig.yml"));
                        }
                        if (unconfiguredInvite) {
                            player.sendMessage(ColorUtil.colorize("&#ED4245│  &c• Discord Invite URL is not set in &econfig.yml"));
                        }
                        player.sendMessage(ColorUtil.colorize("&#ED4245│  &7Open &fplugins/OmniCord/config.yml &7and run &e/oc reload"));
                        player.sendMessage(ColorUtil.colorize("&#ED4245╰──────────────────────────────────────────────────╯"));
                    }
                }, 30L);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // 1. In-game aesthetic quit broadcast
        if (plugin.getConfigManager().getMessages().getBoolean("events.custom_in_game_messages", true)) {
            int remaining = Math.max(0, Bukkit.getOnlinePlayers().size() - 1);
            String quitMsg = plugin.getConfigManager().getMessage("events.quit_minecraft", "")
                    .replace("%player%", player.getName())
                    .replace("%online%", String.valueOf(remaining))
                    .replace("%max%", String.valueOf(Bukkit.getMaxPlayers()));
            if (!quitMsg.isEmpty()) {
                event.setQuitMessage(quitMsg);
            }
        }

        // 2. Discord Embed
        if (plugin.getDiscordBotManager().isConnected()) {
            String channelId = plugin.getConfigManager().getConfig().getString("channels.chat", "0");
            TextChannel channel = plugin.getDiscordBotManager().getChannel(channelId);
            if (channel != null) {
                int remaining = Math.max(0, Bukkit.getOnlinePlayers().size() - 1);
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("%player%", player.getName());
                placeholders.put("%displayname%", ColorUtil.stripColor(player.getDisplayName()));
                placeholders.put("%uuid%", player.getUniqueId().toString());
                placeholders.put("%online%", String.valueOf(remaining));
                placeholders.put("%max%", String.valueOf(Bukkit.getMaxPlayers()));

                MessageEmbed embed = plugin.getDiscordBotManager().getEmbedFactory().createEmbed(
                        "quit", placeholders, player.getUniqueId(), player.getName()
                );

                if (embed != null) {
                    channel.sendMessageEmbeds(embed).queue();
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Player killer = player.getKiller();
        Location loc = player.getLocation();

        String rawDeathMessage = event.getDeathMessage() != null ?
                ColorUtil.stripColor(event.getDeathMessage()) : player.getName() + " died.";

        boolean isPvP = (killer != null && !killer.equals(player));
        String weaponName = "Fists";
        int distance = 0;

        if (isPvP) {
            if (killer.getWorld().equals(player.getWorld())) {
                distance = (int) Math.round(killer.getLocation().distance(player.getLocation()));
            } else {
                distance = 0;
            }
            ItemStack mainHand = killer.getInventory().getItemInMainHand();
            if (mainHand != null && mainHand.getType() != Material.AIR) {
                ItemMeta meta = mainHand.getItemMeta();
                if (meta != null && meta.hasDisplayName()) {
                    weaponName = ColorUtil.stripColor(meta.getDisplayName());
                } else {
                    weaponName = formatMaterialName(mainHand.getType());
                }
            }
        }

        // 1. In-game aesthetic death broadcast
        if (plugin.getConfigManager().getMessages().getBoolean("events.custom_in_game_messages", true)) {
            if (isPvP) {
                String pvpMsg = plugin.getConfigManager().getMessage("events.pvp_kill_minecraft", "")
                        .replace("%player%", player.getName())
                        .replace("%killer%", killer.getName())
                        .replace("%weapon%", weaponName)
                        .replace("%distance%", String.valueOf(distance));
                if (!pvpMsg.isEmpty()) {
                    event.setDeathMessage(pvpMsg);
                }
            }
        }

        // 2. Discord Embed
        if (plugin.getDiscordBotManager().isConnected()) {
            String channelId = plugin.getConfigManager().getConfig().getString("channels.chat", "0");
            TextChannel channel = plugin.getDiscordBotManager().getChannel(channelId);
            if (channel != null) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("%player%", player.getName());
                placeholders.put("%death_message%", rawDeathMessage);
                placeholders.put("%world%", loc.getWorld() != null ? loc.getWorld().getName() : "world");
                placeholders.put("%x%", String.valueOf(loc.getBlockX()));
                placeholders.put("%y%", String.valueOf(loc.getBlockY()));
                placeholders.put("%z%", String.valueOf(loc.getBlockZ()));

                if (isPvP) {
                    placeholders.put("%killer%", killer.getName());
                    placeholders.put("%killer_uuid%", killer.getUniqueId().toString());
                    placeholders.put("%weapon%", weaponName);
                    placeholders.put("%distance%", String.valueOf(distance));
                    String avatarService = plugin.getConfigManager().getConfig().getString("chat_bridge.avatar_service", "crafthead");
                    placeholders.put("%killer_avatar_url%", SkinUtil.getAvatarUrl(avatarService, killer.getUniqueId(), killer.getName()));

                    MessageEmbed pvpEmbed = plugin.getDiscordBotManager().getEmbedFactory().createEmbed(
                            "pvp_kill", placeholders, player.getUniqueId(), player.getName()
                    );
                    if (pvpEmbed != null) {
                        channel.sendMessageEmbeds(pvpEmbed).queue();
                        return;
                    }
                }

                // Entity / Environment death fallback
                Entity killerEntity = getDamagingEntity(player);
                if (killerEntity != null && !(killerEntity instanceof Player)) {
                    placeholders.put("%entity%", formatMaterialNameString(killerEntity.getType().name()));
                } else {
                    placeholders.put("%entity%", "Nature");
                }

                MessageEmbed embed = plugin.getDiscordBotManager().getEmbedFactory().createEmbed(
                        "death", placeholders, player.getUniqueId(), player.getName()
                );

                if (embed != null) {
                    channel.sendMessageEmbeds(embed).queue();
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        Advancement advancement = event.getAdvancement();
        String key = advancement.getKey().getKey();

        // Filter out recipes and root advancement trees
        if (key.startsWith("recipes/") || key.endsWith("/root")) {
            return;
        }

        Player player = event.getPlayer();
        String cleanTitle = formatAdvancementName(key);
        String advType = determineAdvancementType(key);

        // 1. In-game broadcast
        if (plugin.getConfigManager().getMessages().getBoolean("events.custom_in_game_messages", true)) {
            String msgKey = "events.advancement_" + advType.toLowerCase() + "_minecraft";
            String inGameMsg = plugin.getConfigManager().getMessage(msgKey, "")
                    .replace("%player%", player.getName())
                    .replace("%advancement_title%", cleanTitle)
                    .replace("%advancement_type%", advType);

            if (inGameMsg.isEmpty()) {
                inGameMsg = plugin.getConfigManager().getMessage("events.advancement_minecraft", "")
                        .replace("%player%", player.getName())
                        .replace("%advancement_title%", cleanTitle)
                        .replace("%advancement_type%", advType);
            }
            if (!inGameMsg.isEmpty()) {
                Bukkit.broadcastMessage(inGameMsg);
            }
        }

        // 2. Discord Embed
        if (plugin.getDiscordBotManager().isConnected()) {
            String channelId = plugin.getConfigManager().getConfig().getString("channels.chat", "0");
            TextChannel channel = plugin.getDiscordBotManager().getChannel(channelId);
            if (channel != null) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("%player%", player.getName());
                placeholders.put("%advancement_title%", cleanTitle);
                placeholders.put("%advancement_description%", "Unlocked " + cleanTitle);
                placeholders.put("%advancement_type%", advType);

                String embedKey = "advancement_" + advType.toLowerCase();
                MessageEmbed embed = plugin.getDiscordBotManager().getEmbedFactory().createEmbed(
                        embedKey, placeholders, player.getUniqueId(), player.getName()
                );

                if (embed == null) {
                    embed = plugin.getDiscordBotManager().getEmbedFactory().createEmbed(
                            "advancement", placeholders, player.getUniqueId(), player.getName()
                    );
                }

                if (embed != null) {
                    channel.sendMessageEmbeds(embed).queue();
                }
            }
        }
    }

    private Entity getDamagingEntity(Player player) {
        if (player.getLastDamageCause() instanceof EntityDamageByEntityEvent) {
            return ((EntityDamageByEntityEvent) player.getLastDamageCause()).getDamager();
        }
        return null;
    }

    private String determineAdvancementType(String key) {
        String lower = key.toLowerCase();
        if (lower.contains("challenge") || lower.contains("how_did_we_get_here") || lower.contains("arbalistic") ||
            lower.contains("uneasy_alliance") || lower.contains("two_birds") || lower.contains("sniper_duel") ||
            lower.contains("subspace_bubble") || lower.contains("all_potions") || lower.contains("hero_of_the_village")) {
            return "Challenge";
        }
        if (lower.contains("goal") || lower.contains("beacon") || lower.contains("respawn_dragon") ||
            lower.contains("netherite") || lower.contains("caves") || lower.contains("explore")) {
            return "Goal";
        }
        return "Task";
    }

    private String formatAdvancementName(String rawKey) {
        String[] parts = rawKey.split("/");
        String name = parts[parts.length - 1].replace('_', ' ');
        StringBuilder title = new StringBuilder();
        for (String word : name.split(" ")) {
            if (!word.isEmpty()) {
                title.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase()).append(" ");
            }
        }
        return title.toString().trim();
    }

    private String formatMaterialName(Material material) {
        return formatMaterialNameString(material.name());
    }

    private String formatMaterialNameString(String name) {
        String[] parts = name.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private void playServerSound(Sound sound) {
        try {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.playSound(player.getLocation(), sound, 0.7f, 1.0f);
            }
        } catch (Throwable ignored) {}
    }
}
