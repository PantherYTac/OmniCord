package com.omnicord.addons.admin;

import com.omnicord.plugin.api.OmniCordAPI;
import com.omnicord.plugin.api.addon.OmniCordAddon;
import com.omnicord.plugin.utils.ColorUtil;
import com.omnicord.plugin.utils.SkinUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

import java.awt.Color;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * OmniCord Official Addon: Advanced Administration & Moderation Suite.
 * Features:
 * 1. Discord /inventory & /enderchest viewer with [Clear Inv] & [Confiscate] actions.
 * 2. Anti-Cheat & CoreProtect Watchdog Alerts with [Freeze], [Rollback 15m], [Kick], [Ban] buttons.
 * 3. Comprehensive Combat & Death Log Inspector tracking 10s damage history.
 */
public class AdminAddon extends OmniCordAddon implements Listener {

    // Combat Log History (UUID -> Recent Damage Events)
    private final Map<UUID, List<CombatRecord>> combatLogs = new ConcurrentHashMap<>();

    // FIX Bug #19: Dedicated freeze set — avoids misusing TwoFactorAuthManager which sends confusing Discord DMs
    private final java.util.Set<UUID> frozenPlayers = ConcurrentHashMap.newKeySet();

    public static class CombatRecord {
        private final long timestamp;
        private final String damagerName;
        private final double damage;
        private final String weapon;

        public CombatRecord(long timestamp, String damagerName, double damage, String weapon) {
            this.timestamp = timestamp;
            this.damagerName = damagerName;
            this.damage = damage;
            this.weapon = weapon;
        }

        public long getTimestamp() { return timestamp; }
        public String getDamagerName() { return damagerName; }
        public double getDamage() { return damage; }
        public String getWeapon() { return weapon; }
    }

    @Override
    public String getName() { return "Admin"; }
    @Override
    public String getVersion() { return "0.0.1-alpha"; }
    @Override
    public String getAuthor() { return "PantherYTac"; }
    @Override
    public String getDescription() { return "Advanced Administration: Discord InvSee, Anti-Cheat Watchdog & Combat Logger."; }

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // 1. Register Slash Commands
        registerSlashCommand(
                Commands.slash("inventory", "View and moderate a player's live inventory")
                        .addOption(OptionType.STRING, "player", "Minecraft username", true),
                this::handleInventorySlash
        );

        registerSlashCommand(
                Commands.slash("enderchest", "View a player's live Ender Chest")
                        .addOption(OptionType.STRING, "player", "Minecraft username", true),
                this::handleEnderChestSlash
        );

        // 2. Register Button Handlers
        registerButtonHandler("admin_inv:", this::handleViewInventoryButton);
        registerButtonHandler("admin_ec:", this::handleViewEnderChestButton);
        registerButtonHandler("admin_clearinv:", this::handleClearInventoryButton);
        registerButtonHandler("admin_confiscate:", this::handleConfiscateButton);
        registerButtonHandler("admin_freeze:", this::handleFreezeButton);
        registerButtonHandler("admin_unfreeze:", this::handleUnfreezeButton);
        registerButtonHandler("admin_rollback:", this::handleRollbackButton);

        // 3. Register Bukkit Combat Listener
        registerBukkitListener(this);

