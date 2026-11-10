package com.omnicord.plugin.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Fired when a player links their Minecraft account to Discord.
 */
public class OmniCordLinkEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID uuid;
    private final String discordId;
    private final String discordTag;

    public OmniCordLinkEvent(UUID uuid, String discordId, String discordTag) {
        this.uuid = uuid;
        this.discordId = discordId;
        this.discordTag = discordTag;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getDiscordId() {
        return discordId;
    }

    public String getDiscordTag() {
        return discordTag;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
