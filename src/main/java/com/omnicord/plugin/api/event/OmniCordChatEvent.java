package com.omnicord.plugin.api.event;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired when a message is bridged between Minecraft and Discord.
 */
public class OmniCordChatEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String sender;
    private String message;
    private final ChannelType channelType;
    private boolean cancelled;

    public enum ChannelType {
        GLOBAL_CHAT,
        STAFF_CHAT,
        CONSOLE
    }

    public OmniCordChatEvent(String sender, String message, ChannelType channelType) {
        super(true); // Async event
        this.sender = sender;
        this.message = message;
        this.channelType = channelType;
    }

    public String getSender() {
        return sender;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public ChannelType getChannelType() {
        return channelType;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
