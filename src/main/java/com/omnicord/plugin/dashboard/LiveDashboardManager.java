package com.omnicord.plugin.dashboard;

import com.omnicord.plugin.OmniCord;
import com.omnicord.plugin.utils.ServerUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages the self-updating Discord Live Status Dashboard embed.
 */
public class LiveDashboardManager {

    private final OmniCord plugin;
    private BukkitTask updateTask;
    // FIX Bug #14: volatile prevents stale reads across async Bukkit timer thread and JDA callback threads
    private volatile String dashboardMessageId;

    public LiveDashboardManager(OmniCord plugin) {
        this.plugin = plugin;
    }

    public void startDashboard() {
        if (!plugin.getConfigManager().getConfig().getBoolean("modules.live_dashboard", true) ||
            !plugin.getConfigManager().getDashboard().getBoolean("enabled", true)) {
            return;
        }

        this.dashboardMessageId = plugin.getConfigManager().getDashboard().getString("pinned_message_id", "");
        int interval = Math.max(10, plugin.getConfigManager().getDashboard().getInt("update_interval_seconds", 15));

        this.updateTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::updateDashboard, 100L, interval * 20L);
    }

    public void stopDashboard() {
        if (updateTask != null) {
            updateTask.cancel();
        }
    }

    public void updateDashboard() {
        if (!plugin.getDiscordBotManager().isConnected()) return;

        String channelId = plugin.getConfigManager().getDashboard().getString("channel_id", "0");
        if (channelId == null || channelId.isEmpty() || "0".equals(channelId) || "000000000000000000".equals(channelId)) {
            return;
        }

        TextChannel channel = plugin.getDiscordBotManager().getChannel(channelId);
        if (channel == null || !channel.canTalk()) return;

        MessageEmbed embed = buildDashboardEmbed();
        List<Button> buttons = buildDashboardButtons();

        if (dashboardMessageId == null || dashboardMessageId.isEmpty()) {
            // Send new message and remember its ID
            var action = channel.sendMessageEmbeds(embed);
            if (!buttons.isEmpty()) {
                action = action.setActionRow(buttons);
            }
            action.queue(msg -> {
                this.dashboardMessageId = msg.getId();
                plugin.getConfigManager().getDashboard().set("pinned_message_id", dashboardMessageId);
                try {
                    plugin.getConfigManager().getDashboard().save(new java.io.File(plugin.getDataFolder(), "dashboard.yml"));
                } catch (Exception ignored) {}
            }, err -> {});
        } else {
            // Edit existing message
            var editAction = channel.editMessageEmbedsById(dashboardMessageId, embed);
            if (!buttons.isEmpty()) {
                editAction = editAction.setActionRow(buttons);
            }
            editAction.queue(null, err -> {
                // Message might have been deleted, reset ID to recreate on next tick
                this.dashboardMessageId = "";
            });
        }
    }

    public MessageEmbed buildDashboardEmbed() {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(new Color(87, 242, 135));

        String serverName = plugin.getConfigManager().getConfig().getString("general.server_name", "Minecraft Server");
        String serverIp = plugin.getConfigManager().getConfig().getString("general.server_ip", "play.example.com");
        eb.setTitle("⚡ " + serverName + " — Live Status Dashboard");

        double[] tps = ServerUtil.getTPS();
        double currentTps = Math.min(20.0, tps[0]);
        String tpsBar = createProgressBar((int) currentTps, 20, 10);

        int memPct = ServerUtil.getMemoryPercentage();
        String memBar = createProgressBar(memPct, 100, 10);

        Collection<? extends Player> players = Bukkit.getOnlinePlayers();
        int online = players.size();
        int max = Bukkit.getMaxPlayers();
        String playerBar = createProgressBar(online, Math.max(1, max), 10);

        eb.addField("👥 Online Players (" + online + "/" + max + ")",
                playerBar + "\n" + (players.isEmpty() ? "*No players online*" :
                        players.stream().limit(20).map(p -> "`" + p.getName() + "`").collect(Collectors.joining(", ")) +
                                (players.size() > 20 ? " *(and " + (players.size() - 20) + " more)*" : "")), false);

        eb.addField("⚡ Server Performance (TPS)",
                tpsBar + " `" + String.format("%.2f", currentTps) + " TPS` (MSPT: `" + String.format("%.1f", ServerUtil.getMSPT()) + "ms`)", true);

        eb.addField("💾 RAM Utilization",
                memBar + " `" + memPct + "%` (" + ServerUtil.getMemoryUsage() + ")", true);

        eb.addField("🌐 Direct Connection", "`" + serverIp + "`", true);
        eb.addField("⏱️ Server Uptime", "`" + ServerUtil.getUptime() + "`", true);

        eb.setFooter("OmniCord Live Dashboard • Auto-updated");
        eb.setTimestamp(Instant.now());

        return eb.build();
    }

    private List<Button> buildDashboardButtons() {
        List<Button> buttons = new ArrayList<>();
        if (plugin.getConfigManager().getDashboard().getBoolean("buttons.copy_ip.enabled", true)) {
            buttons.add(Button.primary("dashboard_copy_ip", plugin.getConfigManager().getDashboard().getString("buttons.copy_ip.label", "📋 Copy Server IP")));
        }

        String storeUrl = plugin.getConfigManager().getDashboard().getString("buttons.store_link.url", "");
        if (storeUrl != null && !storeUrl.isEmpty() && (storeUrl.startsWith("http://") || storeUrl.startsWith("https://"))) {
            buttons.add(Button.link(storeUrl, plugin.getConfigManager().getDashboard().getString("buttons.store_link.label", "🛒 Store")));
        }

        if (plugin.getConfigManager().getDashboard().getBoolean("buttons.refresh.enabled", true)) {
            buttons.add(Button.secondary("dashboard_refresh", plugin.getConfigManager().getDashboard().getString("buttons.refresh.label", "🔄 Force Refresh")));
        }

        return buttons;
    }

    private String createProgressBar(int current, int max, int totalBlocks) {
        float percent = (float) current / Math.max(1, max);
        int filled = Math.min(totalBlocks, Math.max(0, Math.round(percent * totalBlocks)));
        int unfilled = totalBlocks - filled;

        StringBuilder sb = new StringBuilder("`[");
        for (int i = 0; i < filled; i++) sb.append("█");
        for (int i = 0; i < unfilled; i++) sb.append("░");
        sb.append("]`");
        return sb.toString();
    }
}
