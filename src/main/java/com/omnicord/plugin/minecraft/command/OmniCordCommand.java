package com.omnicord.plugin.minecraft.command;

import com.omnicord.plugin.OmniCord;
import com.omnicord.plugin.api.addon.OmniCordAddon;
import com.omnicord.plugin.utils.ColorUtil;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Master administrative command for OmniCord, Addon management, and Ecosystem status.
 * Features an interactive, clean, modern help menu.
 */
public class OmniCordCommand implements CommandExecutor, TabCompleter {

    private final OmniCord plugin;

    public OmniCordCommand(OmniCord plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender, label);
            return true;
        }

        if (!sender.hasPermission("omnicord.admin")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("commands.no_permission"));
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "setup":
                handleSetup(sender, args, label);
                break;
            case "reload":
            case "rl":
                handleReload(sender);
                break;
            case "status":
                handleStatus(sender);
                break;
            case "sync":
                handleSync(sender);
                break;
            case "updates":
            case "update":
                handleUpdates(sender);
                break;
            case "addons":
            case "addon":
                handleAddons(sender, args, label);
                break;
            default:
                sendHelp(sender, label);
                break;
        }

        return true;
    }

    private void handleReload(CommandSender sender) {
        long start = System.currentTimeMillis();
        plugin.reloadPlugin();
        long duration = System.currentTimeMillis() - start;

        String msg = plugin.getConfigManager().getMessage("commands.reload_success")
                .replace("%ms%", String.valueOf(duration));
        sender.sendMessage(msg);
    }

    private void handleStatus(CommandSender sender) {
        boolean connected = plugin.getDiscordBotManager().isConnected();
        if (connected) {
            long ping = plugin.getDiscordBotManager().getJda().getGatewayPing();
            String msg = plugin.getConfigManager().getMessage("commands.status_connected")
                    .replace("%ping%", String.valueOf(ping));
            sender.sendMessage(msg);
        } else {
            sender.sendMessage(plugin.getConfigManager().getMessage("commands.status_disconnected"));
        }
    }

    private void handleSync(CommandSender sender) {
        sender.sendMessage(plugin.getConfigManager().getGlobalPrefix() + ColorUtil.colorize("&eSynchronizing all online linked players with Discord..."));
        plugin.getRoleSyncManager().syncAllOnline();
        sender.sendMessage(plugin.getConfigManager().getMessage("commands.sync_success"));
    }

    private void handleSetup(CommandSender sender, String[] args, String label) {
        String p = plugin.getConfigManager().getGlobalPrefix();

        if (args.length == 1) {
            sender.sendMessage("");
            sender.sendMessage(ColorUtil.colorize("&8&m════════════════════════════════════════════════════════════"));
            sender.sendMessage(p + ColorUtil.colorize("&6&lIN-GAME SETUP WIZARD &8(&7Quick Configuration&8)"));
            sender.sendMessage(ColorUtil.colorize("&7Use the commands below to configure OmniCord entirely in-game:"));
            sender.sendMessage("");

            sendInteractiveCommand(sender, "/" + label + " setup token <bot_token>", "Configure your Discord Bot Token");
            sendInteractiveCommand(sender, "/" + label + " setup guild <guild_id>", "Set target Discord Server (Guild) ID");
            sendInteractiveCommand(sender, "/" + label + " setup invite <discord_url>", "Set Discord Invite URL shown to players");
            sendInteractiveCommand(sender, "/" + label + " setup channel chat <channel_id>", "Set 2-way Minecraft <-> Discord Chat channel");
            sendInteractiveCommand(sender, "/" + label + " setup channel console <channel_id>", "Set live Console & remote execution channel");
            sendInteractiveCommand(sender, "/" + label + " setup channel staff <channel_id>", "Set private Staff Chat channel");
            sendInteractiveCommand(sender, "/" + label + " setup channel reports <channel_id>", "Set in-game Reports & Watchdog channel");
            sendInteractiveCommand(sender, "/" + label + " setup channel voice <channel_id>", "Set live Voice Stats channel ID");
            sendInteractiveCommand(sender, "/" + label + " setup role <mc_group> <role_id>", "Link a Minecraft rank to a Discord role ID");
            sendInteractiveCommand(sender, "/" + label + " reload", "Apply all changes and restart Discord Bot");

            sender.sendMessage("");
            sender.sendMessage(ColorUtil.colorize("&e💡 Tip: &7Click any command above to auto-fill it in your chat bar!"));
            sender.sendMessage(ColorUtil.colorize("&8&m════════════════════════════════════════════════════════════"));
            return;
        }

        String sub = args[1].toLowerCase();
        switch (sub) {
            case "token":
                if (args.length < 3) {
                    sender.sendMessage(p + ColorUtil.colorize("&cUsage: /" + label + " setup token <bot_token>"));
                    return;
                }
                plugin.getConfigManager().getConfig().set("bot.token", args[2].trim());
                plugin.getConfigManager().saveConfig();
                sender.sendMessage(p + ColorUtil.colorize("&aBot token saved. Restarting Discord bot..."));
                plugin.reloadPlugin();
                break;

            case "guild":
                if (args.length < 3) {
                    sender.sendMessage(p + ColorUtil.colorize("&cUsage: /" + label + " setup guild <guild_id>"));
                    return;
                }
                plugin.getConfigManager().getConfig().set("bot.guild_id", args[2].trim());
                plugin.getConfigManager().saveConfig();
                sender.sendMessage(p + ColorUtil.colorize("&aDiscord Guild ID saved (&f" + args[2] + "&a). Run &e/" + label + " reload &ato apply."));
                break;

            case "invite":
                if (args.length < 3) {
                    sender.sendMessage(p + ColorUtil.colorize("&cUsage: /" + label + " setup invite <discord_invite_url>"));
                    return;
                }
                plugin.getConfigManager().getConfig().set("linking.discord_invite_url", args[2].trim());
                plugin.getConfigManager().saveConfig();
                sender.sendMessage(p + ColorUtil.colorize("&aDiscord Invite URL updated (&f" + args[2] + "&a)."));
                break;

            case "channel":
                if (args.length < 4) {
                    sender.sendMessage(p + ColorUtil.colorize("&cUsage: /" + label + " setup channel <chat|console|staff|reports|voice> <channel_id>"));
                    return;
                }
                String chType = args[2].toLowerCase();
                String chId = args[3].trim();
                String configKey;
                switch (chType) {
                    case "chat": configKey = "channels.chat"; break;
                    case "console": configKey = "channels.console"; break;
                    case "staff": configKey = "channels.staff_chat"; break;
                    case "reports": configKey = "channels.reports"; break;
                    case "voice": configKey = "channels.voice_stats_channel_id"; break;
                    default:
                        sender.sendMessage(p + ColorUtil.colorize("&cInvalid channel type. Choose: chat, console, staff, reports, voice"));
                        return;
                }
                plugin.getConfigManager().getConfig().set(configKey, chId);
                plugin.getConfigManager().saveConfig();
                sender.sendMessage(p + ColorUtil.colorize("&aChannel &e" + chType + " &aset to ID &f" + chId + "&a."));
                break;

            case "role":
                if (args.length < 4) {
                    sender.sendMessage(p + ColorUtil.colorize("&cUsage: /" + label + " setup role <minecraft_group> <discord_role_id>"));
                    return;
                }
                String group = args[2].toLowerCase();
                String roleId = args[3].trim();
                plugin.getConfigManager().getConfig().set("role_sync.groups." + group, roleId);
                plugin.getConfigManager().saveConfig();
                sender.sendMessage(p + ColorUtil.colorize("&aLinked rank &e" + group + " &ato Discord Role ID &f" + roleId + "&a."));
                break;

            default:
                sender.sendMessage(p + ColorUtil.colorize("&cUnknown setup option. Run &e/" + label + " setup &cfor full list."));
                break;
        }
    }

    private void handleUpdates(CommandSender sender) {
        String p = plugin.getConfigManager().getGlobalPrefix();
        if (plugin.getIntegrationManager() != null && plugin.getIntegrationManager().getPantherCoreHook().isAvailable()) {
            sender.sendMessage(ColorUtil.colorize("&8&m──────────────────────────────────────────"));
            sender.sendMessage(p + ColorUtil.colorize("&bPanther Ecosystem Update Registry:"));
            for (String update : plugin.getIntegrationManager().getPantherCoreHook().getAvailableUpdates()) {
                sender.sendMessage(ColorUtil.colorize(update));
            }
            sender.sendMessage(ColorUtil.colorize("&8&m──────────────────────────────────────────"));
        } else {
            sender.sendMessage(p + ColorUtil.colorize("&aOmniCord v" + plugin.getDescription().getVersion() + " &7is running the latest release. &8(Author: PantherYTac)"));
        }
    }

    private void handleAddons(CommandSender sender, String[] args, String label) {
        String p = plugin.getConfigManager().getGlobalPrefix();
        Map<String, OmniCordAddon> addons = plugin.getAddonManager().getLoadedAddons();

        if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
            sender.sendMessage(ColorUtil.colorize("&8&m──────────────────────────────────────────"));
            sender.sendMessage(p + ColorUtil.colorize("&fLoaded Addons &8(&#57F287" + addons.size() + "&8):"));
            if (addons.isEmpty()) {
                sender.sendMessage(ColorUtil.colorize("  &7No addons installed. Place addon JARs into &eplugins/OmniCord/addons/"));
            } else {
                for (OmniCordAddon addon : addons.values()) {
                    String status = addon.isEnabled() ? "&#57F287●" : "&#ED4245●";
                    sender.sendMessage(ColorUtil.colorize("  " + status + " &#8A9CFE" + addon.getName() + " &7v" + addon.getVersion() + " &8by &f" + addon.getAuthor()));
                }
            }
            sender.sendMessage(ColorUtil.colorize("&8&m──────────────────────────────────────────"));
            return;
        }

        if (args[1].equalsIgnoreCase("reload")) {
            if (args.length < 3) {
                sender.sendMessage(p + ColorUtil.colorize("&cUsage: /" + label + " addon reload <addonName|all>"));
                return;
            }

            String target = args[2].toLowerCase();
            if (target.equals("all")) {
                plugin.getAddonManager().reloadAddons();
                sender.sendMessage(p + ColorUtil.colorize("&#57F287All addons reloaded successfully."));
                return;
            }

            OmniCordAddon addon = plugin.getAddonManager().getAddon(target);
            if (addon == null) {
                sender.sendMessage(p + ColorUtil.colorize("&cAddon '" + target + "' was not found."));
                return;
            }

            addon.onReload();
            sender.sendMessage(p + ColorUtil.colorize("&#57F287Addon &#8A9CFE" + addon.getName() + " &#57F287reloaded."));
            return;
        }

        sender.sendMessage(p + ColorUtil.colorize("&cUsage: /" + label + " addons <list|reload>"));
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(ColorUtil.colorize("&8&m───────&r &#5865F2❖ &#8A9CFE&lOMNICORD &7v" + plugin.getDescription().getVersion() + " &8&m───────"));
        sender.sendMessage(ColorUtil.colorize("&7Next-Gen Minecraft to Discord Bridge Suite &8| &fPantherYTac"));
        sender.sendMessage("");

        // 1. Core Player Commands
        sender.sendMessage(ColorUtil.colorize("&#57F287&l✦ Core Player Commands:"));
        sendInteractiveCommand(sender, "/discord link", "Link your Minecraft account with Discord (/discord link <code>)");
        sendInteractiveCommand(sender, "/discord unlink", "Disconnect your linked Discord account");
        sendInteractiveCommand(sender, "/discord info", "View your Discord linking and sync status");
        sendInteractiveCommand(sender, "/report <player> <reason>", "Submit a player report directly to Discord staff");
        sendInteractiveCommand(sender, "/staffchat <msg>", "Send a message to the staff-only Discord channel");
        sender.sendMessage("");

        // 2. Administrative Commands
        if (sender.hasPermission("omnicord.admin")) {
            sender.sendMessage(ColorUtil.colorize("&#FEE75C&l✦ Administrative & In-Game Setup:"));
            sendInteractiveCommand(sender, "/" + label + " setup", "Open In-Game Setup Wizard (token, guild, channels, roles)");
            sendInteractiveCommand(sender, "/" + label + " reload", "Reload plugin configs and Discord bot");
            sendInteractiveCommand(sender, "/" + label + " status", "Check Discord Gateway latency & connection");
            sendInteractiveCommand(sender, "/" + label + " sync", "Force-synchronize all online player roles");
            sendInteractiveCommand(sender, "/" + label + " addons [list|reload]", "Manage installed addon modules");
            if (plugin.getIntegrationManager() != null && plugin.getIntegrationManager().getPantherCoreHook().isAvailable()) {
                sendInteractiveCommand(sender, "/" + label + " updates", "View Panther ecosystem update catalog");
            }
            sender.sendMessage("");
        }

        // 3. Dynamic Addon Commands
        Map<String, OmniCordAddon> addons = plugin.getAddonManager().getLoadedAddons();
        if (!addons.isEmpty()) {
            sender.sendMessage(ColorUtil.colorize("&#EB459E&l✦ Active Addon Commands:"));
            for (OmniCordAddon addon : addons.values()) {
                if (!addon.isEnabled()) continue;
                String name = addon.getName().toLowerCase();
                switch (name) {
                    case "admin":
                        sendInteractiveCommand(sender, "/inventory <player>", "View interactive live inventory snapshot in Discord");
                        sendInteractiveCommand(sender, "/enderchest <player>", "View live Ender Chest snapshot in Discord");
                        break;
                    case "engagement":
                        sendInteractiveCommand(sender, "/giveaway [enter|list]", "Join or view active Discord giveaways");
                        sendInteractiveCommand(sender, "/auctions", "View live server auction house marketplace");
                        break;
                    case "community":
                        sendInteractiveCommand(sender, "/whereis <player>", "View live GPS coordinates and world map card");
                        sendInteractiveCommand(sender, "/map", "Get link to live server web map");
                        sendInteractiveCommand(sender, "/voice <name>", "Create a temporary voice channel in Discord");
                        break;
                    default:
                        sender.sendMessage(ColorUtil.colorize("  &#8A9CFE" + addon.getName() + " &8- &7" + addon.getDescription()));
                        break;
                }
            }
            sender.sendMessage("");
        }

        // 4. Ecosystem Integrations
        if (plugin.getIntegrationManager() != null) {
            List<String> hooks = new ArrayList<>();
            if (plugin.getIntegrationManager().getPantherCoreHook().isAvailable()) hooks.add("&#57F287PantherCore");
            if (plugin.getIntegrationManager().getPantherACHook().isAvailable()) hooks.add("&#57F287" + plugin.getIntegrationManager().getPantherACHook().getAcVariant());
            if (plugin.getIntegrationManager().getFabulousClaimsHook().isAvailable()) hooks.add("&#57F287Fabulous Claims");
            if (plugin.getIntegrationManager().getNeonClansHook().isAvailable()) hooks.add("&#57F287NeonClans");

            if (!hooks.isEmpty()) {
                sender.sendMessage(ColorUtil.colorize("&8[§bPanther Ecosystem§8] &7Active Hooks: " + String.join("&7, ", hooks)));
            }
        }

        sender.sendMessage(ColorUtil.colorize("&8&m──────────────────────────────────────────"));
    }

    private void sendInteractiveCommand(CommandSender sender, String cmd, String description) {
        if (sender instanceof Player) {
            Player p = (Player) sender;
            TextComponent comp = new TextComponent(ColorUtil.colorize("  &#7289DA" + cmd + " &8» &f" + description));
            comp.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(ColorUtil.colorize("&eClick to run: &b" + cmd)).create()));
            comp.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, cmd.split(" ")[0] + " "));
            p.spigot().sendMessage(comp);
        } else {
            sender.sendMessage(ColorUtil.colorize("  &#7289DA" + cmd + " &8» &f" + description));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(Arrays.asList("help", "setup", "reload", "status", "sync", "addons", "updates"));
            List<String> matches = new ArrayList<>();
            for (String s : subs) {
                if (s.startsWith(args[0].toLowerCase())) matches.add(s);
            }
            return matches;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setup")) {
            return Arrays.asList("token", "guild", "invite", "channel", "role");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setup") && args[1].equalsIgnoreCase("channel")) {
            return Arrays.asList("chat", "console", "staff", "reports", "voice");
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("addons") || args[0].equalsIgnoreCase("addon"))) {
            return Arrays.asList("list", "reload");
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("reload")) {
            List<String> matches = new ArrayList<>();
            matches.add("all");
            matches.addAll(plugin.getAddonManager().getLoadedAddons().keySet());
            return matches;
        }
        return Collections.emptyList();
    }
}
