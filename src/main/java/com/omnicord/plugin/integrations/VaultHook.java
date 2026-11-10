package com.omnicord.plugin.integrations;

import com.omnicord.plugin.OmniCord;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Soft-dependency Vault integration for OmniCord (core).
 *
 * <p><b>Design:</b> This outer class contains ZERO direct Vault imports.
 * All Vault API access (Economy, Chat, Permission interfaces) is isolated inside
 * {@link VaultDelegate}, which is only instantiated after confirming Vault is loaded.
 * This prevents {@link NoClassDefFoundError} when Vault is absent.</p>
 */
public class VaultHook {

    private final OmniCord plugin;
    private boolean available = false;
    private VaultDelegate delegate;

    public VaultHook(OmniCord plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().info("[OmniCord] Vault not found — Vault integration disabled.");
            return;
        }
        try {
            this.delegate = new VaultDelegate(plugin);
            this.available = delegate.isAnyProviderFound();
            if (available) {
                plugin.getLogger().info("[OmniCord] Successfully hooked into Vault.");
            }
        } catch (Throwable t) {
            this.available = false;
            plugin.getLogger().warning("[OmniCord] Vault hook failed: " + t.getMessage());
        }
    }

    public boolean isAvailable() { return available; }

    public String getPlayerPrefix(Player player) {
        if (!available || delegate == null) return "";
        return delegate.getPlayerPrefix(player);
    }

    public String getPlayerSuffix(Player player) {
        if (!available || delegate == null) return "";
        return delegate.getPlayerSuffix(player);
    }

    public List<String> getPlayerGroups(UUID uuid) {
        if (!available || delegate == null) return Collections.emptyList();
        return delegate.getPlayerGroups(uuid);
    }

    public double getBalance(OfflinePlayer player) {
        if (!available || delegate == null) return 0.0;
        return delegate.getBalance(player);
    }

    public double getBalance(UUID uuid) {
        if (!available || delegate == null || uuid == null) return 0.0;
        return delegate.getBalance(Bukkit.getOfflinePlayer(uuid));
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        if (!available || delegate == null || player == null) return false;
        return delegate.withdraw(player, amount);
    }

    public boolean withdraw(UUID uuid, double amount) {
        if (!available || delegate == null || uuid == null) return false;
        return delegate.withdraw(Bukkit.getOfflinePlayer(uuid), amount);
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        if (!available || delegate == null || player == null) return false;
        return delegate.deposit(player, amount);
    }

    public boolean deposit(UUID uuid, double amount) {
        if (!available || delegate == null || uuid == null) return false;
        return delegate.deposit(Bukkit.getOfflinePlayer(uuid), amount);
    }

    // -----------------------------------------------------------------------
    // Inner delegate — the ONLY class allowed to reference Vault API types.
    // -----------------------------------------------------------------------
    static final class VaultDelegate {

        private net.milkbowl.vault.chat.Chat chat;
        private net.milkbowl.vault.permission.Permission permission;
        private net.milkbowl.vault.economy.Economy economy;

        VaultDelegate(OmniCord plugin) {
            RegisteredServiceProvider<net.milkbowl.vault.chat.Chat> chatRsp =
                    Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.chat.Chat.class);
            if (chatRsp != null) this.chat = chatRsp.getProvider();

            RegisteredServiceProvider<net.milkbowl.vault.permission.Permission> permRsp =
                    Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.permission.Permission.class);
            if (permRsp != null) this.permission = permRsp.getProvider();

            RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> ecoRsp =
                    Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
            if (ecoRsp != null) this.economy = ecoRsp.getProvider();
        }

        boolean isAnyProviderFound() {
            return chat != null || permission != null || economy != null;
        }

        String getPlayerPrefix(Player player) {
            if (chat == null || player == null) return "";
            try {
                String prefix = chat.getPlayerPrefix(player);
                return prefix != null ? prefix : "";
            } catch (Throwable t) {
                return "";
            }
        }

        String getPlayerSuffix(Player player) {
            if (chat == null || player == null) return "";
            try {
                String suffix = chat.getPlayerSuffix(player);
                return suffix != null ? suffix : "";
            } catch (Throwable t) {
                return "";
            }
        }

        List<String> getPlayerGroups(UUID uuid) {
            if (permission == null) return Collections.emptyList();
            try {
                OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                String[] groups = permission.getPlayerGroups(null, player);
                if (groups != null) {
                    return java.util.Arrays.asList(groups);
                }
            } catch (Throwable t) {
                return Collections.emptyList();
            }
            return Collections.emptyList();
        }

        double getBalance(OfflinePlayer player) {
            if (economy == null || player == null) return 0.0;
            try {
                return economy.getBalance(player);
            } catch (Throwable t) {
                return 0.0;
            }
        }

        boolean withdraw(OfflinePlayer player, double amount) {
            if (economy == null || player == null || amount < 0) return false;
            try {
                return economy.withdrawPlayer(player, amount).transactionSuccess();
            } catch (Throwable t) {
                return false;
            }
        }

        boolean deposit(OfflinePlayer player, double amount) {
            if (economy == null || player == null || amount < 0) return false;
            try {
                return economy.depositPlayer(player, amount).transactionSuccess();
            } catch (Throwable t) {
                return false;
            }
        }
    }
}
