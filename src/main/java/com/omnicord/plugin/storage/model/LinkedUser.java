package com.omnicord.plugin.storage.model;

import java.util.UUID;

/**
 * Represents a linked player account between Minecraft and Discord.
 */
public class LinkedUser {

    private final UUID minecraftUuid;
    private final String discordId;
    private final String discordTag;
    private final long linkedTimestamp;
    private boolean rewardClaimed;

    public LinkedUser(UUID minecraftUuid, String discordId, String discordTag, long linkedTimestamp, boolean rewardClaimed) {
        this.minecraftUuid = minecraftUuid;
        this.discordId = discordId;
        this.discordTag = discordTag;
        this.linkedTimestamp = linkedTimestamp;
        this.rewardClaimed = rewardClaimed;
    }

    public UUID getMinecraftUuid() {
        return minecraftUuid;
    }

    public String getDiscordId() {
        return discordId;
    }

    public String getDiscordTag() {
        return discordTag;
    }

    public long getLinkedTimestamp() {
        return linkedTimestamp;
    }

    public boolean isRewardClaimed() {
        return rewardClaimed;
    }

    public void setRewardClaimed(boolean rewardClaimed) {
        this.rewardClaimed = rewardClaimed;
    }
}
