package com.omnicord.addons.community;

import com.omnicord.plugin.api.OmniCordAPI;
import com.omnicord.plugin.api.addon.OmniCordAddon;
import com.omnicord.plugin.utils.ColorUtil;
import com.omnicord.plugin.utils.SkinUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OmniCord Official Addon: Social, Voice & Community Integration Suite.
 * Features:
 * 1. Temporary Discord Voice Channels (Auto-created for parties/clans, auto-deleted when empty).
 * 2. Smart In-Game & Discord FAQ Assistant (Automatic keyword answering for player queries).
 * 3. Live Server Map & GPS Location Cards (/whereis <player> and /map).
 */
public class CommunityAddon extends OmniCordAddon implements Listener {

    private final Set<String> tempVoiceChannelIds = ConcurrentHashMap.newKeySet();
    private final Map<String, String> faqDatabase = new LinkedHashMap<>();

    @Override
    public String getName() { return "Community"; }
    @Override
    public String getVersion() { return "0.0.1-alpha"; }
    @Override
    public String getAuthor() { return "PantherYTac"; }
    @Override
    public String getDescription() { return "Social & Voice: Temporary Voice Channels, FAQ Assistant & Live Map Cards."; }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadFaqDatabase();

        // 1. Register Slash Commands
        registerSlashCommand(
                Commands.slash("voice", "Create a temporary private voice channel for your party or clan")
                        .addOption(OptionType.STRING, "name", "Voice channel name", true),
                this::handleVoiceSlash
        );

        registerSlashCommand(
                Commands.slash("whereis", "Locate a player on the live server map")
                        .addOption(OptionType.STRING, "player", "Minecraft username", true),
                this::handleWhereIsSlash
        );

        registerSlashCommand(
                Commands.slash("map", "Get the link to the live web map (Dynmap/BlueMap/Squaremap)"),
                this::handleMapSlash
        );

        // 2. Register Discord Voice Listener for Auto-Cleanup
        registerDiscordListener(new ListenerAdapter() {
            @Override
            public void onGuildVoiceUpdate(@NotNull GuildVoiceUpdateEvent event) {
                if (event.getChannelLeft() != null) {
                    String leftId = event.getChannelLeft().getId();
                    if (tempVoiceChannelIds.contains(leftId) && event.getChannelLeft().getMembers().isEmpty()) {
                        tempVoiceChannelIds.remove(leftId);
                        event.getChannelLeft().delete().queue(null, err -> {});
                    }
                }
            }
        });

        // 3. Register Bukkit Chat Listener for FAQ Assistant
        registerBukkitListener(this);

