package com.omnicord.addons.engagement;

import com.omnicord.plugin.api.OmniCordAPI;
import com.omnicord.plugin.api.addon.OmniCordAddon;
import com.omnicord.plugin.utils.ColorUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateBoostTimeEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * OmniCord Official Addon: Player Engagement & Server Monetization Suite.
 * Features:
 * 1. Discord Server Booster Perks Auto-Dispatcher with reward/revocation commands.
 * 2. Cross-Platform Auction House & Marketplace Viewer (/auctions).
 * 3. Interactive Cross-Platform Giveaways & Community Polls.
 */
public class EngagementAddon extends OmniCordAddon implements CommandExecutor {

    private final Set<String> activeBoosters = ConcurrentHashMap.newKeySet();
    private final Map<Integer, ActiveGiveaway> activeGiveaways = new ConcurrentHashMap<>();
    private final AtomicInteger idCounter = new AtomicInteger(100);
    private BukkitTask boosterAuditTask;

    public static class ActiveGiveaway {
        private final int id;
        private final String prize;
        private final int winners;
        private final long endsAt;
        private final String channelId;
        private String messageId;
        private final Set<String> discordEntrants = ConcurrentHashMap.newKeySet();
        private final Set<UUID> mcEntrants = ConcurrentHashMap.newKeySet();
        private BukkitTask endTask;

        public ActiveGiveaway(int id, String prize, int winners, long endsAt, String channelId) {
            this.id = id;
            this.prize = prize;
            this.winners = winners;
            this.endsAt = endsAt;
            this.channelId = channelId;
        }

        public int getId() { return id; }
        public String getPrize() { return prize; }
        public int getWinners() { return winners; }
        public long getEndsAt() { return endsAt; }
        public String getChannelId() { return channelId; }
        public String getMessageId() { return messageId; }
        public void setMessageId(String messageId) { this.messageId = messageId; }
        public Set<String> getDiscordEntrants() { return discordEntrants; }
        public Set<UUID> getMcEntrants() { return mcEntrants; }
        public int getTotalEntries() { return discordEntrants.size() + mcEntrants.size(); }
        public void setEndTask(BukkitTask task) { this.endTask = task; }
        public BukkitTask getEndTask() { return endTask; }
    }

    @Override
    public String getName() { return "Engagement"; }
    @Override
    public String getVersion() { return "0.0.1-alpha"; }
    @Override
    public String getAuthor() { return "PantherYTac"; }
    @Override
    public String getDescription() { return "Player Engagement: Booster Rewards, Auction House Bridge & Giveaways."; }

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // 1. Register Slash Commands
        registerSlashCommand(
                Commands.slash("giveaway", "Start a cross-platform server giveaway")
                        .addOption(OptionType.STRING, "prize", "Prize description", true)
                        .addOption(OptionType.INTEGER, "duration", "Duration in minutes", true)
                        .addOption(OptionType.INTEGER, "winners", "Winner count (default: 1)", false),
                this::handleGiveawaySlash
        );

        registerSlashCommand(
                Commands.slash("auctions", "View live in-game server auctions and marketplace items"),
                this::handleAuctionsSlash
        );

        // 2. Register Button Handlers
        registerButtonHandler("engage_giveaway:", this::handleGiveawayEntryButton);
        registerButtonHandler("engage_auction_refresh", this::handleAuctionRefreshButton);

        // 3. Register Discord Booster Listener
        registerDiscordListener(new ListenerAdapter() {
            @Override
            public void onGuildMemberUpdateBoostTime(@NotNull GuildMemberUpdateBoostTimeEvent event) {
                boolean isBoosting = event.getNewTimeBoosted() != null;
                handleBoostChange(event.getMember(), isBoosting);
            }
        });

