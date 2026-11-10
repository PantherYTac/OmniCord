package com.omnicord.plugin.minecraft.listener;

import com.omnicord.plugin.OmniCord;
import com.omnicord.plugin.api.event.OmniCordChatEvent;
import com.omnicord.plugin.utils.ColorUtil;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Listens for in-game chat and relays it to Discord via Webhooks or standard bot channel messages.
 */
public class PlayerChatListener implements Listener {

    private final OmniCord plugin;

    public PlayerChatListener(OmniCord plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!plugin.getConfigManager().getConfig().getBoolean("modules.chat_bridge", true) ||
            !plugin.getConfigManager().getConfig().getBoolean("chat_bridge.minecraft_to_discord", true)) {
            return;
        }

        if (!plugin.getDiscordBotManager().isConnected()) {
            return;
        }

        Player player = event.getPlayer();
        String message = event.getMessage();

        // Check Trivia Answer from Minecraft
        if (plugin.getTriviaManager() != null && plugin.getTriviaManager().isActive()) {
            if (plugin.getTriviaManager().checkAnswer(message, player.getName(), player.getUniqueId(), false, null)) {
                return;
            }
        }

        // Fire OmniCord chat event
        OmniCordChatEvent chatEvent = new OmniCordChatEvent(player.getName(), message, OmniCordChatEvent.ChannelType.GLOBAL_CHAT);
        Bukkit.getPluginManager().callEvent(chatEvent);
        if (chatEvent.isCancelled()) return;

        String cleanMessage = ColorUtil.minecraftToDiscord(chatEvent.getMessage());
        String channelId = plugin.getConfigManager().getConfig().getString("channels.chat", "0");
        TextChannel channel = plugin.getDiscordBotManager().getChannel(channelId);
        if (channel == null) return;

        boolean useWebhooks = plugin.getConfigManager().getConfig().getBoolean("chat_bridge.use_webhooks", true);

        if (useWebhooks) {
            String nameFormat = plugin.getConfigManager().getConfig().getString("chat_bridge.webhook_name_format", "%player%")
                    .replace("%player%", player.getName())
                    .replace("%displayname%", ColorUtil.stripColor(player.getDisplayName()))
                    .replace("%vault_prefix%", ColorUtil.stripColor(plugin.getVaultHook().getPlayerPrefix(player)))
                    .replace("%luckperms_prefix%", ColorUtil.stripColor(plugin.getLuckPermsHook().getPlayerPrefix(player.getUniqueId())))
                    .replace("%luckperms_group%", plugin.getLuckPermsHook().getPrimaryGroup(player.getUniqueId()));

            plugin.getDiscordBotManager().getWebhookManager().sendWebhookMessage(
                    channel,
                    player.getUniqueId(),
                    nameFormat,
                    cleanMessage
            );
        } else {
            String template = plugin.getConfigManager().getMessages().getString("chat.minecraft_to_discord", "**%player%**: %message%")
                    .replace("%player%", player.getName())
                    .replace("%message%", cleanMessage);

            channel.sendMessage(template).queue();
        }
    }
}
