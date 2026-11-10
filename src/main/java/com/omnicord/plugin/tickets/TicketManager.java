package com.omnicord.plugin.tickets;

import com.omnicord.plugin.OmniCord;
import com.omnicord.plugin.utils.ColorUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.awt.Color;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages in-game player support tickets and synchronizes them with Discord threads.
 */
public class TicketManager {

    private final OmniCord plugin;
    private final AtomicInteger ticketCounter = new AtomicInteger(1000);
    private final Map<UUID, Ticket> activeTicketsByPlayer = new ConcurrentHashMap<>();
    private final Map<String, Ticket> activeTicketsByThreadId = new ConcurrentHashMap<>();

    public TicketManager(OmniCord plugin) {
        this.plugin = plugin;
    }

    public static class Ticket {
        private final int id;
        private final UUID playerUuid;
        private final String playerName;
        private final String initialMessage;
        private String threadId;

        public Ticket(int id, UUID playerUuid, String playerName, String initialMessage) {
            this.id = id;
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.initialMessage = initialMessage;
        }

        public int getId() { return id; }
        public UUID getPlayerUuid() { return playerUuid; }
        public String getPlayerName() { return playerName; }
        public String getInitialMessage() { return initialMessage; }
        public String getThreadId() { return threadId; }
        public void setThreadId(String threadId) { this.threadId = threadId; }
    }

    public boolean hasOpenTicket(UUID uuid) {
        return activeTicketsByPlayer.containsKey(uuid);
    }

    public Ticket getTicketByPlayer(UUID uuid) {
        return activeTicketsByPlayer.get(uuid);
    }

    public Ticket getTicketByThreadId(String threadId) {
        return activeTicketsByThreadId.get(threadId);
    }

    public boolean isEnabled() {
        return plugin.getConfigManager().getConfig().getBoolean("modules.support_tickets", true) &&
               plugin.getConfigManager().getTickets().getBoolean("enabled", true);
    }

    public void createTicket(Player player, String message) {
        UUID uuid = player.getUniqueId();

        if (!isEnabled()) {
            player.sendMessage(ColorUtil.colorize("&cThe support ticket system is currently disabled."));
            return;
        }

        if (hasOpenTicket(uuid)) {
            String alreadyOpenMsg = plugin.getConfigManager().getTickets().getString("messages.ticket_already_open", "&cYou already have an open ticket!");
            player.sendMessage(ColorUtil.colorize(alreadyOpenMsg));
            return;
        }

        int ticketId = ticketCounter.incrementAndGet();
        Ticket ticket = new Ticket(ticketId, uuid, player.getName(), message);
        activeTicketsByPlayer.put(uuid, ticket);

        // Feedback in game
        String createdMsg = plugin.getConfigManager().getTickets().getString("messages.ticket_created",
                "&#57F287Your support ticket #%ticket_id% has been created! Our staff on Discord have been notified.")
                .replace("%prefix%", plugin.getConfigManager().getGlobalPrefix())
                .replace("%ticket_id%", String.valueOf(ticketId));
        player.sendMessage(ColorUtil.colorize(createdMsg));

        // Create Discord Thread
        if (!plugin.getDiscordBotManager().isConnected()) return;

        String channelId = plugin.getConfigManager().getTickets().getString("ticket_channel_id", "0");
        TextChannel channel = plugin.getDiscordBotManager().getChannel(channelId);
        if (channel == null) return;

        String staffRoleId = plugin.getConfigManager().getTickets().getString("staff_role_id", "0");
        String roleMention = (!"0".equals(staffRoleId) && !staffRoleId.isEmpty()) ? "<@&" + staffRoleId + "> " : "";

        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(new Color(88, 101, 242));
        eb.setTitle("🎫 Ticket #" + ticketId + " — " + player.getName());
        eb.addField("👤 Player", player.getName() + " (`" + uuid + "`)", true);
        eb.addField("📍 Location", player.getWorld().getName() + " (" + player.getLocation().getBlockX() + ", " + player.getLocation().getBlockY() + ", " + player.getLocation().getBlockZ() + ")", true);
        eb.addField("📝 Issue / Question", message, false);
        eb.setFooter("OmniCord ModMail Engine", null);
        eb.setTimestamp(Instant.now());

        channel.sendMessage(roleMention + "New ticket submitted by **" + player.getName() + "**")
                .setEmbeds(eb.build())
                .setActionRow(Button.danger("ticket_close:" + ticketId, "🔒 Close Ticket"))
                .queue(msg -> {
                    // Create thread for ticket
                    msg.createThreadChannel("ticket-" + ticketId + "-" + player.getName()).queue(thread -> {
                        ticket.setThreadId(thread.getId());
                        activeTicketsByThreadId.put(thread.getId(), ticket);
                    }, err -> {
                        plugin.getLogger().warning("Could not create Discord thread for Ticket #" + ticketId + " (Check bot thread permissions): " + err.getMessage());
                    });
                }, err -> {
                    plugin.getLogger().warning("Could not send Discord ticket message for Ticket #" + ticketId + ": " + err.getMessage());
                });
    }

