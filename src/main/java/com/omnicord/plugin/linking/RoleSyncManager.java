package com.omnicord.plugin.linking;

import com.omnicord.plugin.OmniCord;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Handles full bidirectional rank & role synchronization between Minecraft (LuckPerms/Vault) and Discord.
 */
public class RoleSyncManager {

    private final OmniCord plugin;

    public RoleSyncManager(OmniCord plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfigManager().getConfig().getBoolean("modules.role_sync", true) &&
               plugin.getConfigManager().getRoleSync().getBoolean("enabled", true);
    }

    /**
     * Synchronize a specific user's Discord roles, Minecraft ranks, and nickname.
     */
    public void syncUser(UUID uuid, String discordId) {
        if (!isEnabled()) return;
        if (!plugin.getDiscordBotManager().isConnected()) return;

        Guild guild = plugin.getDiscordBotManager().getGuild();
        if (guild == null) return;

        guild.retrieveMemberById(discordId).queue(member -> {
            if (member == null) return;

            String direction = plugin.getConfigManager().getRoleSync().getString("sync.sync_direction", "BIDIRECTIONAL").toUpperCase();

            // 1. Sync linked role
            String linkedRoleId = plugin.getConfigManager().getRoleSync().getString("linked_settings.linked_role_id", "0");
            if (!"0".equals(linkedRoleId) && !linkedRoleId.isEmpty()) {
                Role linkedRole = guild.getRoleById(linkedRoleId);
                if (linkedRole != null && guild.getSelfMember().canInteract(linkedRole) && !member.getRoles().contains(linkedRole)) {
                    guild.addRoleToMember(member, linkedRole).queue(s -> {}, err -> {});
                }
            }

            // 2. Sync Nickname
            boolean syncNick = plugin.getConfigManager().getRoleSync().getBoolean("linked_settings.sync_nicknames", true);
            if (syncNick && guild.getSelfMember().canInteract(member)) {
                String playerName = Bukkit.getOfflinePlayer(uuid).getName();
                if (playerName != null) {
                    String format = plugin.getConfigManager().getRoleSync().getString("linked_settings.nickname_format", "%player%");
                    String newNick = format.replace("%player%", playerName);
                    if (newNick.length() > 32) {
                        newNick = newNick.substring(0, 32);
                    }
                    if (!Objects.equals(member.getNickname(), newNick)) {
                        member.modifyNickname(newNick).queue(s -> {}, err -> {});
                    }
                }
            }

            // 3. Minecraft -> Discord Role Sync
            if ("BIDIRECTIONAL".equals(direction) || "MINECRAFT_TO_DISCORD".equals(direction)) {
                syncMinecraftToDiscord(uuid, member, guild);
            }

            // 4. Discord -> Minecraft Rank Sync
            if ("BIDIRECTIONAL".equals(direction) || "DISCORD_TO_MINECRAFT".equals(direction)) {
                syncDiscordToMinecraft(uuid, member);
            }

        }, error -> {});
    }

    private void syncMinecraftToDiscord(UUID uuid, Member member, Guild guild) {
        ConfigurationSection groupMap = plugin.getConfigManager().getRoleSync().getConfigurationSection("minecraft_to_discord");
        if (groupMap == null) {
            groupMap = plugin.getConfigManager().getRoleSync().getConfigurationSection("group_to_role_map");
        }
        if (groupMap == null) return;

        List<String> playerGroups = plugin.getLuckPermsHook().getPlayerGroups(uuid);
        if (playerGroups.isEmpty()) {
            playerGroups = plugin.getVaultHook().getPlayerGroups(uuid);
        }

        Set<String> rolesToAdd = new HashSet<>();
        Set<String> rolesToRemove = new HashSet<>();

        for (String groupKey : groupMap.getKeys(false)) {
            String roleId = groupMap.getString(groupKey);
            if (roleId == null || roleId.equals("0") || roleId.isEmpty()) continue;

            if (playerGroups.stream().anyMatch(g -> g.equalsIgnoreCase(groupKey))) {
                rolesToAdd.add(roleId);
            } else {
                rolesToRemove.add(roleId);
            }
        }

        // Apply additions
        for (String roleId : rolesToAdd) {
            Role role = guild.getRoleById(roleId);
            if (role != null && guild.getSelfMember().canInteract(role) && !member.getRoles().contains(role)) {
                guild.addRoleToMember(member, role).queue(s -> {}, err -> {});
            }
        }

        // Apply removals
        for (String roleId : rolesToRemove) {
            Role role = guild.getRoleById(roleId);
            if (role != null && guild.getSelfMember().canInteract(role) && member.getRoles().contains(role)) {
                guild.removeRoleFromMember(member, role).queue(s -> {}, err -> {});
            }
        }
    }

    private void syncDiscordToMinecraft(UUID uuid, Member member) {
        ConfigurationSection roleMap = plugin.getConfigManager().getRoleSync().getConfigurationSection("discord_to_minecraft");
        if (roleMap == null) return;

        Set<String> memberRoleIds = new HashSet<>();
        for (Role role : member.getRoles()) {
            memberRoleIds.add(role.getId());
        }

        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        String playerName = player.getName();
        if (playerName == null) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            for (String roleId : roleMap.getKeys(false)) {
                if (roleId == null || roleId.equals("0") || roleId.isEmpty()) continue;

                String mcGroup = roleMap.getString(roleId);
                if (mcGroup == null || mcGroup.isEmpty()) continue;

                boolean hasDiscordRole = memberRoleIds.contains(roleId);

                // Use LuckPerms command dispatch for universal group assignment
                if (hasDiscordRole) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + playerName + " parent add " + mcGroup);
                } else {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + playerName + " parent remove " + mcGroup);
                }
            }
        });
    }

    /**
     * Synchronize all online linked players.
     */
    public void syncAllOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.getAccountLinkingManager().getLink(player.getUniqueId()).thenAccept(opt -> {
                opt.ifPresent(linkedUser -> syncUser(player.getUniqueId(), linkedUser.getDiscordId()));
            });
        }
    }
}
