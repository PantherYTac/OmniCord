package com.omnicord.plugin.discord.listener;

import com.omnicord.plugin.OmniCord;
import com.omnicord.plugin.storage.model.LinkedUser;
import com.omnicord.plugin.utils.ServerUtil;
import com.omnicord.plugin.utils.SkinUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles all Discord Slash Command interactions.
 */
public class DiscordSlashCommandListener extends ListenerAdapter {

    private final OmniCord plugin;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");

    public DiscordSlashCommandListener(OmniCord plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        String commandName = event.getName().toLowerCase();

        switch (commandName) {
            case "playerlist":
                handlePlayerList(event);
                break;
            case "lookup":
                handleLookup(event);
                break;
            case "tps":
                handleTps(event);
                break;
            case "execute":
                handleExecute(event);
                break;
            case "link":
                handleLink(event);
                break;
            case "rewards":
                handleRewards(event);
                break;
            case "leaderboard":
            case "top":
                handleLeaderboard(event);
                break;
            default:
                if (plugin.getAddonManager() != null && plugin.getAddonManager().handleSlashCommand(event)) {
                    return;
                }
                if (plugin.getCustomCommandManager() != null && plugin.getCustomCommandManager().handleCustomCommand(event)) {
                    return;
                }
                event.reply("Unknown command.").setEphemeral(true).queue();
                break;
        }
    }

    public MessageEmbed buildPlayerListEmbed() {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(new Color(88, 101, 242));
        eb.setTitle("🎮 " + plugin.getConfigManager().getConfig().getString("general.server_name", "Minecraft Server") + " — Online Players");

        Collection<? extends Player> players = Bukkit.getOnlinePlayers();
        int online = players.size();
        int max = Bukkit.getMaxPlayers();

        eb.setDescription(online == 0 ? "*No players are currently online.*" :
                players.stream().map(p -> "`" + p.getName() + "`").collect(Collectors.joining(", ")));

        eb.addField("👥 Player Count", online + " / " + max, true);
        eb.addField("⚡ Server TPS", ServerUtil.getFormattedTPS() + " (MSPT: " + String.format("%.1f", ServerUtil.getMSPT()) + ")", true);
        eb.addField("⏱️ Uptime", ServerUtil.getUptime(), true);

        eb.setFooter("OmniCord Network Status", null);
        eb.setTimestamp(Instant.now());
        return eb.build();
    }

    private void handlePlayerList(SlashCommandInteractionEvent event) {
        event.replyEmbeds(buildPlayerListEmbed())
                .addActionRow(Button.primary("refresh_playerlist", "🔄 Refresh List"))
                .queue();
    }

    private void handleLookup(SlashCommandInteractionEvent event) {
        String targetName = Objects.requireNonNull(event.getOption("player")).getAsString();
        event.deferReply().queue();

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (target.getName() == null && !target.hasPlayedBefore() && !target.isOnline()) {
            event.getHook().sendMessage("❌ Player `" + targetName + "` has never joined this server.").setEphemeral(true).queue();
            return;
        }

        UUID uuid = target.getUniqueId();
        String skinUrl = SkinUtil.getAvatarUrl("crafthead", uuid, target.getName());

        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(new Color(87, 242, 135));
        eb.setTitle("👤 Player Profile: " + (target.getName() != null ? target.getName() : targetName));
        eb.setThumbnail(skinUrl);

        eb.addField("🆔 UUID", "`" + uuid + "`", false);
        eb.addField("📶 Status", target.isOnline() ? "🟢 Online" : "🔴 Offline", true);
        eb.addField("⭐ Primary Rank", plugin.getLuckPermsHook().getPrimaryGroup(uuid), true);

        if (plugin.getVaultHook().isAvailable()) {
            eb.addField("💰 Balance", "$" + String.format("%.2f", plugin.getVaultHook().getBalance(target)), true);
        }

        if (target.hasPlayedBefore()) {
            eb.addField("📅 First Joined", dateFormat.format(new Date(target.getFirstPlayed())), true);
            eb.addField("🚪 Last Played", dateFormat.format(new Date(target.getLastPlayed())), true);
        }

        plugin.getAccountLinkingManager().getLink(uuid).thenAccept(opt -> {
            if (opt.isPresent()) {
                LinkedUser user = opt.get();
                eb.addField("🔗 Discord Account", "<@" + user.getDiscordId() + "> (`" + user.getDiscordTag() + "`)", false);
            } else {
                eb.addField("🔗 Discord Account", "❌ Not Linked", false);
            }

            event.getHook().sendMessageEmbeds(eb.build()).queue();
        });
    }