        getLogger().info("Community Addon enabled with Temporary Voice, FAQ Desk & Map Cards!");
    }

    @Override
    public void onDisable() {
        // Cleanup remaining empty temp voice channels
        Guild guild = OmniCordAPI.getGuild();
        if (guild != null) {
            for (String chId : tempVoiceChannelIds) {
                VoiceChannel vc = guild.getVoiceChannelById(chId);
                if (vc != null && vc.getMembers().isEmpty()) {
                    vc.delete().queue(null, err -> {});
                }
            }
        }
        tempVoiceChannelIds.clear();
        getLogger().info("Community Addon disabled.");
    }

    private void loadFaqDatabase() {
        faqDatabase.clear();
        faqDatabase.put("claim", "To claim your land, use a Golden Shovel to select two opposite corners! Type /claim for details.");
        faqDatabase.put("land", "You can protect your build with a Golden Shovel! Type /kit claim to get started.");
        faqDatabase.put("ip", "Server Direct IP: " + getApi().getPluginInstance().getConfigManager().getConfig().getString("general.server_ip", "play.example.com"));
        faqDatabase.put("store", "Visit our server webstore: https://store.example.com to support the server!");
        faqDatabase.put("rules", "Server rules: 1. No griefing | 2. No hacked clients/cheating | 3. Be respectful in chat.");
        faqDatabase.put("money", "You can earn money by selling items at /shop, joining jobs via /jobs, or trading in /ah!");
    }

    // --------------------------------------------------------------------------
    // 1. Temporary Voice Channels
    // --------------------------------------------------------------------------

    private void handleVoiceSlash(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        if (member == null) return;

        String name = Objects.requireNonNull(event.getOption("name")).getAsString().trim();
        Guild guild = event.getGuild();
        if (guild == null) return;

        String catId = getConfig().getString("voice_category_id", "0");
        Category category = (!"0".equals(catId) && !catId.isEmpty()) ? guild.getCategoryById(catId) : null;

        var action = (category != null) ? category.createVoiceChannel("🔊 " + name) : guild.createVoiceChannel("🔊 " + name);
        action.queue(vc -> {
            tempVoiceChannelIds.add(vc.getId());
            event.reply("🎉 **Created temporary voice channel:** <#" + vc.getId() + ">\n*This channel will automatically delete when everyone leaves.*")
                    .setEphemeral(true).queue();
        }, err -> event.reply("❌ Could not create voice channel: " + err.getMessage()).setEphemeral(true).queue());
    }

    // --------------------------------------------------------------------------
    // 2. Map & WhereIs
    // --------------------------------------------------------------------------

    private void handleWhereIsSlash(SlashCommandInteractionEvent event) {
        String targetName = Objects.requireNonNull(event.getOption("player")).getAsString();
        Player player = Bukkit.getPlayer(targetName);

        if (player == null || !player.isOnline()) {
            event.reply("❌ Player `" + targetName + "` is not online.").setEphemeral(true).queue();
            return;
        }

        Location loc = player.getLocation();
        String mapBaseUrl = getConfig().getString("web_map_url", "http://map.example.com");
        String mapDeepLink = mapBaseUrl + "/?worldname=" + player.getWorld().getName() + "&x=" + loc.getBlockX() + "&z=" + loc.getBlockZ();

        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(new Color(88, 101, 242));
        eb.setTitle("🗺️ Live Player Location: " + player.getName());
        eb.setThumbnail(SkinUtil.getAvatarUrl("crafthead", player.getUniqueId(), player.getName()));

        eb.addField("🌍 World", "`" + player.getWorld().getName() + "`", true);
        eb.addField("🌲 Biome", "`" + loc.getBlock().getBiome().name() + "`", true);
        eb.addField("📍 Coordinates", "`X: " + loc.getBlockX() + " | Y: " + loc.getBlockY() + " | Z: " + loc.getBlockZ() + "`", false);
        eb.addField("🌐 Live Web Map", "[Click to View on Dynmap/BlueMap](" + mapDeepLink + ")", false);

        eb.setFooter("OmniCord Community GPS Engine", null);
        eb.setTimestamp(Instant.now());

        event.replyEmbeds(eb.build()).queue();
    }

    private void handleMapSlash(SlashCommandInteractionEvent event) {
        String mapUrl = getConfig().getString("web_map_url", "http://map.example.com");
        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(new Color(87, 242, 135));
        eb.setTitle("🗺️ Live Server Web Map");
        eb.setDescription("View all online players, claims, and terrain in real-time:\n🔗 **" + mapUrl + "**");
        eb.setFooter("OmniCord Map Suite", null);
        eb.setTimestamp(Instant.now());

        event.replyEmbeds(eb.build()).queue();
    }

    // --------------------------------------------------------------------------
    // 3. Smart FAQ Assistant
    // --------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!getConfig().getBoolean("faq_assistant.enabled", true)) return;

        String msg = event.getMessage().toLowerCase();
        for (Map.Entry<String, String> entry : faqDatabase.entrySet()) {
            if (msg.contains("how to " + entry.getKey()) || msg.contains("how do i " + entry.getKey()) || msg.equals("!" + entry.getKey())) {
                Player player = event.getPlayer();
                Bukkit.getScheduler().runTaskLater(getApi().getPluginInstance(), () -> {
                    if (player.isOnline()) {
                        String prefix = getApi().getPluginInstance().getConfigManager().getGlobalPrefix();
                        player.sendMessage(prefix + ColorUtil.colorize("&#7289DA[Assistant] &#FFFFFF" + entry.getValue()));
                    }
                }, 20L);
                break;
            }
        }
    }
}
