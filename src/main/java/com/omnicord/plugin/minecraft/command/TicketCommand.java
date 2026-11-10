package com.omnicord.plugin.minecraft.command;

import com.omnicord.plugin.OmniCord;
import com.omnicord.plugin.tickets.TicketManager;
import com.omnicord.plugin.utils.ColorUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * In-game player support ticket command.
 */
public class TicketCommand implements CommandExecutor, TabCompleter {

    private final OmniCord plugin;

    public TicketCommand(OmniCord plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("commands.player_only"));
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            player.sendMessage(ColorUtil.colorize(plugin.getConfigManager().getTickets().getString("messages.ticket_usage", "&cUsage: /ticket <create|reply|close|list> [message]")));
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "create":
            case "new":
            case "open":
                if (args.length < 2) {
                    player.sendMessage(ColorUtil.colorize("&cUsage: /ticket create <issue or question>"));
                    return true;
                }
                String issue = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                plugin.getTicketManager().createTicket(player, issue);
                break;

            case "reply":
            case "msg":
                if (args.length < 2) {
                    player.sendMessage(ColorUtil.colorize("&cUsage: /ticket reply <message>"));
                    return true;
                }
                String reply = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                plugin.getTicketManager().replyFromPlayer(player, reply);
                break;

            case "close":
                TicketManager.Ticket ticket = plugin.getTicketManager().getTicketByPlayer(player.getUniqueId());
                if (ticket == null) {
                    player.sendMessage(ColorUtil.colorize(plugin.getConfigManager().getTickets().getString("messages.no_open_ticket", "&cYou do not have any open support tickets.")));
                    return true;
                }
                plugin.getTicketManager().closeTicket(ticket.getId());
                break;

            case "list":
            case "status":
                TicketManager.Ticket active = plugin.getTicketManager().getTicketByPlayer(player.getUniqueId());
                if (active != null) {
                    player.sendMessage(ColorUtil.colorize("&8&m----------------------------------------"));
                    player.sendMessage(plugin.getConfigManager().getGlobalPrefix() + ColorUtil.colorize("&fYour Active Support Ticket:"));
                    player.sendMessage(ColorUtil.colorize("  &7ID: &#57F287#" + active.getId()));
                    player.sendMessage(ColorUtil.colorize("  &7Issue: &f" + active.getInitialMessage()));
                    player.sendMessage(ColorUtil.colorize("  &eType &f/ticket reply <msg> &eto send a message to staff."));
                    player.sendMessage(ColorUtil.colorize("&8&m----------------------------------------"));
                } else {
                    player.sendMessage(ColorUtil.colorize(plugin.getConfigManager().getTickets().getString("messages.no_open_ticket", "&cYou do not have any open support tickets.")));
                }
                break;

            default:
                player.sendMessage(ColorUtil.colorize(plugin.getConfigManager().getTickets().getString("messages.ticket_usage", "&cUsage: /ticket <create|reply|close|list> [message]")));
                break;
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = Arrays.asList("create", "reply", "close", "list");
            List<String> matches = new ArrayList<>();
            for (String s : subs) {
                if (s.startsWith(args[0].toLowerCase())) matches.add(s);
            }
            return matches;
        }
        return Collections.emptyList();
    }
}
