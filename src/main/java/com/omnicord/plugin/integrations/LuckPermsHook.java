package com.omnicord.plugin.integrations;

import com.omnicord.plugin.OmniCord;
import org.bukkit.Bukkit;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Soft-dependency LuckPerms integration for OmniCord (core).
 *
 * <p><b>Design:</b> This outer class contains ZERO direct LuckPerms imports.
 * All LuckPerms API access is isolated inside {@link LuckPermsDelegate}, which is only
 * instantiated after we confirm LuckPerms is loaded. This prevents the JVM from throwing
 * {@link NoClassDefFoundError} at plugin enable time when LuckPerms is absent.</p>
 */
public class LuckPermsHook {

    private final OmniCord plugin;
    private boolean available = false;
    private LuckPermsDelegate delegate;

    public LuckPermsHook(OmniCord plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            plugin.getLogger().info("[OmniCord] LuckPerms not found — LP integration disabled.");
            return;
        }
        try {
            // Only now do we instantiate the delegate — this is the first time
            // LuckPerms classes will be loaded by the JVM. If LP isn't present
            // we never reach this line, so NoClassDefFoundError cannot occur.
            this.delegate = new LuckPermsDelegate();
            this.available = true;
            plugin.getLogger().info("[OmniCord] Successfully hooked into LuckPerms API.");
        } catch (Throwable t) {
            this.available = false;
            plugin.getLogger().warning("[OmniCord] LuckPerms hook failed: " + t.getMessage());
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public List<String> getPlayerGroups(UUID uuid) {
        if (!available || delegate == null) return Collections.emptyList();
        return delegate.getPlayerGroups(uuid);
    }

    public String getPrimaryGroup(UUID uuid) {
        if (!available || delegate == null) return "default";
        return delegate.getPrimaryGroup(uuid);
    }

    public String getPlayerPrefix(UUID uuid) {
        if (!available || delegate == null) return "";
        return delegate.getPlayerPrefix(uuid);
    }

    public String getPlayerSuffix(UUID uuid) {
        if (!available || delegate == null) return "";
        return delegate.getPlayerSuffix(uuid);
    }

    public boolean hasPermission(UUID uuid, String permission) {
        if (!available || delegate == null) return false;
        return delegate.hasPermission(uuid, permission);
    }

    public List<String> getAllGroups() {
        if (!available || delegate == null) return Collections.emptyList();
        return delegate.getAllGroups();
    }

    public boolean createGroup(String groupName, String displayName, String prefix, int weight) {
        if (!available || delegate == null) return false;
        return delegate.createGroup(groupName, displayName, prefix, weight);
    }

    public void setPlayerGroup(UUID uuid, String groupName) {
        if (!available || delegate == null) return;
        delegate.setPlayerGroup(uuid, groupName);
    }

    // -----------------------------------------------------------------------
    // Inner delegate — the ONLY class allowed to reference net.luckperms.* types.
    // Java only resolves a class's type dependencies when that class is actually loaded.
    // By keeping LP references here and only loading this class after confirming LP
    // is present, we guarantee NoClassDefFoundError can never happen.
    // -----------------------------------------------------------------------
    static final class LuckPermsDelegate {

        private final net.luckperms.api.LuckPerms lp;

        LuckPermsDelegate() {
            this.lp = net.luckperms.api.LuckPermsProvider.get();
        }

        List<String> getPlayerGroups(UUID uuid) {
            try {
                net.luckperms.api.model.user.User user = lp.getUserManager().getUser(uuid);
                if (user == null) return Collections.emptyList();

                return user.getNodes().stream()
                        .filter(node -> node instanceof net.luckperms.api.node.types.InheritanceNode)
                        .map(node -> ((net.luckperms.api.node.types.InheritanceNode) node).getGroupName())
                        .collect(java.util.stream.Collectors.toList());
            } catch (Throwable t) {
                return Collections.emptyList();
            }
        }

        String getPrimaryGroup(UUID uuid) {
            try {
                net.luckperms.api.model.user.User user = lp.getUserManager().getUser(uuid);
                return user != null ? user.getPrimaryGroup() : "default";
            } catch (Throwable t) {
                return "default";
            }
        }

        String getPlayerPrefix(UUID uuid) {
            try {
                net.luckperms.api.model.user.User user = lp.getUserManager().getUser(uuid);
                if (user == null) return "";
                String prefix = user.getCachedData().getMetaData().getPrefix();
                return prefix != null ? prefix : "";
            } catch (Throwable t) {
                return "";
            }
        }

        String getPlayerSuffix(UUID uuid) {
            try {
                net.luckperms.api.model.user.User user = lp.getUserManager().getUser(uuid);
                if (user == null) return "";
                String suffix = user.getCachedData().getMetaData().getSuffix();
                return suffix != null ? suffix : "";
            } catch (Throwable t) {
                return "";
            }
        }

        boolean hasPermission(UUID uuid, String permission) {
            if (uuid == null || permission == null) return false;
            try {
                net.luckperms.api.model.user.User user = lp.getUserManager().getUser(uuid);
                if (user != null) {
                    return user.getCachedData().getPermissionData()
                            .checkPermission(permission).asBoolean();
                }
            } catch (Throwable ignored) {}
            return false;
        }

        List<String> getAllGroups() {
            try {
                return lp.getGroupManager().getLoadedGroups().stream()
                        .map(net.luckperms.api.model.group.Group::getName)
                        .collect(java.util.stream.Collectors.toList());
            } catch (Throwable t) {
                return Collections.emptyList();
            }
        }

        boolean createGroup(String groupName, String displayName, String prefix, int weight) {
            if (groupName == null) return false;
            try {
                String cleanName = groupName.toLowerCase().replaceAll("[^a-z0-9_-]", "");
                if (cleanName.isEmpty()) return false;

                net.luckperms.api.model.group.Group group = lp.getGroupManager().getGroup(cleanName);
                if (group == null) {
                    group = lp.getGroupManager().createAndLoadGroup(cleanName).join();
                }

                if (prefix != null && !prefix.isEmpty()) {
                    net.luckperms.api.node.types.PrefixNode prefixNode =
                            net.luckperms.api.node.types.PrefixNode.builder(prefix, weight).build();
                    group.data().add(prefixNode);
                }

                lp.getGroupManager().saveGroup(group);
                return true;
            } catch (Throwable t) {
                return false;
            }
        }

        void setPlayerGroup(UUID uuid, String groupName) {
            if (uuid == null || groupName == null) return;
            try {
                lp.getUserManager().loadUser(uuid).thenAcceptAsync(user -> {
                    if (user != null) {
                        net.luckperms.api.node.types.InheritanceNode node =
                                net.luckperms.api.node.types.InheritanceNode.builder(groupName.toLowerCase()).build();
                        user.data().add(node);
                        lp.getUserManager().saveUser(user);
                    }
                });
            } catch (Throwable ignored) {}
        }
    }
}
