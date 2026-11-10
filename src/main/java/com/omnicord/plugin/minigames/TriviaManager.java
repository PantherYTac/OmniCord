package com.omnicord.plugin.minigames;

import com.omnicord.plugin.OmniCord;
import com.omnicord.plugin.utils.ColorUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.awt.Color;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Cross-platform Trivia and Chat Minigame engine.
 */
public class TriviaManager {

    private final OmniCord plugin;
    private BukkitTask triviaLoopTask;
    private BukkitTask expirationTask;

    private String currentQuestion = null;
    private final List<String> currentAnswers = new CopyOnWriteArrayList<>();
    private volatile boolean active = false;

    public TriviaManager(OmniCord plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfigManager().getConfig().getBoolean("modules.chat_games", true) ||
            !plugin.getConfigManager().getTrivia().getBoolean("enabled", true)) {
            return;
        }

        int intervalMinutes = Math.max(1, plugin.getConfigManager().getTrivia().getInt("interval_minutes", 15));
        long ticks = intervalMinutes * 60L * 20L;

        this.triviaLoopTask = Bukkit.getScheduler().runTaskTimer(plugin, this::dispatchNextQuestion, 200L, ticks);
    }

    public void stop() {
        if (triviaLoopTask != null) triviaLoopTask.cancel();
        if (expirationTask != null) expirationTask.cancel();
        active = false;
        currentAnswers.clear();
    }

    public void dispatchNextQuestion() {
        List<Map<?, ?>> questions = plugin.getConfigManager().getTrivia().getMapList("questions");
        if (questions.isEmpty()) return;

        Map<?, ?> qMap = questions.get(new Random().nextInt(questions.size()));
        Object questionObj = qMap.get("question");
        Object answersObj = qMap.get("answers");

        if (questionObj == null || !(answersObj instanceof List)) return;

        this.currentQuestion = questionObj.toString();
        this.currentAnswers.clear();
        for (Object a : (List<?>) answersObj) {
            if (a != null) currentAnswers.add(a.toString().trim().toLowerCase());
        }

        if (this.currentAnswers.isEmpty()) return;

        this.active = true;
        int timeLimit = plugin.getConfigManager().getTrivia().getInt("time_limit_seconds", 45);

        // 1. Broadcast in Minecraft
        String prefix = plugin.getConfigManager().getGlobalPrefix();
        Bukkit.broadcastMessage(ColorUtil.colorize("&8&m----------------------------------------"));
        Bukkit.broadcastMessage(prefix + ColorUtil.colorize("&#FEE75C&lTRIVIA MINIGAME!"));
        Bukkit.broadcastMessage(ColorUtil.colorize("  &f" + currentQuestion));
        Bukkit.broadcastMessage(ColorUtil.colorize("  &7First to type the answer in MC or Discord wins!"));
        Bukkit.broadcastMessage(ColorUtil.colorize("  &7Time limit: &e" + timeLimit + "s"));
        Bukkit.broadcastMessage(ColorUtil.colorize("&8&m----------------------------------------"));

        // 2. Broadcast in Discord
        if (plugin.getDiscordBotManager().isConnected()) {
            String channelId = plugin.getConfigManager().getConfig().getString("channels.chat", "0");
            TextChannel channel = plugin.getDiscordBotManager().getChannel(channelId);
            if (channel != null && channel.canTalk()) {
                EmbedBuilder eb = new EmbedBuilder();
                eb.setColor(new Color(254, 231, 92));
                eb.setTitle("🧠 Cross-Platform Chat Trivia!");
                eb.setDescription("**" + currentQuestion + "**\n\n*Type the answer in this channel or in Minecraft to win!*");
                eb.setFooter("OmniCord Trivia • " + timeLimit + "s time limit", null);
                channel.sendMessageEmbeds(eb.build()).queue(null, err -> {});
            }
        }

        // Expiration task
        if (expirationTask != null) expirationTask.cancel();
        this.expirationTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (active) {
                active = false;
                Bukkit.broadcastMessage(prefix + ColorUtil.colorize("&cTime's up! No one answered the trivia question in time."));
                if (plugin.getDiscordBotManager().isConnected()) {
                    String channelId = plugin.getConfigManager().getConfig().getString("channels.chat", "0");
                    TextChannel channel = plugin.getDiscordBotManager().getChannel(channelId);
                    if (channel != null && channel.canTalk()) {
                        channel.sendMessage("⏰ **Time's up!** No one answered the trivia question in time.").queue(null, err -> {});
                    }
                }
            }
        }, timeLimit * 20L);
    }

    public synchronized boolean checkAnswer(String message, String playerName, UUID uuid, boolean fromDiscord, String discordTag) {
        if (!active || message == null) return false;

        String clean = message.trim().toLowerCase();
        boolean correct = currentAnswers.stream().anyMatch(a -> a.equalsIgnoreCase(clean));

        if (correct) {
            this.active = false;
            if (expirationTask != null) expirationTask.cancel();

            String prefix = plugin.getConfigManager().getGlobalPrefix();
            String platform = fromDiscord ? "Discord" : "Minecraft";
            String displayName = fromDiscord ? discordTag : playerName;

            // Broadcast winner in Minecraft
            Bukkit.getScheduler().runTask(plugin, () -> {
                Bukkit.broadcastMessage(ColorUtil.colorize("&8&m----------------------------------------"));
                Bukkit.broadcastMessage(prefix + ColorUtil.colorize("&#57F287&lWINNER! &#FFFFFF" + displayName + " &7answered correctly from &b" + platform + "&7!"));
                Bukkit.broadcastMessage(ColorUtil.colorize("&8&m----------------------------------------"));
            });

            // Broadcast winner in Discord
            if (plugin.getDiscordBotManager().isConnected()) {
                String channelId = plugin.getConfigManager().getConfig().getString("channels.chat", "0");
                TextChannel channel = plugin.getDiscordBotManager().getChannel(channelId);
                if (channel != null && channel.canTalk()) {
                    channel.sendMessage("🎉 **WINNER!** **" + displayName + "** answered correctly from **" + platform + "**!").queue(null, err -> {});
                }
            }

            // Reward
            if (uuid != null) {
                giveRewards(playerName, uuid, platform);
            }

            return true;
        }

        return false;
    }

    private void giveRewards(String playerName, UUID uuid, String platform) {
        List<String> rewardCommands = plugin.getConfigManager().getTrivia().getStringList("rewards");
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (String cmd : rewardCommands) {
                String formatted = cmd.replace("%player%", playerName)
                        .replace("%uuid%", uuid.toString())
                        .replace("%platform%", platform);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), formatted);
            }
        });
    }

    public boolean isActive() {
        return active;
    }
}
