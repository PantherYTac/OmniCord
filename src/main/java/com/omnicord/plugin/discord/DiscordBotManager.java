package com.omnicord.plugin.discord;

import com.omnicord.plugin.OmniCord;
import com.omnicord.plugin.discord.embeds.EmbedFactory;
import com.omnicord.plugin.discord.listener.DiscordButtonListener;
import com.omnicord.plugin.discord.listener.DiscordChatListener;
import com.omnicord.plugin.discord.listener.DiscordSlashCommandListener;
import com.omnicord.plugin.discord.webhook.WebhookManager;
import com.omnicord.plugin.utils.ServerUtil;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;

/**
 * Manages the Discord Bot lifecycle, JDA instance, commands, and presence tasks.
 */
public class DiscordBotManager {

    private final OmniCord plugin;
    private JDA jda;
    private Guild guild;
    private WebhookManager webhookManager;
    private EmbedFactory embedFactory;
    private DiscordSlashCommandListener slashCommandListener;
    private BukkitTask presenceTask;
    private BukkitTask voiceStatsTask;
    private int currentActivityIndex = 0;
    private String lastConnectionError = null;

    public DiscordBotManager(OmniCord plugin) {
        this.plugin = plugin;
    }

    public void startBot() {
        String token = plugin.getConfigManager().getConfig().getString("bot.token", "").trim();
        String guildId = plugin.getConfigManager().getConfig().getString("bot.guild_id", "").trim();

        if (token.isEmpty() || "PASTE_YOUR_DISCORD_BOT_TOKEN_HERE".equalsIgnoreCase(token)) {
            plugin.getLogger().warning("=========================================================");
            plugin.getLogger().warning(" OmniCord: Discord Bot token is not configured!");
            plugin.getLogger().warning(" Please set your bot token in plugins/OmniCord/config.yml");
            plugin.getLogger().warning("=========================================================");
            this.lastConnectionError = "Bot token is not configured in config.yml";
            return;
        }

        this.webhookManager = new WebhookManager(plugin);
        this.embedFactory = new EmbedFactory(plugin);
        this.slashCommandListener = new DiscordSlashCommandListener(plugin);

        // Attempt 1: Full Privileged Mode (Guild Members + Message Content)
        boolean started = tryStartBot(token, guildId, true);
        if (!started) {
            // Attempt 2: Standard Mode (Message Content without Server Members Intent)
            plugin.getLogger().warning("Retrying Discord connection in Standard Gateway Mode (without Server Members Intent)...");
            started = tryStartBot(token, guildId, false);
        }

        if (started) {
            this.lastConnectionError = null;
        }
    }

    private boolean tryStartBot(String token, String guildId, boolean withMembersIntent) {
        try {
            plugin.getLogger().info("Connecting to Discord Gateway" + (withMembersIntent ? " (Full Privileged Mode)..." : " (Standard Mode)..."));

            JDABuilder builder = JDABuilder.createDefault(token);
            if (withMembersIntent) {
                builder.enableIntents(
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.DIRECT_MESSAGES,
                        GatewayIntent.MESSAGE_CONTENT,
                        GatewayIntent.GUILD_MEMBERS,
                        GatewayIntent.GUILD_EMOJIS_AND_STICKERS
                );
                builder.setMemberCachePolicy(MemberCachePolicy.ALL);
            } else {
                builder.enableIntents(
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.DIRECT_MESSAGES,
                        GatewayIntent.MESSAGE_CONTENT,
                        GatewayIntent.GUILD_EMOJIS_AND_STICKERS
                );
                builder.setMemberCachePolicy(MemberCachePolicy.DEFAULT);
            }

            builder.addEventListeners(
                    new DiscordChatListener(plugin),
                    slashCommandListener,
                    new DiscordButtonListener(plugin)
            );
            builder.setActivity(Activity.playing(plugin.getConfigManager().getConfig().getString("bot.initial_activity", "Minecraft Server")));

            this.jda = builder.build();
            this.jda.awaitReady();

            if (!guildId.isEmpty() && !"000000000000000000".equals(guildId)) {
                this.guild = jda.getGuildById(guildId);
            }
            if (this.guild == null && !jda.getGuilds().isEmpty()) {
                this.guild = jda.getGuilds().get(0);
            }

            if (this.guild != null) {
                plugin.getLogger().info("Discord Bot successfully bound to server: " + guild.getName() + " (" + guild.getId() + ")");
                registerSlashCommands();
            } else {
                plugin.getLogger().warning("Bot is connected, but no matching Discord Server (Guild) was found!");
            }

            startPresenceRotation();
            startVoiceStatsUpdater();
            return true;

        } catch (InvalidTokenException e) {
            this.lastConnectionError = "Invalid Bot Token! Please re-copy the bot token from the Discord Developer Portal.";
            plugin.getLogger().severe("Failed to start Discord Bot: " + lastConnectionError);
            if (this.jda != null) {
                try { this.jda.shutdownNow(); } catch (Exception ignored) {}
                this.jda = null;
            }
            return false;
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            if (msg.contains("4014") || msg.toLowerCase().contains("disallowed intent")) {
                this.lastConnectionError = "Disallowed Gateway Intents (Code 4014)! Ensure 'Message Content Intent' is turned ON in Discord Developer Portal -> Bot -> Privileged Gateway Intents.";
            } else {
                this.lastConnectionError = msg;
            }
            plugin.getLogger().severe("Failed to start Discord Bot: " + this.lastConnectionError);
            if (this.jda != null) {
                try { this.jda.shutdownNow(); } catch (Exception ignored) {}
                this.jda = null;
            }
            return false;
        }
    }

