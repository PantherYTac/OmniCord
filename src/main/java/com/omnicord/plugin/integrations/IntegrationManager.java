package com.omnicord.plugin.integrations;

import com.omnicord.plugin.OmniCord;

/**
 * Centralized Integration and Ecosystem Hook Manager.
 * Orchestrates hooks for Vault, LuckPerms, PlaceholderAPI, PantherCore, PantherAC, Fabulous Claims, and NeonClans.
 */
public class IntegrationManager {

    private final OmniCord plugin;
    private final PantherCoreHook pantherCoreHook;
    private final PantherACHook pantherACHook;
    private final FabulousClaimsHook fabulousClaimsHook;
    private final NeonClansHook neonClansHook;

    public IntegrationManager(OmniCord plugin) {
        this.plugin = plugin;
        this.pantherCoreHook = new PantherCoreHook(plugin);
        this.pantherACHook = new PantherACHook(plugin);
        this.fabulousClaimsHook = new FabulousClaimsHook(plugin);
        this.neonClansHook = new NeonClansHook(plugin);
    }

    public void initialize() {
        pantherCoreHook.initialize();
        pantherACHook.initialize();
        fabulousClaimsHook.initialize();
        neonClansHook.initialize();
    }

    public PantherCoreHook getPantherCoreHook() { return pantherCoreHook; }
    public PantherACHook getPantherACHook() { return pantherACHook; }
    public FabulousClaimsHook getFabulousClaimsHook() { return fabulousClaimsHook; }
    public NeonClansHook getNeonClansHook() { return neonClansHook; }
}
