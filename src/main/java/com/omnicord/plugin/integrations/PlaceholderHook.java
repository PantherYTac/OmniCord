package com.omnicord.plugin.integrations;

import com.omnicord.plugin.OmniCord;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * Soft-dependency PlaceholderAPI hook for OmniCord (core).
 *
 * <p><b>Design:</b> This outer class contains ZERO direct PlaceholderAPI imports.
 * The {@link OmniCordExpansion} inner class that extends {@code PlaceholderExpansion} is kept
 * in the separate file {@link PlaceholderExpansionImpl} so that its superclass is only resolved
 * by the JVM when the class is actually loaded — which only happens inside {@link #initialize()}
 * after confirming PlaceholderAPI is present.</p>
 */
public class PlaceholderHook {

    private final OmniCord plugin;
    private boolean available = false;

    public PlaceholderHook(OmniCord plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            plugin.getLogger().info("[OmniCord] PlaceholderAPI not found — PAPI integration disabled.");
            return;
        }
        try {
            // Only now do we instantiate OmniCordExpansion — this is the first time
            // PlaceholderExpansion will be resolved by the JVM. If PAPI isn't present
            // we never reach this line, so NoClassDefFoundError cannot occur.
            new OmniCordExpansion(plugin).register();
            this.available = true;
            plugin.getLogger().info("[OmniCord] Successfully hooked into PlaceholderAPI.");
        } catch (Throwable t) {
            this.available = false;
            plugin.getLogger().warning("[OmniCord] PlaceholderAPI hook failed: " + t.getMessage());
        }
    }

    public String setPlaceholders(Player player, String text) {
        if (!available || text == null) return text;
        try {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        } catch (Throwable t) {
            return text;
        }
    }

    public String setPlaceholders(OfflinePlayer player, String text) {
        if (!available || text == null) return text;
        try {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        } catch (Throwable t) {
            return text;
        }
    }

    public boolean isAvailable() {
        return available;
    }

    // -----------------------------------------------------------------------
    // Inner expansion class — the ONLY class allowed to reference PAPI types.
    // Java only resolves a class's superclass/interface hierarchy when that
    // class is actually loaded. By keeping this inner and only loading it after
    // confirming PAPI is present, NoClassDefFoundError cannot occur.
    // -----------------------------------------------------------------------
    static final class OmniCordExpansion extends me.clip.placeholderapi.expansion.PlaceholderExpansion {

        private final OmniCord plugin;

        OmniCordExpansion(OmniCord plugin) {
            this.plugin = plugin;
        }

        @Override
        public String getIdentifier() { return "omnicord"; }

        @Override
        public String getAuthor() { return "PantherYTac"; }

        @Override
        public String getVersion() { return plugin.getDescription().getVersion(); }

        @Override
        public boolean persist() { return true; }

        @Override
        public String onRequest(OfflinePlayer player, String params) {
            if (player == null) return "";

            java.util.Optional<com.omnicord.plugin.storage.model.LinkedUser> user;

            if (params.equalsIgnoreCase("linked")) {
                user = plugin.getAccountLinkingManager().getLink(player.getUniqueId()).join();
                return user.isPresent() ? "true" : "false";
            }

            if (params.equalsIgnoreCase("discord_tag")) {
                user = plugin.getAccountLinkingManager().getLink(player.getUniqueId()).join();
                return user.map(com.omnicord.plugin.storage.model.LinkedUser::getDiscordTag).orElse("Not Linked");
            }

            if (params.equalsIgnoreCase("discord_id")) {
                user = plugin.getAccountLinkingManager().getLink(player.getUniqueId()).join();
                return user.map(com.omnicord.plugin.storage.model.LinkedUser::getDiscordId).orElse("None");
            }

            return null;
        }
    }
}
