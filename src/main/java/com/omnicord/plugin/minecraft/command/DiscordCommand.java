package com.omnicord.plugin.minecraft.command;

import com.omnicord.plugin.OmniCord;
import com.omnicord.plugin.storage.model.LinkedUser;
import com.omnicord.plugin.utils.ColorUtil;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * In-game player command for Discord server information, clickable invites, linking and synchronization.
 */
public class DiscordCommand implements CommandExecutor, TabCompleter {

    private final OmniCord plugin;

    public DiscordCommand(OmniCord plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("commands.player_only"));
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            handleInviteAndInfo(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "link":
                handleLink(player);
                break;
            case "unlink":
                handleUnlink(player);
                break;
            case "sync":
                handleSync(player);
                break;
            case "invite":
            case "info":
            default:
                handleInviteAndInfo(player);
                break;
        }

        return true;
    }

    private void handleInviteAndInfo(Player player) {
        String serverName = plugin.getConfigManager().getConfig().getString("general.server_name", "Minecraft Server");
        String invite = plugin.getConfigManager().getConfig().getString("linking.discord_invite_url", "https://discord.gg/yourserver");

        plugin.getAccountLinkingManager().getLink(player.getUniqueId()).thenAccept(opt -> {
            player.sendMessage(ColorUtil.colorize("&#5865F2╭──────────────────────────────────────────────────╮"));
            player.sendMessage(ColorUtil.colorize("&#5865F2│  &#5865F2❖ &#7289DA&l" + serverName + " — Official Discord"));
            player.sendMessage(ColorUtil.colorize("&#5865F2│  &7Join our community to chat, get rewards & open tickets!"));

            // Clickable Discord Invite Link
            try {
                TextComponent inviteComp = new TextComponent(ColorUtil.colorize("&#5865F2│  &#57F287▶ &l[✦ CLICK TO JOIN OUR DISCORD SERVER ✦]"));
                inviteComp.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, invite));
                inviteComp.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ColorUtil.colorize("&aClick to open: &#5865F2" + invite))));
                player.spigot().sendMessage(inviteComp);
            } catch (Throwable t) {
                player.sendMessage(ColorUtil.colorize("&#5865F2│  &bInvite: &#5865F2" + invite));
            }

            if (opt.isPresent()) {
                LinkedUser user = opt.get();
                player.sendMessage(ColorUtil.colorize("&#5865F2│  &7Linked Account: &#57F287✔ &#7289DA" + user.getDiscordTag()));
            } else {
                player.sendMessage(ColorUtil.colorize("&#5865F2│  &7Account Status: &#ED4245Not Linked &8(Type &e/discord link&8)"));
            }

            player.sendMessage(ColorUtil.colorize("&#5865F2╰──────────────────────────────────────────────────╯"));
        });
    }

    private void handleLink(Player player) {
        plugin.getAccountLinkingManager().getLink(player.getUniqueId()).thenAccept(opt -> {
            if (opt.isPresent()) {
                String msg = plugin.getConfigManager().getMessage("linking.already_linked")
                        .replace("%discord_tag%", opt.get().getDiscordTag());
                player.sendMessage(msg);
                return;
            }

            plugin.getAccountLinkingManager().generateLinkCode(player.getUniqueId()).thenAccept(code -> {
                int expireMinutes = plugin.getConfigManager().getConfig().getInt("linking.link_code_expire_minutes", 10);
                String invite = plugin.getConfigManager().getConfig().getString("linking.discord_invite_url", "https://discord.gg/yourserver");

                player.sendMessage(ColorUtil.colorize("&#5865F2╭──────────────────────────────────────────────────╮"));
                player.sendMessage(ColorUtil.colorize("&#5865F2│  &#5865F2❖ &#7289DA&lDiscord Account Verification"));
                player.sendMessage(ColorUtil.colorize("&#5865F2│  &7Your 6-digit link PIN is: &#57F287&l" + code));

                // Click-to-copy button
                try {
                    TextComponent copyComp = new TextComponent(ColorUtil.colorize("&#5865F2│  &#8A9CFE▶ [Click to Copy /link command]"));
                    copyComp.setClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, "/link code:" + code));
                    copyComp.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ColorUtil.colorize("&aClick to copy &f/link code:" + code + " &ato clipboard!"))));

                    TextComponent inviteComp = new TextComponent(ColorUtil.colorize("&#5865F2│  &#7289DA▶ [Click to Join Discord Server]"));
                    inviteComp.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, invite));
                    inviteComp.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ColorUtil.colorize("&bClick to open Discord invite link!"))));

                    player.spigot().sendMessage(copyComp);
                    player.spigot().sendMessage(inviteComp);
                } catch (Throwable t) {
                    player.sendMessage(ColorUtil.colorize("&#5865F2│  &7Run in Discord: &#8A9CFE/link code:" + code));
                }

                player.sendMessage(ColorUtil.colorize("&#5865F2│  &8• &cExpires in &e" + expireMinutes + " minutes"));
                player.sendMessage(ColorUtil.colorize("&#5865F2╰──────────────────────────────────────────────────╯"));
            });
        });
    }

    private void handleUnlink(Player player) {
        plugin.getAccountLinkingManager().getLink(player.getUniqueId()).thenAccept(opt -> {
            if (!opt.isPresent()) {
                player.sendMessage(plugin.getConfigManager().getMessage("linking.not_linked"));
                return;
            }

            plugin.getAccountLinkingManager().unlink(player.getUniqueId()).thenRun(() -> {
                player.sendMessage(plugin.getConfigManager().getMessage("linking.unlink_success"));
            });
        });
    }

    private void handleSync(Player player) {
        plugin.getAccountLinkingManager().getLink(player.getUniqueId()).thenAccept(opt -> {
            if (!opt.isPresent()) {
                player.sendMessage(plugin.getConfigManager().getMessage("linking.not_linked"));
                return;
            }

            plugin.getRoleSyncManager().syncUser(player.getUniqueId(), opt.get().getDiscordId());
            player.sendMessage(plugin.getConfigManager().getMessage("commands.sync_success"));
        });
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = Arrays.asList("invite", "link", "unlink", "sync", "info");
            List<String> matches = new ArrayList<>();
            for (String s : subs) {
                if (s.startsWith(args[0].toLowerCase())) matches.add(s);
            }
            return matches;
        }
        return Collections.emptyList();
    }
}
