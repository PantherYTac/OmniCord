package com.omnicord.plugin.minecraft.command;

import com.omnicord.plugin.OmniCord;
import com.omnicord.plugin.api.event.OmniCordChatEvent;
import com.omnicord.plugin.utils.ColorUtil;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Two-way in-game staff chat command.
 */
public class StaffChatCommand implements CommandExecutor {

    private final OmniCord plugin;

    public StaffChatCommand(OmniCord plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!plugin.getConfigManager().getConfig().getBoolean("modules.staff_chat", true)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("staff_chat.disabled", "&cStaff chat is currently disabled."));
            return true;
        }

        if (!sender.hasPermission("omnicord.staffchat")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("staff_chat.no_permission"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(plugin.getConfigManager().getMessage("staff_chat.empty_message"));
            return true;
        }

        String message = String.join(" ", args);
        String senderName = sender.getName();

        // Format for in-game staff
        String inGameTemplate = plugin.getConfigManager().getMessages().getString("staff_chat.minecraft_format",
                "&#EB455F[StaffChat] &#FFFFFF%player%&7: &#FEE75C%message%")
                .replace("%player%", senderName)
                .replace("%message%", message);
        String formatted = ColorUtil.colorize(inGameTemplate);

        OmniCordChatEvent event = new OmniCordChatEvent(senderName, formatted, OmniCordChatEvent.ChannelType.STAFF_CHAT);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return true;

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("omnicord.staffchat")) {
                p.sendMessage(event.getMessage());
            }
        }
        Bukkit.getConsoleSender().sendMessage(event.getMessage());

        // Relay to Discord Staff Channel
        if (plugin.getDiscordBotManager().isConnected()) {
            String channelId = plugin.getConfigManager().getConfig().getString("channels.staff_chat", "0");
            TextChannel channel = plugin.getDiscordBotManager().getChannel(channelId);
            if (channel != null && channel.canTalk()) {
                String discordTemplate = plugin.getConfigManager().getMessages().getString("staff_chat.discord_format",
                        "🔒 **[StaffChat] %player%**: %message%")
                        .replace("%player%", senderName)
                        .replace("%message%", ColorUtil.minecraftToDiscord(message));
                channel.sendMessage(discordTemplate).queue();
            }
        }

        return true;
    }
}
