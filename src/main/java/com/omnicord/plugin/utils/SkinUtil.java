package com.omnicord.plugin.utils;

import java.util.UUID;

/**
 * Generates avatar head image URLs for Minecraft players across multiple skin avatar APIs.
 */
public final class SkinUtil {

    private SkinUtil() {}

    /**
     * Get avatar image URL for a player based on the configured avatar service.
     */
    public static String getAvatarUrl(String service, UUID uuid, String playerName) {
        if (service == null) service = "crafthead";
        String uuidStr = uuid != null ? uuid.toString() : "8667ba71-b85a-4004-af54-457a9734eed7"; // Default Steve

        switch (service.toLowerCase()) {
            case "cravatar":
                return "https://cravatar.eu/helmavatar/" + uuidStr + "/128.png";
            case "minotar":
                return "https://minotar.net/helm/" + (playerName != null ? playerName : uuidStr) + "/128.png";
            case "visage":
                return "https://visage.surgeplay.com/face/128/" + uuidStr;
            case "crafthead":
            default:
                return "https://crafthead.net/helm/" + uuidStr + "/128";
        }
    }
}