    public void replyFromPlayer(Player player, String message) {
        Ticket ticket = getTicketByPlayer(player.getUniqueId());
        if (ticket == null || ticket.getThreadId() == null) {
            String noTicketMsg = plugin.getConfigManager().getTickets().getString("messages.no_open_ticket", "&cYou do not have any open support tickets.");
            player.sendMessage(ColorUtil.colorize(noTicketMsg));
            return;
        }

        player.sendMessage(ColorUtil.colorize("&8[Ticket #" + ticket.getId() + " - You]: &#FFFFFF" + message));

        if (plugin.getDiscordBotManager().getJda() != null) {
            ThreadChannel thread = plugin.getDiscordBotManager().getJda().getThreadChannelById(ticket.getThreadId());
            if (thread != null) {
                thread.sendMessage("💬 **" + player.getName() + " (In-Game)**: " + message).queue();
            }
        }
    }

    public void replyFromStaff(String threadId, String staffName, String message) {
        Ticket ticket = getTicketByThreadId(threadId);
        if (ticket == null) return;

        Player player = Bukkit.getPlayer(ticket.getPlayerUuid());
        if (player != null && player.isOnline()) {
            String template = plugin.getConfigManager().getTickets().getString("messages.ticket_reply_from_staff",
                    "&#7289DA[Staff Support - %staff%]: &#FFFFFF%message%")
                    .replace("%prefix%", plugin.getConfigManager().getGlobalPrefix())
                    .replace("%staff%", staffName)
                    .replace("%message%", message);
            player.sendMessage(ColorUtil.colorize(template));
        }
    }

    public void closeTicket(int ticketId) {
        Ticket found = null;
        for (Ticket t : activeTicketsByPlayer.values()) {
            if (t.getId() == ticketId) {
                found = t;
                break;
            }
        }

        if (found != null) {
            activeTicketsByPlayer.remove(found.getPlayerUuid());
            if (found.getThreadId() != null) {
                activeTicketsByThreadId.remove(found.getThreadId());
                if (plugin.getDiscordBotManager().getJda() != null) {
                    ThreadChannel thread = plugin.getDiscordBotManager().getJda().getThreadChannelById(found.getThreadId());
                    if (thread != null) {
                        thread.sendMessage("🔒 **Ticket closed by staff.**").queue(s -> {
                            try {
                                thread.getManager().setLocked(true).setArchived(true).queue();
                            } catch (Exception ignored) {}
                        });
                    }
                }
            }

            Player player = Bukkit.getPlayer(found.getPlayerUuid());
            if (player != null && player.isOnline()) {
                String closedMsg = plugin.getConfigManager().getTickets().getString("messages.ticket_closed", "&eYour support ticket #%ticket_id% has been closed by staff.")
                        .replace("%prefix%", plugin.getConfigManager().getGlobalPrefix())
                        .replace("%ticket_id%", String.valueOf(ticketId));
                player.sendMessage(ColorUtil.colorize(closedMsg));
            }
        }
    }
}
