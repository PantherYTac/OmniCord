package com.omnicord.plugin.minecraft.command;

import com.omnicord.plugin.OmniCord;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles in-game player reports and relays them with interactive buttons to Discord.
 */
public class ReportCommand implements CommandExecutor, TabCompleter {

    private final OmniCord plugin;

    public ReportCommand(OmniCord plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("commands.player_only"));
            return true;
        }

        Player reporter = (Player) sender;

        if (!plugin.getConfigManager().getConfig().getBoolean("modules.reports_system", true)) {
            reporter.sendMessage(plugin.getConfigManager().getMessage("reports.disabled", "&cThe player reporting system is currently disabled."));
            return true;
        }

        if (args.length < 2) {
            reporter.sendMessage(plugin.getConfigManager().getMessage("reports.usage"));
            return true;
        }

        String targetName = args[0];
        if (targetName.equalsIgnoreCase(reporter.getName())) {
            reporter.sendMessage(plugin.getConfigManager().getMessage("reports.cannot_report_self"));
            return true;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null || !target.isOnline()) {
            reporter.sendMessage(plugin.getConfigManager().getMessage("reports.player_not_found").replace("%target%", targetName));
            return true;
        }

        String[] reasonArgs = Arrays.copyOfRange(args, 1, args.length);
        String reason = String.join(" ", reasonArgs);

        // Feedback in game
        reporter.sendMessage(plugin.getConfigManager().getMessage("reports.submitted").replace("%target%", target.getName()));

        // Relay to Discord Reports Channel
        if (plugin.getDiscordBotManager().isConnected()) {
            String reportChannelId = plugin.getConfigManager().getConfig().getString("channels.reports", "0");
            TextChannel channel = plugin.getDiscordBotManager().getChannel(reportChannelId);

            if (channel != null && channel.canTalk()) {
                Location loc = target.getLocation();
                int targetPing = 0;
                try {
                    targetPing = target.getPing();
                } catch (Throwable ignored) {}

                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("%target%", target.getName());
                placeholders.put("%target_uuid%", target.getUniqueId().toString());
                placeholders.put("%reporter%", reporter.getName());
                placeholders.put("%reason%", reason);
                placeholders.put("%world%", loc.getWorld() != null ? loc.getWorld().getName() : "world");
                placeholders.put("%x%", String.valueOf(loc.getBlockX()));
                placeholders.put("%y%", String.valueOf(loc.getBlockY()));
                placeholders.put("%z%", String.valueOf(loc.getBlockZ()));
                placeholders.put("%target_ping%", String.valueOf(targetPing));

                MessageEmbed embed = plugin.getDiscordBotManager().getEmbedFactory().createEmbed(
                        "report", placeholders, target.getUniqueId(), target.getName()
                );

                if (embed != null) {
                    channel.sendMessageEmbeds(embed)
                            .addActionRow(
                                    Button.danger("report_kick:" + target.getUniqueId(), "👢 Kick"),
                                    Button.danger("report_ban:" + target.getUniqueId(), "🔨 Ban"),
                                    Button.secondary("report_dismiss:" + target.getUniqueId(), "✖ Dismiss")
                            ).queue(null, err -> {});
                }
            }
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> !name.equalsIgnoreCase(sender.getName()))
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