    private void handleTps(SlashCommandInteractionEvent event) {
        double[] tps = ServerUtil.getTPS();
        double tps1m = Math.min(20.0, tps[0]);
        double tps5m = Math.min(20.0, tps[1]);
        double tps15m = Math.min(20.0, tps[2]);

        String tpsIcon = tps1m >= 18.5 ? "🟢" : (tps1m >= 15.0 ? "🟡" : "🔴");

        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(tps1m >= 18.5 ? new Color(87, 242, 135) : new Color(237, 66, 69));
        eb.setTitle("⚡ Server Performance & Diagnostics");

        eb.addField("📊 TPS (1m, 5m, 15m)", String.format("%s `%.2f`, `%.2f`, `%.2f`", tpsIcon, tps1m, tps5m, tps15m), false);
        eb.addField("⏱️ Tick Duration (MSPT)", String.format("`%.2f ms` (Limit: 50.0 ms)", ServerUtil.getMSPT()), true);
        eb.addField("💾 JVM Memory Usage", "`" + ServerUtil.getMemoryUsage() + "` (" + ServerUtil.getMemoryPercentage() + "%)", true);
        eb.addField("⏳ Server Uptime", "`" + ServerUtil.getUptime() + "`", true);

        eb.setFooter("OmniCord Real-Time Engine", null);
        eb.setTimestamp(Instant.now());

        event.replyEmbeds(eb.build()).queue();
    }