    private void registerSlashCommands() {
        if (guild == null) return;

        List<net.dv8tion.jda.api.interactions.commands.build.SlashCommandData> commandDataList = new java.util.ArrayList<>(java.util.Arrays.asList(
                Commands.slash("playerlist", "View online players, server TPS, and uptime"),
                Commands.slash("lookup", "Look up a player's profile and Discord link status")
                        .addOption(OptionType.STRING, "player", "Minecraft username", true),
                Commands.slash("tps", "View real-time TPS, MSPT, and memory diagnostics"),
                Commands.slash("execute", "Execute a console command (Admin only)")
                        .addOption(OptionType.STRING, "command", "Command to run", true),
                Commands.slash("link", "Link your Discord account to Minecraft")
                        .addOption(OptionType.STRING, "code", "6-digit linking code generated in-game", true),
                Commands.slash("rewards", "Check your Discord linking rewards"),
                Commands.slash("leaderboard", "View top server leaderboards")
                        .addOption(OptionType.STRING, "type", "Leaderboard type (balance, joined)", false)
        ));

        // Add custom slash commands from custom_commands.yml
        if (plugin.getCustomCommandManager() != null) {
            commandDataList.addAll(plugin.getCustomCommandManager().getCustomSlashCommands());
        }

        // Add slash commands registered by addons
        if (plugin.getAddonManager() != null) {
            commandDataList.addAll(plugin.getAddonManager().getAddonSlashCommands());
        }

        guild.updateCommands().addCommands(commandDataList).queue(
                cmds -> plugin.getLogger().info("Successfully registered " + cmds.size() + " Discord Slash Commands (including custom & addons)."),
                err -> plugin.getLogger().warning("Failed to register Discord Slash Commands: " + err.getMessage())
        );
    }

