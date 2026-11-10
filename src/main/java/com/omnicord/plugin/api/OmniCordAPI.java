package com.omnicord.plugin.api;

import com.omnicord.plugin.OmniCord;
import com.omnicord.plugin.addon.AddonManager;
import com.omnicord.plugin.storage.model.LinkedUser;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Public developer API for interacting with OmniCord.
 */
public class OmniCordAPI {

    private static OmniCord plugin;
    private static final OmniCordAPI INSTANCE = new OmniCordAPI();

    public static OmniCordAPI get() {
        return INSTANCE;
    }

    public static void setPlugin(OmniCord instance) {
        plugin = instance;
    }

    public OmniCord getPluginInstance() {
        return plugin;
    }

    public OmniCord getPlugin() {
        return plugin;
    }

    /**
     * Access the Addon Manager for registering and querying addons.
     */
    public AddonManager getAddonManager() {
        return plugin != null ? plugin.getAddonManager() : null;
    }

    /**
     * Get the active JDA instance.
     */
    public static JDA getJDA() {
        return plugin != null ? plugin.getDiscordBotManager().getJda() : null;
    }

    /**
     * Get the configured Discord Guild (Server).
     */
    public static Guild getGuild() {
        return plugin != null ? plugin.getDiscordBotManager().getGuild() : null;
    }

    /**
     * Fetch linked Discord account by Minecraft UUID.
     */
    public static CompletableFuture<Optional<LinkedUser>> getLinkedUser(UUID uuid) {
        return plugin.getAccountLinkingManager().getLink(uuid);
    }

    /**
     * Fetch linked Minecraft UUID by Discord ID.
     */
    public static CompletableFuture<Optional<LinkedUser>> getLinkedUserByDiscord(String discordId) {
        return plugin.getAccountLinkingManager().getLinkByDiscord(discordId);
    }

    /**
     * Send a raw message to a Discord channel by Channel ID.
     */
    public static void sendMessage(String channelId, String message) {
        if (plugin == null || !plugin.getDiscordBotManager().isConnected()) return;
        TextChannel channel = plugin.getDiscordBotManager().getChannel(channelId);
        if (channel != null && channel.canTalk()) {
            channel.sendMessage(message).queue();
        }
    }

    /**
     * Send an embed to a Discord channel by Channel ID.
     */
    public static void sendEmbed(String channelId, MessageEmbed embed) {
        if (plugin == null || !plugin.getDiscordBotManager().isConnected()) return;
        TextChannel channel = plugin.getDiscordBotManager().getChannel(channelId);
        if (channel != null && channel.canTalk()) {
            channel.sendMessageEmbeds(embed).queue();
        }
    }
}
