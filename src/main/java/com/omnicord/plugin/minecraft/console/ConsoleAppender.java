package com.omnicord.plugin.minecraft.console;

import com.omnicord.plugin.OmniCord;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Universal console logger handler that streams filtered Minecraft console logs to the Discord console channel.
 * Compatible across Spigot, Paper, Purpur, Bukkit, and Folia.
 */
public class ConsoleAppender extends Handler {

    private static final Pattern ANSI_PATTERN = Pattern.compile("\\x1B\\[[0-9;]*[a-zA-Z]");
    private final OmniCord plugin;
    private final ConcurrentLinkedQueue<String> logQueue = new ConcurrentLinkedQueue<>();
    private BukkitTask batchFlushTask;
    // FIX Bug #16: Cache keyword list — fetching from config on every log line is a massive CPU bottleneck
    private volatile List<String> cachedKeywords = new java.util.ArrayList<>();

    public ConsoleAppender(OmniCord plugin) {
        this.plugin = plugin;
    }

    public void attach() {
        if (!plugin.getConfigManager().getConfig().getBoolean("modules.console_bridge", true) ||
            !plugin.getConfigManager().getConfig().getBoolean("console_bridge.stream_logs", true)) {
            return;
        }

        try {
            Logger rootLogger = Bukkit.getLogger();
            rootLogger.addHandler(this);
            // Cache the keyword list on attach
            refreshKeywords();
            startBatchFlusher();
            plugin.getLogger().info("Attached real-time Console Bridge Handler.");
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not attach Console Handler: " + t.getMessage());
        }
    }

    /** Call this from the plugin's reload method to refresh the cached keyword list. */
    public void refreshKeywords() {
        this.cachedKeywords = plugin.getConfigManager().getConfig().getStringList("console_bridge.sensitive_keywords");
    }

    public void detach() {
        if (batchFlushTask != null) {
            batchFlushTask.cancel();
        }

        try {
            Bukkit.getLogger().removeHandler(this);
        } catch (Throwable ignored) {}
    }

    @Override
    public void publish(LogRecord record) {
        if (record == null || record.getMessage() == null) return;

        String raw = record.getMessage();
        if (raw.isEmpty()) return;

        // Strip ANSI codes
        String clean = ANSI_PATTERN.matcher(raw).replaceAll("");

        // Format with level
        String formatted = "[" + record.getLevel().getName() + "] " + clean;

        // Sensitive keyword filter — use cached list to avoid config read on every line
        if (plugin.getConfigManager().getConfig().getBoolean("console_bridge.redact_sensitive_commands", true)) {
            List<String> keywords = cachedKeywords;
            for (String kw : keywords) {
                if (clean.toLowerCase().contains(kw.toLowerCase())) {
                    formatted = "[" + record.getLevel().getName() + "] [REDACTED COMMAND LOG]";
                    break;
                }
            }
        }

        logQueue.offer(formatted);
    }

    @Override
    public void flush() {}

    @Override
    public void close() throws SecurityException {
        detach();
    }

    private void startBatchFlusher() {
        this.batchFlushTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (logQueue.isEmpty() || !plugin.getDiscordBotManager().isConnected()) return;

            String channelId = plugin.getConfigManager().getConfig().getString("channels.console", "0");
            TextChannel channel = plugin.getDiscordBotManager().getChannel(channelId);
            if (channel == null || !channel.canTalk()) return;

            StringBuilder sb = new StringBuilder("```log\n");
            int count = 0;

            // Drop logs if queue is overflowing to prevent memory explosion
            if (logQueue.size() > 2000) {
                logQueue.clear();
                logQueue.offer("[WARN] Log queue overflowed. Too many messages. Cleared buffer.");
            }

            while (!logQueue.isEmpty() && count < 200) {
                String line = logQueue.poll();
                if (line == null) break;

                if (sb.length() + line.length() + 10 > 1900) {
                    sb.append("```");
                    channel.sendMessage(sb.toString()).queue();
                    sb = new StringBuilder("```log\n");
                }

                sb.append(line).append("\n");
                count++;
            }

            if (sb.length() > 8) {
                sb.append("```");
                channel.sendMessage(sb.toString()).queue();
            }
        }, 60L, 40L); // Flush every 2 seconds
    }
}
