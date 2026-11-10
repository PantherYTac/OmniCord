package com.omnicord.plugin.discord.listener;

import com.omnicord.plugin.OmniCord;
import com.omnicord.plugin.api.event.OmniCordChatEvent;
import com.omnicord.plugin.utils.ColorUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Handles incoming Discord messages and relays them into Minecraft.
 * Also processes Direct Message (DM) Account Linking.
 */
public class DiscordChatListener extends ListenerAdapter {

    private final OmniCord plugin;

    public DiscordChatListener(OmniCord plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        // Ignore webhooks and self/bots
        if (event.isWebhookMessage() || event.getAuthor().isBot()) {
            return;
        }

        // Handle Direct Messages (DM Linking)
        if (!event.isFromGuild()) {
            handleDirectMessage(event);
            return;
        }

        String channelId = event.getChannel().getId();
        String chatChannelId = plugin.getConfigManager().getConfig().getString("channels.chat", "0");
        String staffChannelId = plugin.getConfigManager().getConfig().getString("channels.staff_chat", "0");
        String consoleChannelId = plugin.getConfigManager().getConfig().getString("channels.console", "0");

        String content = event.getMessage().getContentDisplay();
        final String rawContent = content;
        Member member = event.getMember();
        String authorName = member != null ? member.getEffectiveName() : event.getAuthor().getName();
        final String finalAuthor = authorName;

        // 0. Check if message is in an active Ticket Thread
        if (event.isFromThread()) {
            String threadId = event.getChannel().getId();
            if (plugin.getTicketManager().getTicketByThreadId(threadId) != null) {
                plugin.getTicketManager().replyFromStaff(threadId, authorName, content);
                return;
            }
        }

        // Check Trivia Answer from Discord
        if (plugin.getTriviaManager() != null && plugin.getTriviaManager().isActive()) {
            plugin.getAccountLinkingManager().getLinkByDiscord(event.getAuthor().getId()).thenAccept(opt -> {
                UUID uuid = opt.map(com.omnicord.plugin.storage.model.LinkedUser::getMinecraftUuid).orElse(null);
                plugin.getTriviaManager().checkAnswer(rawContent, finalAuthor, uuid, true, event.getAuthor().getAsTag());
            });
        }

        // Append attachment links if present
        if (!event.getMessage().getAttachments().isEmpty()) {
            for (Message.Attachment attachment : event.getMessage().getAttachments()) {
                String attachFormat = plugin.getConfigManager().getMessages().getString("chat.attachment_format", "&#7289DA[Attachment: %file_name%]")
                        .replace("%file_name%", attachment.getFileName());
                content = content.isEmpty() ? attachFormat : content + " " + attachFormat;
            }
        }

        if (content.trim().isEmpty()) return;

        // 1. Main Chat Channel
        if (channelId.equals(chatChannelId)) {
            if (!plugin.getConfigManager().getConfig().getBoolean("modules.chat_bridge", true) ||
                !plugin.getConfigManager().getConfig().getBoolean("chat_bridge.discord_to_minecraft", true)) {
                return;
            }

            // Role color
            String roleColor = "&f";
            String topRole = "Member";
            if (member != null && !member.getRoles().isEmpty()) {
                Role highestRole = member.getRoles().get(0);
                topRole = highestRole.getName();
                if (highestRole.getColor() != null) {
                    roleColor = String.format("&#%06X", (0xFFFFFF & highestRole.getColorRaw()));
                }
            }

            String template;
            Message referencedMessage = event.getMessage().getReferencedMessage();
            if (referencedMessage != null) {
                template = plugin.getConfigManager().getMessages().getString("chat.discord_to_minecraft_reply",
                        "&#7289DA[Discord] %role_color%%user% &8(replying to &7%reply_to%&8)&7: &#FFFFFF%message%");
                template = template.replace("%reply_to%", referencedMessage.getAuthor().getName());
            } else {
                template = plugin.getConfigManager().getMessages().getString("chat.discord_to_minecraft",
                        "&#7289DA[Discord] %role_color%%user%&7: &#FFFFFF%message%");
            }

            // FIX: Use getName() instead of deprecated getDiscriminator()
            String formattedMessage = template
                    .replace("%user%", authorName)
                    .replace("%discriminator%", event.getAuthor().getName())
                    .replace("%role%", topRole)
                    .replace("%top_role%", topRole)
                    .replace("%role_color%", roleColor)
                    .replace("%message%", ColorUtil.discordToMinecraft(content));

            String finalColored = ColorUtil.colorize(formattedMessage);

            // FIX Bug #4: JDA fires this on async thread — Bukkit callEvent and broadcastMessage must be on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                OmniCordChatEvent chatEvent = new OmniCordChatEvent(authorName, finalColored, OmniCordChatEvent.ChannelType.GLOBAL_CHAT);
                Bukkit.getPluginManager().callEvent(chatEvent);

                if (!chatEvent.isCancelled()) {
                    Bukkit.broadcastMessage(chatEvent.getMessage());
                    playMentionPings(rawContent);
                }
            });
        }

        // 2. Staff Chat Channel
        else if (channelId.equals(staffChannelId) && !"0".equals(staffChannelId)) {
            if (!plugin.getConfigManager().getConfig().getBoolean("modules.staff_chat", true)) {
                return;
            }
            String template = plugin.getConfigManager().getMessages().getString("staff_chat.discord_to_minecraft",
                    "&#EB455F[StaffChat] &#7289DA[Discord] &#FFFFFF%user%&7: &#FEE75C%message%");
            String formatted = template
                    .replace("%user%", authorName)
                    .replace("%message%", content);
            String colored = ColorUtil.colorize(formatted);

            // FIX Bug #5: JDA fires this on async thread — callEvent must be on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                OmniCordChatEvent chatEvent = new OmniCordChatEvent(authorName, colored, OmniCordChatEvent.ChannelType.STAFF_CHAT);
                Bukkit.getPluginManager().callEvent(chatEvent);

                if (!chatEvent.isCancelled()) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player.hasPermission("omnicord.staffchat")) {
                            player.sendMessage(chatEvent.getMessage());
                        }
                    }
                    Bukkit.getConsoleSender().sendMessage(chatEvent.getMessage());
                }
            });
        }

        // 3. Console Channel Command Execution
        else if (channelId.equals(consoleChannelId) && !"0".equals(consoleChannelId)) {
            if (!plugin.getConfigManager().getConfig().getBoolean("modules.console_bridge", true) ||
                !plugin.getConfigManager().getConfig().getBoolean("console_bridge.allow_commands", true)) {
                return;
            }

            String discordId = event.getAuthor().getId();
            plugin.getAccountLinkingManager().getLinkByDiscord(discordId).thenAccept(optLink -> {
                if (!optLink.isPresent()) {
                    event.getMessage().addReaction(net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("⛔")).queue();
                    event.getMessage().reply("⛔ **Access Denied:** You must link your Minecraft account (`/discord link`) before you can execute console commands.").queue();
                    return;
                }

                UUID uuid = optLink.get().getMinecraftUuid();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    org.bukkit.OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(uuid);
                    boolean isOp = targetPlayer.isOp();
                    boolean hasAdminPerm = isOp || (plugin.getLuckPermsHook().isAvailable() && plugin.getLuckPermsHook().hasPermission(uuid, "omnicord.admin"))
                            || (targetPlayer.getPlayer() != null && targetPlayer.getPlayer().hasPermission("omnicord.admin"));

                    if (!hasAdminPerm) {
                        event.getMessage().addReaction(net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("⛔")).queue();
                        String mcName = targetPlayer.getName() != null ? targetPlayer.getName() : uuid.toString();
                        event.getMessage().reply("⛔ **Access Denied:** Your linked Minecraft account (**" + mcName + "**) does not have OP or Admin permissions on this server.").queue();
                        return;
                    }

                    String command = rawContent.startsWith("/") ? rawContent.substring(1) : rawContent;
                    String mcName = targetPlayer.getName() != null ? targetPlayer.getName() : uuid.toString();
                    plugin.getLogger().info("[Discord Console (" + finalAuthor + " -> MC: " + mcName + ")]: /" + command);

                    boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                    if (success) {
                        event.getMessage().addReaction(net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("✅")).queue();
                    } else {
                        event.getMessage().addReaction(net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("⚠️")).queue();
                    }
                });
            });
        }
    }

    private void playMentionPings(String content) {
        if (!plugin.getConfigManager().getConfig().getBoolean("chat_bridge.enable_mention_pings", true)) {
            return;
        }

        String soundName = plugin.getConfigManager().getConfig().getString("chat_bridge.mention_ping_sound", "BLOCK_NOTE_BLOCK_BELL");
        Sound sound;
        try {
            sound = Sound.valueOf(soundName);
        } catch (Exception e) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (content.toLowerCase().contains(player.getName().toLowerCase())) {
                player.playSound(player.getLocation(), sound, 1.0f, 1.2f);
            }
        }
    }

    private void handleDirectMessage(@NotNull MessageReceivedEvent event) {
        String text = event.getMessage().getContentDisplay().trim();
        if (text.isEmpty()) return;

        // Clean code of any command prefix or punctuation
        String code = text.replaceFirst("(?i)^[\\!/]?link\\s*", "").replaceAll("[\\s-]", "").trim();

        // If message looks like a linking code (4 to 10 alphanumeric or numeric chars)
        if (code.matches("^[0-9A-Za-z]{4,10}$")) {
            String discordId = event.getAuthor().getId();
            String discordTag = event.getAuthor().getAsTag();

            plugin.getAccountLinkingManager().completeLink(code, discordId, discordTag)
                    .thenAccept(success -> {
                        if (success) {
                            EmbedBuilder eb = new EmbedBuilder();
                            eb.setColor(new Color(87, 242, 135)); // Green
                            eb.setTitle("🎉 Account Successfully Linked!");
                            eb.setDescription("Your Discord account (**" + discordTag + "**) is now linked to your Minecraft profile!\n\n" +
                                    "✨ Your in-game ranks, perks, and Discord roles have been synchronized.");
                            eb.setFooter("OmniCord Security & Link Core • Author: PantherYTac", null);
                            eb.setTimestamp(Instant.now());

                            event.getChannel().sendMessageEmbeds(eb.build()).queue();
                        } else {
                            EmbedBuilder eb = new EmbedBuilder();
                            eb.setColor(new Color(237, 66, 69)); // Red
                            eb.setTitle("❌ Linking Code Invalid or Expired");
                            eb.setDescription("The code **`" + code + "`** could not be verified or has already expired.\n\n" +
                                    "👉 Please join the Minecraft server and run **`/discord link`** to generate a fresh 6-digit code.");
                            eb.setFooter("OmniCord Security & Link Core • Author: PantherYTac", null);
                            eb.setTimestamp(Instant.now());

                            event.getChannel().sendMessageEmbeds(eb.build()).queue();
                        }
                    });
        } else {
            // Helpful Guide for DMing the bot
            EmbedBuilder eb = new EmbedBuilder();
            eb.setColor(new Color(88, 101, 242)); // Blurple
            eb.setTitle("🔗 OmniCord Account Linking");
            eb.setDescription("To link your Minecraft account to Discord:\n\n" +
                    "1️⃣ Join the Minecraft server.\n" +
                    "2️⃣ Run **`/discord link`** in-game to generate your unique 6-digit code.\n" +
                    "3️⃣ **Send the 6-digit code directly in this DM!**");
            eb.setFooter("OmniCord Assistant • Author: PantherYTac", null);
            eb.setTimestamp(Instant.now());

            event.getChannel().sendMessageEmbeds(eb.build()).queue();
        }
    }
}
