package com.omnicord.plugin.integrations;

import com.omnicord.plugin.OmniCord;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.awt.Color;
import java.time.Instant;

/**
 * Ecosystem Hook for PantherAC, PantherAC Lite, and PantherAC Premium by PantherYTac.
 * Provides real-time Discord Watchdog alerts with 1-click staff moderation action buttons.
 */
public class PantherACHook implements Listener {

    private final OmniCord plugin;
    private boolean available = false;
    private String acVariant = "None";

    public PantherACHook(OmniCord plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        Plugin pacPremium = Bukkit.getPluginManager().getPlugin("PantherAC-Premium");
        Plugin pac = Bukkit.getPluginManager().getPlugin("PantherAC");
        Plugin pacLite = Bukkit.getPluginManager().getPlugin("PantherAC-Lite");

        if (pacPremium != null && pacPremium.isEnabled()) {
            this.available = true;
            this.acVariant = "PantherAC Premium (Neural Heuristics)";
        } else if (pac != null && pac.isEnabled()) {
            this.available = true;
            this.acVariant = "PantherAC (Standard)";
        } else if (pacLite != null && pacLite.isEnabled()) {
            this.available = true;
            this.acVariant = "PantherAC Lite";
        }

        if (available) {
            plugin.getLogger().info("✓ Hooked into " + acVariant + " (by PantherYTac) - Discord Watchdog Sentinel activated.");
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    /**
     * Dispatches an Anti-Cheat Watchdog Alert to Discord and staff chat.
     */
    public void sendWatchdogAlert(Player player, String checkName, int vl, String details) {
        if (!available || player == null) return;

        // 1. In-game staff broadcast
        String staffMsg = "§8[§cPantherAC§8] §f" + player.getName() + " §7failed §c" + checkName + " §8(§e+" + vl + " VL§8) §8[§7" + details + "§8]";
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("pantherac.alerts") || online.hasPermission("omnicord.staffchat") || online.isOp()) {
                online.sendMessage(staffMsg);
            }
        }

        // 2. Discord Staff Watchdog Channel
        if (plugin.getDiscordBotManager() != null && plugin.getDiscordBotManager().isConnected()) {
            String channelId = plugin.getConfigManager().getConfig().getString("channels.alerts", "");
            if (channelId.isEmpty() || "0".equals(channelId)) {
                channelId = plugin.getConfigManager().getConfig().getString("channels.reports", "");
            }
            if (channelId.isEmpty() || "0".equals(channelId)) {
                channelId = plugin.getConfigManager().getConfig().getString("channels.staff_chat", "");
            }
            if (channelId.isEmpty() || "0".equals(channelId)) return;

            TextChannel channel = plugin.getDiscordBotManager().getChannel(channelId);
            if (channel == null || !channel.canTalk()) return;

            EmbedBuilder eb = new EmbedBuilder();
            eb.setColor(new Color(237, 66, 69)); // Discord Red
            eb.setTitle("🚨 " + acVariant + " Watchdog Alert");
            eb.setDescription("**Player:** `" + player.getName() + "`\n" +
                    "**Check:** `" + checkName + "`\n" +
                    "**Violation Level:** `" + vl + "`\n" +
                    "**Details:** `" + details + "`\n" +
                    "**Ping:** `" + player.getPing() + "ms` | **World:** `" + player.getWorld().getName() + "`");
            eb.setFooter("PantherAC Security Suite • Author: PantherYTac", null);
            eb.setTimestamp(Instant.now());

            channel.sendMessageEmbeds(eb.build())
                    .addActionRow(
                            Button.danger("pac_freeze:" + player.getName(), "⚡ Freeze"),
                            Button.danger("pac_kick:" + player.getName(), "👢 Kick"),
                            Button.danger("pac_ban:" + player.getName(), "🔨 Ban (PantherAC)"),
                            Button.secondary("pac_inspect:" + player.getName(), "👁️ Inspect")
                    ).queue();
        }
    }

    public boolean isAvailable() { return available; }
    public String getAcVariant() { return acVariant; }
}
