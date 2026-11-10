package com.omnicord.plugin.discord.listener;

import com.omnicord.plugin.OmniCord;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Handles all Discord button interactions (Playerlist, 2FA, Dashboard, Tickets, Reports).
 */
public class DiscordButtonListener extends ListenerAdapter {

    private final OmniCord plugin;

    public DiscordButtonListener(OmniCord plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();

        // 0. Check Addon Button Handlers
        if (plugin.getAddonManager() != null && plugin.getAddonManager().handleButtonInteraction(event)) {
            return;
        }

        // 1. Refresh Playerlist
        if ("refresh_playerlist".equals(buttonId)) {
            DiscordSlashCommandListener slashListener = plugin.getDiscordBotManager().getSlashCommandListener();
            if (slashListener != null) {
                event.editMessageEmbeds(slashListener.buildPlayerListEmbed()).queue(null, err -> {});
            } else {
                event.deferEdit().queue(null, err -> {});
            }
            return;
        }

        // 2. Dashboard Buttons
        if ("dashboard_copy_ip".equals(buttonId)) {
            String ip = plugin.getConfigManager().getConfig().getString("general.server_ip", "play.example.com");
            event.reply("🎮 **Server IP:** `" + ip + "` *(Click to copy and connect in Minecraft!)*")
                    .setEphemeral(true).queue(null, err -> {});
            return;
        }

        if ("dashboard_refresh".equals(buttonId)) {
            if (plugin.getLiveDashboardManager() != null) {
                plugin.getLiveDashboardManager().updateDashboard();
                event.reply("✅ Dashboard refreshed.").setEphemeral(true).queue(null, err -> {});
            } else {
                event.deferEdit().queue(null, err -> {});
            }
            return;
        }

        // 3. 2FA Security Buttons
        if (buttonId.startsWith("2fa_approve:")) {
            try {
                UUID uuid = UUID.fromString(buttonId.substring("2fa_approve:".length()));
                plugin.getTwoFactorAuthManager().approve(uuid);
                event.reply("✅ **Login Approved!** You may now play on the Minecraft server.").setEphemeral(true).queue(null, err -> {});
            } catch (Exception e) {
                event.reply("❌ Invalid verification token.").setEphemeral(true).queue(null, err -> {});
            }
            return;
        }

        if (buttonId.startsWith("2fa_deny:")) {
            try {
                UUID uuid = UUID.fromString(buttonId.substring("2fa_deny:".length()));
                plugin.getTwoFactorAuthManager().deny(uuid);
                event.reply("❌ **Login Denied!** The session has been terminated.").setEphemeral(true).queue(null, err -> {});
            } catch (Exception e) {
                event.reply("❌ Invalid verification token.").setEphemeral(true).queue(null, err -> {});
            }
            return;
        }

        // 4. Ticket Close Button
        if (buttonId.startsWith("ticket_close:")) {
            try {
                int ticketId = Integer.parseInt(buttonId.substring("ticket_close:".length()));
                plugin.getTicketManager().closeTicket(ticketId);
                event.reply("🔒 Support Ticket #" + ticketId + " has been closed.").queue(null, err -> {});
            } catch (Exception e) {
                event.reply("❌ Invalid ticket ID.").setEphemeral(true).queue(null, err -> {});
            }
            return;
        }

        // 5. Report Moderation Buttons
        if (buttonId.startsWith("report_")) {
            Member member = event.getMember();
            if (member == null || !member.hasPermission(Permission.KICK_MEMBERS)) {
                event.reply("⛔ You do not have staff permissions to perform this moderation action.").setEphemeral(true).queue(null, err -> {});
                return;
            }

            String[] parts = buttonId.split(":", 2);
            String action = parts[0];
            String uuidStr = parts.length > 1 ? parts[1] : null;

            if (uuidStr == null) {
                event.reply("❌ Invalid report payload.").setEphemeral(true).queue(null, err -> {});
                return;
            }

            event.deferReply().queue(null, err -> {});

            try {
                UUID uuid = UUID.fromString(uuidStr);
                OfflinePlayer target = Bukkit.getOfflinePlayer(uuid);
                String targetName = target.getName() != null ? target.getName() : uuidStr;

                switch (action) {
                    case "report_kick":
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            Player onlinePlayer = Bukkit.getPlayer(uuid);
                            if (onlinePlayer != null) {
                                onlinePlayer.kickPlayer("Kicked by staff via Discord report.");
                                event.getHook().sendMessage("👢 Kicked `" + targetName + "` from the server.").queue();
                            } else {
                                event.getHook().sendMessage("⚠️ Player `" + targetName + "` is not online.").setEphemeral(true).queue();
                            }
                        });
                        break;

                    case "report_ban":
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ban " + targetName + " Banned via Discord moderation report");
                            event.getHook().sendMessage("🔨 Banned `" + targetName + "` from the server.").queue();
                        });
                        break;

                    case "report_dismiss":
                        event.getHook().sendMessage("✅ Report for `" + targetName + "` was dismissed by " + member.getAsMention() + ".").queue();
                        break;

                    default:
                        event.getHook().sendMessage("Unknown report action.").setEphemeral(true).queue();
                        break;
                }
            } catch (Exception e) {
                event.getHook().sendMessage("❌ Invalid target UUID: " + uuidStr).setEphemeral(true).queue();
            }
        }
    }
}
