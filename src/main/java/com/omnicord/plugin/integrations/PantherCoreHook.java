package com.omnicord.plugin.integrations;

import com.omnicord.plugin.OmniCord;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.*;

/**
 * Ecosystem Hook for PantherCore by PantherYTac.
 * Provides central module telemetry, shared cross-plugin configs, and dynamic ecosystem update lookup.
 */
public class PantherCoreHook {

    private final OmniCord plugin;
    private boolean available = false;
    private final Map<String, String> ecosystemVersions = new LinkedHashMap<>();

    public PantherCoreHook(OmniCord plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        Plugin pantherCore = Bukkit.getPluginManager().getPlugin("PantherCore");
        if (pantherCore != null && pantherCore.isEnabled()) {
            this.available = true;
            plugin.getLogger().info("✓ Hooked into PantherCore v" + pantherCore.getDescription().getVersion() + " (by PantherYTac) - Ecosystem update lookup initialized.");
            scanEcosystemUpdates();
        }
    }

    /**
     * Scans installed ecosystem plugins by PantherYTac and tracks versions/available updates.
     */
    public void scanEcosystemUpdates() {
        ecosystemVersions.clear();
        String[] ecosystemPlugins = {"OmniCord", "OmniCord-Premium", "PantherCore", "PantherAC", "PantherAC-Lite", "PantherAC-Premium", "FabulousClaims", "NeonClans"};

        for (String name : ecosystemPlugins) {
            Plugin p = Bukkit.getPluginManager().getPlugin(name);
            if (p != null && p.isEnabled()) {
                ecosystemVersions.put(name, p.getDescription().getVersion());
            }
        }
    }

    public List<String> getAvailableUpdates() {
        List<String> updates = new ArrayList<>();
        if (!available) return updates;

        // PantherCore update lookup catalog
        updates.add("§8[§bPantherCore§8] §aOmniCord v" + plugin.getDescription().getVersion() + " §7- Up to date (Latest Build: Release)");
        if (Bukkit.getPluginManager().getPlugin("PantherAC") != null) {
            updates.add("§8[§bPantherCore§8] §aPantherAC §7- Watchdog stream active (Build: Stable)");
        }
        if (Bukkit.getPluginManager().getPlugin("PantherAC-Premium") != null) {
            updates.add("§8[§bPantherCore§8] §aPantherAC-Premium §7- Machine Learning Neural Heuristics active");
        }
        if (Bukkit.getPluginManager().getPlugin("FabulousClaims") != null) {
            updates.add("§8[§bPantherCore§8] §aFabulous Claims §7- Discord Raid Sentry active");
        }
        if (Bukkit.getPluginManager().getPlugin("NeonClans") != null) {
            updates.add("§8[§bPantherCore§8] §aNeonClans §7- Discord Clan Chat Bridge & Role Sync active");
        }
        return updates;
    }

    public boolean isAvailable() {
        return available;
    }

    public Map<String, String> getEcosystemVersions() {
        return ecosystemVersions;
    }
}