        getLogger().info("Admin Addon enabled with InvSee, Anti-Cheat Watchdog & Combat Logger!");
    }

    @Override
    public void onDisable() {
        combatLogs.clear();
        frozenPlayers.clear();
        getLogger().info("Admin Addon disabled.");
    }

    // Clear freeze on quit so the set doesn't leak memory
    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        frozenPlayers.remove(event.getPlayer().getUniqueId());
    }

    // Block frozen players from moving
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFrozenMove(org.bukkit.event.player.PlayerMoveEvent event) {
        if (frozenPlayers.contains(event.getPlayer().getUniqueId())) {
            org.bukkit.Location from = event.getFrom();
            org.bukkit.Location to = event.getTo();
            if (to != null && (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ())) {
                event.setTo(from);
            }
        }
    }

    // Block frozen players from chatting
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFrozenChat(org.bukkit.event.player.AsyncPlayerChatEvent event) {
        if (frozenPlayers.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ColorUtil.colorize("&c&l[FROZEN] &cYou cannot chat while frozen by staff."));
        }
    }

    // Block frozen players from running commands
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFrozenCommand(org.bukkit.event.player.PlayerCommandPreprocessEvent event) {
        if (frozenPlayers.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ColorUtil.colorize("&c&l[FROZEN] &cYou cannot use commands while frozen by staff."));
        }
    }

    // --------------------------------------------------------------------------
    // 1. Inventory & EnderChest Viewer
    // --------------------------------------------------------------------------

    private void handleInventorySlash(SlashCommandInteractionEvent event) {
        String targetName = Objects.requireNonNull(event.getOption("player")).getAsString();
        event.deferReply().queue();

        Bukkit.getScheduler().runTask(getApi().getPluginInstance(), () -> {
            Player player = Bukkit.getPlayer(targetName);
            if (player == null || !player.isOnline()) {
                event.getHook().sendMessage("❌ Player `" + targetName + "` is not currently online.").setEphemeral(true).queue();
                return;
            }

            MessageEmbed embed = buildInventoryEmbed(player);
            event.getHook().sendMessageEmbeds(embed)
                    .addActionRow(
                            Button.primary("admin_inv:" + player.getName(), "🎒 Inventory").asDisabled(),
                            Button.secondary("admin_ec:" + player.getName(), "🔮 Ender Chest"),
                            Button.danger("admin_clearinv:" + player.getName(), "🗑️ Clear Inventory"),
                            Button.danger("admin_confiscate:" + player.getName(), "⚖️ Confiscate Hand")
                    ).queue();
        });
    }

    private void handleEnderChestSlash(SlashCommandInteractionEvent event) {
        String targetName = Objects.requireNonNull(event.getOption("player")).getAsString();
        event.deferReply().queue();

        Bukkit.getScheduler().runTask(getApi().getPluginInstance(), () -> {
            Player player = Bukkit.getPlayer(targetName);
            if (player == null || !player.isOnline()) {
                event.getHook().sendMessage("❌ Player `" + targetName + "` is not currently online.").setEphemeral(true).queue();
                return;
            }

            MessageEmbed embed = buildEnderChestEmbed(player);
            event.getHook().sendMessageEmbeds(embed)
                    .addActionRow(
                            Button.secondary("admin_inv:" + player.getName(), "🎒 Inventory"),
                            Button.primary("admin_ec:" + player.getName(), "🔮 Ender Chest").asDisabled()
                    ).queue();
        });
    }

    private void handleViewInventoryButton(ButtonInteractionEvent event) {
        String targetName = event.getComponentId().substring("admin_inv:".length());
        Bukkit.getScheduler().runTask(getApi().getPluginInstance(), () -> {
            Player player = Bukkit.getPlayer(targetName);
            if (player == null || !player.isOnline()) {
                event.reply("⚠️ Player `" + targetName + "` is offline.").setEphemeral(true).queue();
                return;
            }

            event.editMessageEmbeds(buildInventoryEmbed(player))
                    .setActionRow(
                            Button.primary("admin_inv:" + player.getName(), "🎒 Inventory").asDisabled(),
                            Button.secondary("admin_ec:" + player.getName(), "🔮 Ender Chest"),
                            Button.danger("admin_clearinv:" + player.getName(), "🗑️ Clear Inventory"),
                            Button.danger("admin_confiscate:" + player.getName(), "⚖️ Confiscate Hand")
                    ).queue();
        });
    }

    private void handleViewEnderChestButton(ButtonInteractionEvent event) {
        String targetName = event.getComponentId().substring("admin_ec:".length());
        Bukkit.getScheduler().runTask(getApi().getPluginInstance(), () -> {
            Player player = Bukkit.getPlayer(targetName);
            if (player == null || !player.isOnline()) {
                event.reply("⚠️ Player `" + targetName + "` is offline.").setEphemeral(true).queue();
                return;
            }

            event.editMessageEmbeds(buildEnderChestEmbed(player))
                    .setActionRow(
                            Button.secondary("admin_inv:" + player.getName(), "🎒 Inventory"),
                            Button.primary("admin_ec:" + player.getName(), "🔮 Ender Chest").asDisabled()
                    ).queue();
        });
    }

    private void handleClearInventoryButton(ButtonInteractionEvent event) {
        Member member = event.getMember();
        if (member == null || !member.hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("⛔ Administrator permissions required.").setEphemeral(true).queue();
            return;
        }

        String targetName = event.getComponentId().substring("admin_clearinv:".length());
        Bukkit.getScheduler().runTask(getApi().getPluginInstance(), () -> {
            Player player = Bukkit.getPlayer(targetName);
            if (player != null && player.isOnline()) {
                player.getInventory().clear();
                player.sendMessage(ColorUtil.colorize("&c[Admin] Your inventory was cleared by staff via Discord."));
                event.reply("🗑️ Cleared inventory for `" + targetName + "`.").queue();
            } else {
                event.reply("⚠️ Player `" + targetName + "` is offline.").setEphemeral(true).queue();
            }
        });
    }

    private void handleConfiscateButton(ButtonInteractionEvent event) {
        Member member = event.getMember();
        if (member == null || !member.hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("⛔ Administrator permissions required.").setEphemeral(true).queue();
            return;
        }

        String targetName = event.getComponentId().substring("admin_confiscate:".length());
        Bukkit.getScheduler().runTask(getApi().getPluginInstance(), () -> {
            Player player = Bukkit.getPlayer(targetName);
            if (player != null && player.isOnline()) {
                ItemStack item = player.getInventory().getItemInMainHand();
                if (item != null && item.getType() != Material.AIR) {
                    player.getInventory().setItemInMainHand(null);
                    player.sendMessage(ColorUtil.colorize("&c[Admin] Your held item was confiscated by staff via Discord."));
                    event.reply("⚖️ Confiscated `" + item.getType().name() + "` from `" + targetName + "`.").queue();
                } else {
                    event.reply("⚠️ Player `" + targetName + "` is not holding any item.").setEphemeral(true).queue();
                }
            } else {
                event.reply("⚠️ Player `" + targetName + "` is offline.").setEphemeral(true).queue();
            }
        });
    }

    private void handleFreezeButton(ButtonInteractionEvent event) {
        Member member = event.getMember();
        if (member == null || !member.hasPermission(Permission.KICK_MEMBERS)) {
            event.reply("⛔ Staff permissions required.").setEphemeral(true).queue();
            return;
        }

        String targetName = event.getComponentId().substring("admin_freeze:".length());
        Bukkit.getScheduler().runTask(getApi().getPluginInstance(), () -> {
            Player player = Bukkit.getPlayer(targetName);
            if (player != null && player.isOnline()) {
                // FIX Bug #19: Use dedicated frozenPlayers set instead of initiate2FA()
                // (initiate2FA sends a misleading Discord DM asking the player to "approve login")
                frozenPlayers.add(player.getUniqueId());
                player.sendMessage(ColorUtil.colorize("&c&l[FROZEN BY STAFF] &cYou have been frozen for inspection. Contact a staff member."));
                event.editMessage("❄️ Player `" + targetName + "` is frozen.")
                        .setActionRow(
                                Button.success("admin_unfreeze:" + targetName, "🔓 Unfreeze Player")
                        ).queue();
            } else {
                event.reply("⚠️ Player `" + targetName + "` is not online.").setEphemeral(true).queue();
            }
        });
    }

    private void handleUnfreezeButton(ButtonInteractionEvent event) {
        Member member = event.getMember();
        if (member == null || !member.hasPermission(Permission.KICK_MEMBERS)) {
            event.reply("⛔ Staff permissions required.").setEphemeral(true).queue();
            return;
        }

        String targetName = event.getComponentId().substring("admin_unfreeze:".length());
        Bukkit.getScheduler().runTask(getApi().getPluginInstance(), () -> {
            Player player = Bukkit.getPlayer(targetName);
            if (player != null && player.isOnline()) {
                frozenPlayers.remove(player.getUniqueId());
                player.sendMessage(ColorUtil.colorize("&a&l[UNFROZEN] &aYou have been unfrozen by staff."));
                event.reply("🔓 Player `" + targetName + "` has been unfrozen.").queue();
            } else {
                // Player offline — still remove from frozen set by name search
                Bukkit.getOnlinePlayers().stream()
                        .filter(p -> p.getName().equalsIgnoreCase(targetName))
                        .findFirst()
                        .ifPresent(p -> frozenPlayers.remove(p.getUniqueId()));
                event.reply("⚠️ Player `" + targetName + "` is offline. Freeze state cleared.").setEphemeral(true).queue();
            }
        });
    }

    private void handleRollbackButton(ButtonInteractionEvent event) {
        Member member = event.getMember();
        if (member == null || !member.hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("⛔ Administrator permissions required.").setEphemeral(true).queue();
            return;
        }

        String targetName = event.getComponentId().substring("admin_rollback:".length());
        Bukkit.getScheduler().runTask(getApi().getPluginInstance(), () -> {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "co rollback u:" + targetName + " t:15m r:#global");
            event.reply("⏪ Dispatched CoreProtect 15m rollback for `" + targetName + "`.").queue();
        });
    }

    // --------------------------------------------------------------------------
    // 2. Combat History & Death Log Inspector
    // --------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player) {
            Player victim = (Player) event.getEntity();
            // FIX Bug #10: Determine damager name AFTER the instanceof check
            String damagerName;
            String weapon = "Fists";

            if (event.getDamager() instanceof Player) {
                Player damager = (Player) event.getDamager();
                damagerName = damager.getName();
                ItemStack hand = damager.getInventory().getItemInMainHand();
                if (hand != null && hand.getType() != Material.AIR) {
                    weapon = hand.getType().name();
                }
            } else {
                // Non-player entity — use entity type as damager name
                damagerName = event.getDamager().getType().name();
            }

            CombatRecord record = new CombatRecord(System.currentTimeMillis(), damagerName, event.getFinalDamage(), weapon);
            combatLogs.computeIfAbsent(victim.getUniqueId(), k -> new ArrayList<>()).add(record);

            // Clean records older than 15s
            long cutoff = System.currentTimeMillis() - 15000L;
            combatLogs.get(victim.getUniqueId()).removeIf(r -> r.getTimestamp() < cutoff);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        List<CombatRecord> history = combatLogs.remove(player.getUniqueId());

        String combatChannelId = getConfig().getString("combat_log_channel_id", "0");
        if ("0".equals(combatChannelId) || combatChannelId.isEmpty()) return;

        TextChannel channel = getApi().getPluginInstance().getDiscordBotManager().getChannel(combatChannelId);
        if (channel == null || !channel.canTalk()) return;

        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(new Color(235, 69, 95));
        eb.setTitle("💀 Combat & Death Recap: " + player.getName());
        eb.setThumbnail(SkinUtil.getAvatarUrl("crafthead", player.getUniqueId(), player.getName()));

        String deathMsg = event.getDeathMessage() != null ? ColorUtil.stripColor(event.getDeathMessage()) : "Died";
        eb.setDescription("**" + deathMsg + "**\n📍 `" + player.getWorld().getName() + " (" + player.getLocation().getBlockX() + ", " + player.getLocation().getBlockY() + ", " + player.getLocation().getBlockZ() + ")`");

        if (history != null && !history.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (CombatRecord rec : history) {
                long agoSec = (System.currentTimeMillis() - rec.getTimestamp()) / 1000L;
                sb.append("• `").append(agoSec).append("s ago`: Took **").append(String.format("%.1f", rec.getDamage())).append(" DMG** from **").append(rec.getDamagerName()).append("** (").append(rec.getWeapon()).append(")\n");
            }
            eb.addField("⚔️ Last 15s Combat Activity", sb.toString(), false);
        }

        eb.setFooter("OmniCord Admin Combat Logger", null);
        eb.setTimestamp(Instant.now());

        channel.sendMessageEmbeds(eb.build()).queue();
    }

    // --------------------------------------------------------------------------
    // Embed Builders
    // --------------------------------------------------------------------------

    private MessageEmbed buildInventoryEmbed(Player player) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(new Color(88, 101, 242));
        eb.setTitle("🎒 " + player.getName() + " — Player Inventory");
        eb.setThumbnail(SkinUtil.getAvatarUrl("crafthead", player.getUniqueId(), player.getName()));

        double health = Math.round(player.getHealth() * 10.0) / 10.0;
        eb.addField("📊 Status", "❤️ `" + health + "/20` | 🍗 `" + player.getFoodLevel() + "/20` | ⭐ Level `" + player.getLevel() + "`", false);

        PlayerInventory inv = player.getInventory();
        eb.addField("🛡️ Equipped Armor",
                "🪖 **Helmet:** " + formatItem(inv.getHelmet()) + "\n" +
                "🦺 **Chestplate:** " + formatItem(inv.getChestplate()) + "\n" +
                "👖 **Leggings:** " + formatItem(inv.getLeggings()) + "\n" +
                "👢 **Boots:** " + formatItem(inv.getBoots()) + "\n" +
                "🗡️ **Main Hand:** " + formatItem(inv.getItemInMainHand()) + "\n" +
                "🛡️ **Off Hand:** " + formatItem(inv.getItemInOffHand()), false);

        List<String> items = new ArrayList<>();
        ItemStack[] contents = inv.getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() != Material.AIR) {
                items.add("`[" + (i < 9 ? "Hotbar " + (i + 1) : "Slot " + (i + 1)) + "]` " + formatItem(item));
            }
        }

        String invDisplay = items.isEmpty() ? "*Empty*" : String.join("\n", items.subList(0, Math.min(15, items.size())));
        if (items.size() > 15) invDisplay += "\n*(and " + (items.size() - 15) + " more items...)*";
        eb.addField("📦 Stored Inventory (" + items.size() + ")", invDisplay, false);

        Collection<PotionEffect> effects = player.getActivePotionEffects();
        if (!effects.isEmpty()) {
            String effectList = effects.stream()
                    .map(e -> "`" + e.getType().getName() + " " + (e.getAmplifier() + 1) + "`")
                    .collect(Collectors.joining(", "));
            eb.addField("🧪 Active Effects", effectList, false);
        }

        eb.setFooter("OmniCord Admin Engine • " + player.getWorld().getName(), null);
        eb.setTimestamp(Instant.now());
        return eb.build();
    }

    private MessageEmbed buildEnderChestEmbed(Player player) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(new Color(155, 89, 182));
        eb.setTitle("🔮 " + player.getName() + " — Ender Chest");
        eb.setThumbnail(SkinUtil.getAvatarUrl("crafthead", player.getUniqueId(), player.getName()));

        List<String> items = new ArrayList<>();
        ItemStack[] contents = player.getEnderChest().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() != Material.AIR) {
                items.add("`[Slot " + (i + 1) + "]` " + formatItem(item));
            }
        }

        String ecDisplay = items.isEmpty() ? "*Empty*" : String.join("\n", items.subList(0, Math.min(20, items.size())));
        eb.addField("🔮 Ender Chest Items (" + items.size() + "/27)", ecDisplay, false);
        eb.setFooter("OmniCord Admin Engine", null);
        eb.setTimestamp(Instant.now());
        return eb.build();
    }

    private String formatItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return "*Empty*";
        String name = (item.hasItemMeta() && item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) ?
                "**" + ColorUtil.stripColor(item.getItemMeta().getDisplayName()) + "**" : item.getType().name();
        return name + (item.getAmount() > 1 ? " `x" + item.getAmount() + "`" : "");
    }
}