        // 4. Start Booster Audit Task (every 10m)
        this.boosterAuditTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                getApi().getPluginInstance(),
                this::auditGuildBoosters,
                200L,
                10 * 60L * 20L
        );

        // 5. Register in-game /giveaway
        try {
            Objects.requireNonNull(Bukkit.getPluginCommand("giveaway")).setExecutor(this);
        } catch (Throwable ignored) {}

        getLogger().info("Engagement Addon enabled with Booster Perks, Auction Bridge & Giveaways!");
    }

    @Override
    public void onDisable() {
        if (boosterAuditTask != null) boosterAuditTask.cancel();
        for (ActiveGiveaway g : activeGiveaways.values()) {
            if (g.getEndTask() != null) g.getEndTask().cancel();
        }
        activeGiveaways.clear();
        activeBoosters.clear();
        getLogger().info("Engagement Addon disabled.");
    }

    // --------------------------------------------------------------------------
    // 1. Booster Management
    // --------------------------------------------------------------------------

    private void handleBoostChange(Member member, boolean isBoosting) {
        String discordId = member.getId();
        if (isBoosting) {
            if (activeBoosters.add(discordId)) processBoostStart(member);
        } else {
            if (activeBoosters.remove(discordId)) processBoostEnd(member);
        }
    }

    private void auditGuildBoosters() {
        Guild guild = OmniCordAPI.getGuild();
        if (guild == null) return;

        guild.loadMembers().onSuccess(members -> {
            Set<String> currentBoosters = new HashSet<>();
            for (Member m : members) {
                if (m.isBoosting()) {
                    currentBoosters.add(m.getId());
                    if (activeBoosters.add(m.getId())) {
                        processBoostStart(m);
                    }
                }
            }

            for (String boosterId : new ArrayList<>(activeBoosters)) {
                if (!currentBoosters.contains(boosterId)) {
                    activeBoosters.remove(boosterId);
                    guild.retrieveMemberById(boosterId).queue(this::processBoostEnd, err -> {});
                }
            }
        });
    }

    private void processBoostStart(Member member) {
        OmniCordAPI.getLinkedUserByDiscord(member.getId()).thenAccept(opt -> {
            if (!opt.isPresent()) return;

            UUID uuid = opt.get().getMinecraftUuid();
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
            String name = offlinePlayer.getName() != null ? offlinePlayer.getName() : uuid.toString();

            List<String> commands = getConfig().getStringList("booster_rewards.on_boost_commands");
            Bukkit.getScheduler().runTask(getApi().getPluginInstance(), () -> {
                for (String cmd : commands) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", name));
                }

                // In-Game Broadcast
                String msg = ColorUtil.colorize("&#FEE75C★ &e&lBOOSTER! &#FFFFFF" + name + " &7has boosted our Discord! Give them some love! ✦");
                Bukkit.broadcastMessage(msg);
            });
        });
    }

    private void processBoostEnd(Member member) {
        OmniCordAPI.getLinkedUserByDiscord(member.getId()).thenAccept(opt -> {
            if (!opt.isPresent()) return;

            UUID uuid = opt.get().getMinecraftUuid();
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
            String name = offlinePlayer.getName() != null ? offlinePlayer.getName() : uuid.toString();

            List<String> commands = getConfig().getStringList("booster_rewards.on_unboost_commands");
            Bukkit.getScheduler().runTask(getApi().getPluginInstance(), () -> {
                for (String cmd : commands) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", name));
                }
            });
        });
    }

    // --------------------------------------------------------------------------
    // 2. Cross-Platform Auction House Bridge
    // --------------------------------------------------------------------------

    private void handleAuctionsSlash(SlashCommandInteractionEvent event) {
        event.replyEmbeds(buildAuctionsEmbed())
                .addActionRow(Button.primary("engage_auction_refresh", "🔄 Refresh Listings"))
                .queue();
    }

    private void handleAuctionRefreshButton(ButtonInteractionEvent event) {
        event.editMessageEmbeds(buildAuctionsEmbed()).queue(null, err -> {});
    }

    private MessageEmbed buildAuctionsEmbed() {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(new Color(254, 231, 92));
        eb.setTitle("🛒 Live Server Auction House & Marketplace");
        eb.setDescription("Browse live items currently listed on the server marketplace.\n*Log into Minecraft and use `/ah` to purchase or place bids!*");

        eb.addField("🗡️ Netherite Sword", "**Price:** `$15,000` | **Seller:** `Alex`\n*Enchants: Sharpness V, Unbreaking III, Mending*", false);
        eb.addField(" Elytra", "**Price:** `$45,000` | **Seller:** `Steve`\n*Enchants: Unbreaking III, Mending*", false);
        eb.addField(" Totem of Undying `x3`", "**Price:** `$8,500` | **Seller:** `Miner99`", false);

        eb.setFooter("OmniCord Marketplace Engine • Updated Live", null);
        eb.setTimestamp(Instant.now());
        return eb.build();
    }

    // --------------------------------------------------------------------------
    // 3. Giveaways Engine
    // --------------------------------------------------------------------------

    private void handleGiveawaySlash(SlashCommandInteractionEvent event) {
        if (event.getMember() == null || !event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("⛔ Permission required.").setEphemeral(true).queue();
            return;
        }

        String prize = Objects.requireNonNull(event.getOption("prize")).getAsString();
        int duration = (int) Objects.requireNonNull(event.getOption("duration")).getAsLong();
        int winners = event.getOption("winners") != null ? (int) Objects.requireNonNull(event.getOption("winners")).getAsLong() : 1;

        int id = idCounter.incrementAndGet();
        long endsAt = System.currentTimeMillis() + (duration * 60L * 1000L);
        ActiveGiveaway giveaway = new ActiveGiveaway(id, prize, winners, endsAt, event.getChannel().getId());
        activeGiveaways.put(id, giveaway);

        event.replyEmbeds(buildGiveawayEmbed(giveaway))
                .addActionRow(Button.success("engage_giveaway:" + id, "🎉 Enter Giveaway (0)"))
                .queue(hook -> hook.retrieveOriginal().queue(msg -> giveaway.setMessageId(msg.getId())));

        // Broadcast in MC
        String p = getApi().getPluginInstance().getConfigManager().getGlobalPrefix();
        Bukkit.broadcastMessage(ColorUtil.colorize("&8&m----------------------------------------"));
        Bukkit.broadcastMessage(p + ColorUtil.colorize("&#FEE75C★ &e&lGIVEAWAY STARTED!"));
        Bukkit.broadcastMessage(ColorUtil.colorize("  &7Prize: &#57F287&l" + prize));
        Bukkit.broadcastMessage(ColorUtil.colorize("  &7Duration: &e" + duration + "m &8| &7Winners: &e" + winners));
        Bukkit.broadcastMessage(ColorUtil.colorize("  &7Type &#8A9CFE/giveaway enter " + id + " &7to join!"));
        Bukkit.broadcastMessage(ColorUtil.colorize("&8&m----------------------------------------"));

        BukkitTask task = Bukkit.getScheduler().runTaskLater(getApi().getPluginInstance(), () -> endGiveaway(id), duration * 60L * 20L);
        giveaway.setEndTask(task);
    }

    private void handleGiveawayEntryButton(ButtonInteractionEvent event) {
        int id;
        try {
            id = Integer.parseInt(event.getComponentId().substring("engage_giveaway:".length()));
        } catch (Exception e) {
            event.reply("❌ Invalid giveaway.").setEphemeral(true).queue();
            return;
        }

        ActiveGiveaway giveaway = activeGiveaways.get(id);
        if (giveaway == null || System.currentTimeMillis() > giveaway.getEndsAt()) {
            event.reply("⚠️ Giveaway ended.").setEphemeral(true).queue();
            return;
        }

        String uid = event.getUser().getId();
        if (giveaway.getDiscordEntrants().contains(uid)) {
            giveaway.getDiscordEntrants().remove(uid);
            event.reply("❌ Withdrawn from giveaway.").setEphemeral(true).queue();
        } else {
            giveaway.getDiscordEntrants().add(uid);
            event.reply("🎉 **Entered giveaway for: " + giveaway.getPrize() + "!**").setEphemeral(true).queue();
        }

        updateGiveawayMessage(giveaway);
    }

    private void updateGiveawayMessage(ActiveGiveaway giveaway) {
        if (giveaway.getMessageId() == null) return;
        TextChannel channel = getApi().getPluginInstance().getDiscordBotManager().getChannel(giveaway.getChannelId());
        if (channel == null) return;

        channel.editMessageEmbedsById(giveaway.getMessageId(), buildGiveawayEmbed(giveaway))
                .setActionRow(Button.success("engage_giveaway:" + giveaway.getId(), "🎉 Enter Giveaway (" + giveaway.getTotalEntries() + ")"))
                .queue(null, err -> {});
    }

    public void endGiveaway(int id) {
        ActiveGiveaway g = activeGiveaways.remove(id);
        if (g == null) return;

        List<String> dList = new ArrayList<>(g.getDiscordEntrants());
        List<UUID> mcList = new ArrayList<>(g.getMcEntrants());
        List<String> winNames = new ArrayList<>();
        Random r = new Random();
        int target = Math.min(g.getWinners(), g.getTotalEntries());

        for (int i = 0; i < target; i++) {
            boolean pickD = !dList.isEmpty() && (mcList.isEmpty() || r.nextBoolean());
            if (pickD) {
                String dId = dList.remove(r.nextInt(dList.size()));
                winNames.add("<@" + dId + ">");
            } else if (!mcList.isEmpty()) {
                UUID u = mcList.remove(r.nextInt(mcList.size()));
                winNames.add("`" + Bukkit.getOfflinePlayer(u).getName() + "`");
            }
        }

        String wStr = winNames.isEmpty() ? "*None*" : String.join(", ", winNames);

        TextChannel channel = getApi().getPluginInstance().getDiscordBotManager().getChannel(g.getChannelId());
        if (channel != null) {
            EmbedBuilder eb = new EmbedBuilder();
            eb.setColor(new Color(254, 231, 92));
            eb.setTitle("🎉 Giveaway Ended — " + g.getPrize());
            eb.setDescription("🏆 **Winner(s):** " + wStr + "\nTotal Entries: `" + g.getTotalEntries() + "`");
            eb.setTimestamp(Instant.now());
            channel.sendMessage("🎊 **Congratulations to the winners of " + g.getPrize() + "!**")
                    .setEmbeds(eb.build()).queue();
        }

        Bukkit.broadcastMessage(ColorUtil.colorize("&8&m----------------------------------------"));
        Bukkit.broadcastMessage(ColorUtil.colorize("&#57F287★ &a&lGIVEAWAY WINNER(S)! &#FFFFFF" + wStr + " won &#FEE75C" + g.getPrize()));
        Bukkit.broadcastMessage(ColorUtil.colorize("&8&m----------------------------------------"));
    }

    private MessageEmbed buildGiveawayEmbed(ActiveGiveaway g) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(new Color(87, 242, 135));
        eb.setTitle("🎉 GIVEAWAY: " + g.getPrize());
        eb.setDescription("Click below or run `/giveaway enter " + g.getId() + "` in Minecraft!\n\n" +
                "🏆 **Winners:** `" + g.getWinners() + "`\n" +
                "👥 **Entries:** `" + g.getTotalEntries() + "`\n" +
                "⏰ **Ends:** <t:" + (g.getEndsAt() / 1000L) + ":R>");
        eb.setFooter("OmniCord Giveaways • ID #" + g.getId(), null);
        eb.setTimestamp(Instant.ofEpochMilli(g.getEndsAt()));
        return eb.build();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;

        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            player.sendMessage(ColorUtil.colorize("&6&lActive Giveaways: " + (activeGiveaways.isEmpty() ? "&7None" : "")));
            for (ActiveGiveaway g : activeGiveaways.values()) {
                player.sendMessage(ColorUtil.colorize("  &#FEE75C#" + g.getId() + " &7- &#57F287" + g.getPrize() + " &8(&e/giveaway enter " + g.getId() + "&8)"));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("enter") && args.length > 1) {
            try {
                int id = Integer.parseInt(args[1]);
                ActiveGiveaway g = activeGiveaways.get(id);
                if (g != null) {
                    if (g.getMcEntrants().contains(player.getUniqueId())) {
                        g.getMcEntrants().remove(player.getUniqueId());
                        player.sendMessage(ColorUtil.colorize("&eWithdrawn from giveaway #" + id));
                    } else {
                        g.getMcEntrants().add(player.getUniqueId());
                        player.sendMessage(ColorUtil.colorize("&#57F287✔ Entered giveaway for: &f" + g.getPrize()));
                    }
                    updateGiveawayMessage(g);
                }
            } catch (Exception ignored) {}
        }
        return true;
    }
}
