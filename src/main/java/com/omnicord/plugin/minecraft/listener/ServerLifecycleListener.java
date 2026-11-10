package com.omnicord.plugin.minecraft.listener;

import com.omnicord.plugin.OmniCord;
import com.omnicord.plugin.utils.ColorUtil;
import com.omnicord.plugin.utils.ServerUtil;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.Bukkit;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles server startup and shutdown broadcasts to Discord and Minecraft console with aesthetic formatting.
 */
public class ServerLifecycleListener {

    private final OmniCord plugin;

    public ServerLifecycleListener(OmniCord plugin) {
        this.plugin = plugin;
    }

    public void broadcastServerStart(long bootDurationMs) {
        if (!plugin.getDiscordBotManager().isConnected()) return;

        String channelId = plugin.getConfigManager().getConfig().getString("channels.chat", "0");
        TextChannel channel = plugin.getDiscordBotManager().getChannel(channelId);
        if (channel == null) return;

        Runtime runtime = Runtime.getRuntime();
        long maxMemoryMB = runtime.maxMemory() / (1024 * 1024);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%boot_time%", String.format("%.2f", bootDurationMs / 1000.0));
        placeholders.put("%server_version%", Bukkit.getVersion());
        placeholders.put("%java_version%", System.getProperty("java.version"));
        placeholders.put("%max_ram%", maxMemoryMB + " MB");
        placeholders.put("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()));
        placeholders.put("%max%", String.valueOf(Bukkit.getMaxPlayers()));

        MessageEmbed embed = plugin.getDiscordBotManager().getEmbedFactory().createEmbed(
                "server_start", placeholders, null, "Server"
        );

        if (embed != null) {
            channel.sendMessageEmbeds(embed).queue();
        }
    }

    public void broadcastServerStop() {
        if (!plugin.getDiscordBotManager().isConnected()) return;

        String channelId = plugin.getConfigManager().getConfig().getString("channels.chat", "0");
        TextChannel channel = plugin.getDiscordBotManager().getChannel(channelId);
        if (channel == null) return;

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%uptime%", ServerUtil.getUptime());
        placeholders.put("%server_name%", plugin.getConfigManager().getConfig().getString("general.server_name", "Minecraft Server"));

        MessageEmbed embed = plugin.getDiscordBotManager().getEmbedFactory().createEmbed(
                "server_stop", placeholders, null, "Server"
        );

        if (embed != null) {
            try {
                channel.sendMessageEmbeds(embed).complete();
            } catch (Exception ignored) {}
        }
    }
}