    private void handleExecute(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        if (member == null || !member.hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("⛔ You must have Administrator permissions in this Discord server to execute console commands.")
                    .setEphemeral(true).queue();
            return;
        }

        String command = Objects.requireNonNull(event.getOption("command")).getAsString();
        event.deferReply(true).queue();

        Bukkit.getScheduler().runTask(plugin, () -> {
            plugin.getLogger().info("[Discord Admin /execute (" + member.getUser().getAsTag() + ")]: " + command);
            boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);

            EmbedBuilder eb = new EmbedBuilder();
            eb.setColor(success ? new Color(87, 242, 135) : new Color(237, 66, 69));
            eb.setTitle("💻 Command Executed");
            eb.setDescription("Command: `/" + command + "`\nStatus: " + (success ? "✅ Successfully Dispatched" : "⚠️ Executed with possible warnings"));
            eb.setFooter("Executed by " + member.getUser().getAsTag(), null);
            eb.setTimestamp(Instant.now());

            event.getHook().sendMessageEmbeds(eb.build()).setEphemeral(true).queue();
        });
    }

    private void handleLink(SlashCommandInteractionEvent event) {
        String code = Objects.requireNonNull(event.getOption("code")).getAsString().trim();
        String discordId = event.getUser().getId();
        String discordTag = event.getUser().getAsTag();

        event.deferReply(true).queue();

        plugin.getAccountLinkingManager().completeLink(code, discordId, discordTag)
                .thenAccept(success -> {
                    if (success) {
                        event.getHook().sendMessage("🎉 **Success!** Your Discord account has been successfully linked to your Minecraft profile!").setEphemeral(true).queue();
                    } else {
                        event.getHook().sendMessage("❌ **Invalid or Expired Code.** Please generate a new code in-game using `/discord link`.").setEphemeral(true).queue();
                    }
                });
    }

    private void handleRewards(SlashCommandInteractionEvent event) {
        String discordId = event.getUser().getId();
        event.deferReply(true).queue();

        plugin.getAccountLinkingManager().getLinkByDiscord(discordId).thenAccept(opt -> {
            if (!opt.isPresent()) {
                event.getHook().sendMessage("❌ Your Discord account is not linked to any Minecraft profile yet! Run `/discord link` in-game.").setEphemeral(true).queue();
                return;
            }

            LinkedUser user = opt.get();
            OfflinePlayer player = Bukkit.getOfflinePlayer(user.getMinecraftUuid());
            String name = player.getName() != null ? player.getName() : user.getMinecraftUuid().toString();

            EmbedBuilder eb = new EmbedBuilder();
            eb.setColor(new Color(254, 231, 92));
            eb.setTitle("🎁 Account Linking Status & Rewards");
            eb.setDescription("Linked Account: **" + name + "**\nStatus: " + (user.isRewardClaimed() ? "✅ Rewards Claimed" : "🎉 Rewards Ready to Claim"));
            eb.setThumbnail(SkinUtil.getAvatarUrl("crafthead", user.getMinecraftUuid(), name));

            event.getHook().sendMessageEmbeds(eb.build()).setEphemeral(true).queue();
        });
    }

    private void handleLeaderboard(SlashCommandInteractionEvent event) {
        String type = event.getOption("type") != null ? Objects.requireNonNull(event.getOption("type")).getAsString().toLowerCase() : "balance";
        event.deferReply().queue();

        Bukkit.getScheduler().runTask(plugin, () -> {
            EmbedBuilder eb = new EmbedBuilder();
            eb.setColor(new Color(254, 231, 92));
            eb.setTitle("🏆 " + plugin.getConfigManager().getConfig().getString("general.server_name", "Minecraft Server") + " — Top Players");

            List<OfflinePlayer> players = Arrays.stream(Bukkit.getOfflinePlayers())
                    .filter(p -> p.getName() != null && p.getFirstPlayed() > 0)
                    .collect(Collectors.toList());

            if (players.isEmpty()) {
                eb.setDescription("*No player data recorded yet.*");
                event.getHook().sendMessageEmbeds(eb.build()).queue();
                return;
            }

            StringBuilder sb = new StringBuilder();
            String[] medals = {"🥇", "🥈", "🥉", "4️⃣", "5️⃣", "6️⃣", "7️⃣", "8️⃣", "9️⃣", "🔟"};

            if ("balance".equalsIgnoreCase(type) && plugin.getVaultHook().isAvailable()) {
                eb.setDescription("**Top 10 Wealthiest Players (Vault Economy)**\n");
                players.sort((p1, p2) -> Double.compare(plugin.getVaultHook().getBalance(p2), plugin.getVaultHook().getBalance(p1)));
                int count = Math.min(10, players.size());
                for (int i = 0; i < count; i++) {
                    OfflinePlayer p = players.get(i);
                    double bal = plugin.getVaultHook().getBalance(p);
                    sb.append(medals[i]).append(" **").append(p.getName()).append("**: $").append(String.format("%,.2f", bal)).append("\n");
                }
            } else {
                eb.setDescription("**Top 10 Veteran Players (Join Date)**\n");
                players.sort(Comparator.comparingLong(OfflinePlayer::getFirstPlayed));
                int count = Math.min(10, players.size());
                for (int i = 0; i < count; i++) {
                    OfflinePlayer p = players.get(i);
                    sb.append(medals[i]).append(" **").append(p.getName()).append("**: Joined ").append(dateFormat.format(new Date(p.getFirstPlayed()))).append("\n");
                }
            }

            eb.addField("Leaderboard", sb.toString(), false);
            eb.setFooter("OmniCord Leaderboard Engine", null);
            eb.setTimestamp(Instant.now());

            event.getHook().sendMessageEmbeds(eb.build()).queue();
        });
    }
}