    public void reloadPresence() {
        if (presenceTask != null) {
            presenceTask.cancel();
            presenceTask = null;
        }
        boolean enabled = plugin.getConfigManager().getConfig().getBoolean("modules.presence_rotation", true) &&
                          plugin.getConfigManager().getConfig().getBoolean("presence.enabled", true);
        if (!enabled || jda == null) return;

        int interval = Math.max(5, plugin.getConfigManager().getConfig().getInt("presence.cycle_interval_seconds", 15));
        java.util.List<java.util.Map<?, ?>> activities = plugin.getConfigManager().getConfig().getMapList("presence.activities");

        if (activities.isEmpty()) return;
        currentActivityIndex = 0;

        this.presenceTask = org.bukkit.Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (jda == null || activities.isEmpty()) return;

            java.util.Map<?, ?> item = activities.get(currentActivityIndex % activities.size());
            currentActivityIndex++;

            Object typeObj = item.get("type");
            Object textObj = item.get("text");

            String typeStr = (typeObj != null ? typeObj.toString() : "PLAYING").toUpperCase();
            String rawText = textObj != null ? textObj.toString() : "";

            String text = rawText
                    .replace("%online%", String.valueOf(org.bukkit.Bukkit.getOnlinePlayers().size()))
                    .replace("%max%", String.valueOf(org.bukkit.Bukkit.getMaxPlayers()))
                    .replace("%tps%", com.omnicord.plugin.utils.ServerUtil.getFormattedTPS())
                    .replace("%uptime%", com.omnicord.plugin.utils.ServerUtil.getUptime())
                    .replace("%server_name%", plugin.getConfigManager().getConfig().getString("general.server_name", "Minecraft"))
                    .replace("%server_ip%", plugin.getConfigManager().getConfig().getString("general.server_ip", "play.example.com"));

            net.dv8tion.jda.api.entities.Activity activity;
            switch (typeStr) {
                case "WATCHING": activity = net.dv8tion.jda.api.entities.Activity.watching(text); break;
                case "LISTENING": activity = net.dv8tion.jda.api.entities.Activity.listening(text); break;
                case "COMPETING": activity = net.dv8tion.jda.api.entities.Activity.competing(text); break;
                default: activity = net.dv8tion.jda.api.entities.Activity.playing(text); break;
            }

            jda.getPresence().setActivity(activity);
        }, 40L, interval * 20L);
    }

    private void startPresenceRotation() {
        reloadPresence();
    }

    private void startVoiceStatsUpdater() {
        boolean enabled = plugin.getConfigManager().getConfig().getBoolean("voice_channel_stats.enabled", false);
        if (!enabled || guild == null) return;

        String channelId = plugin.getConfigManager().getConfig().getString("voice_channel_stats.channel_id", "0");
        if ("0".equals(channelId) || channelId.isEmpty()) return;

        // Discord channel name edits are heavily rate-limited (2 per 10 minutes)
        int interval = Math.max(300, plugin.getConfigManager().getConfig().getInt("voice_channel_stats.update_interval_seconds", 300));
        String nameFormat = plugin.getConfigManager().getConfig().getString("voice_channel_stats.channel_name_format", "🎮 Online: %online%/%max%");

        this.voiceStatsTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (guild == null) return;
            VoiceChannel vc = guild.getVoiceChannelById(channelId);
            if (vc != null) {
                String newName = nameFormat
                        .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                        .replace("%max%", String.valueOf(Bukkit.getMaxPlayers()))
                        .replace("%server_name%", plugin.getConfigManager().getConfig().getString("general.server_name", "Minecraft"));

                if (!vc.getName().equals(newName)) {
                    vc.getManager().setName(newName).queue(null, err -> {});
                }
            }
        }, 100L, interval * 20L);
    }

    public void stopBot() {
        if (presenceTask != null) presenceTask.cancel();
        if (voiceStatsTask != null) voiceStatsTask.cancel();
        if (webhookManager != null) webhookManager.clearCache();

        if (jda != null) {
            try {
                plugin.getLogger().info("Disconnecting Discord Bot...");
                jda.shutdownNow();
            } catch (Exception e) {
                plugin.getLogger().warning("Error during JDA shutdown: " + e.getMessage());
            }
            this.jda = null;
            this.guild = null;
        }
    }

    public String getLastConnectionError() {
        return lastConnectionError;
    }

    public boolean isConnected() {
        return jda != null && jda.getStatus() == JDA.Status.CONNECTED;
    }

    public JDA getJda() {
        return jda;
    }

    public Guild getGuild() {
        return guild;
    }

    public WebhookManager getWebhookManager() {
        return webhookManager;
    }

    public EmbedFactory getEmbedFactory() {
        return embedFactory;
    }

    public DiscordSlashCommandListener getSlashCommandListener() {
        return slashCommandListener;
    }

    public TextChannel getChannel(String channelId) {
        if (jda == null || channelId == null || "0".equals(channelId) || channelId.isEmpty()) return null;
        return jda.getTextChannelById(channelId);
    }
}
