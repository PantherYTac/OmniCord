package com.omnicord.plugin.integrations;

import com.omnicord.plugin.OmniCord;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.awt.Color;
import java.time.Instant;

/**
 * Ecosystem Hook for NeonClans by PantherYTac.
 * Provides Clan Chat to Discord synchronization, Clan War/Alliance announcements, and Clan Role Sync.
 */
public class NeonClansHook implements Listener {

    private final OmniCord plugin;
    private boolean available = false;

    public NeonClansHook(OmniCord plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        Plugin neonClans = Bukkit.getPluginManager().getPlugin("NeonClans");
        if (neonClans != null && neonClans.isEnabled()) {
            this.available = true;
            plugin.getLogger().info("✓ Hooked into NeonClans v" + neonClans.getDescription().getVersion() + " (by PantherYTac) - Clan Chat Bridge & War announcements active.");
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    /**
     * Bridges Clan Chat to Discord.
     */
    public void bridgeClanChat(String clanName, Player sender, String message) {
        if (!available || sender == null) return;

        if (plugin.getDiscordBotManager() != null && plugin.getDiscordBotManager().isConnected()) {
            String channelId = plugin.getConfigManager().getConfig().getString("channels.clan_chat", "");
            if (channelId.isEmpty() || "0".equals(channelId)) {
                channelId = plugin.getConfigManager().getConfig().getString("channels.chat", "0");
            }
            if (channelId.isEmpty() || "0".equals(channelId)) return;

            TextChannel channel = plugin.getDiscordBotManager().getChannel(channelId);
            if (channel == null || !channel.canTalk()) return;

            channel.sendMessage("🛡️ **[" + clanName + "]** `" + sender.getName() + "`: " + message).queue();
        }
    }

    /**
     * Broadcasts Clan War / Alliance events to Discord.
     */
    public void broadcastClanWar(String clanA, String clanB, String type) {
        if (!available) return;

        if (plugin.getDiscordBotManager() != null && plugin.getDiscordBotManager().isConnected()) {
            String channelId = plugin.getConfigManager().getConfig().getString("channels.announcements", "");
            if (channelId.isEmpty() || "0".equals(channelId)) {
                channelId = plugin.getConfigManager().getConfig().getString("channels.chat", "0");
            }
            if (channelId.isEmpty() || "0".equals(channelId)) return;

            TextChannel channel = plugin.getDiscordBotManager().getChannel(channelId);
            if (channel == null || !channel.canTalk()) return;

            EmbedBuilder eb = new EmbedBuilder();
            eb.setColor(new Color(235, 69, 158)); // Neon Pink
            eb.setTitle("⚔️ NeonClans: " + type);
            eb.setDescription("Clan **" + clanA + "** has declared " + type.toLowerCase() + " against Clan **" + clanB + "**!");
            eb.setFooter("NeonClans Integration • Author: PantherYTac", null);
            eb.setTimestamp(Instant.now());

            channel.sendMessageEmbeds(eb.build()).queue();
        }
    }

    public boolean isAvailable() { return available; }
}
