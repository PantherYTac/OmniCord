package com.omnicord.plugin.integrations;

import com.omnicord.plugin.OmniCord;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.awt.Color;
import java.time.Instant;

/**
 * Ecosystem Hook for Fabulous Claims by PantherYTac.
 * Provides real-time Discord Claim Logging, Intrusion & Raid Alerts, and cross-platform Claim Info.
 */
public class FabulousClaimsHook implements Listener {

    private final OmniCord plugin;
    private boolean available = false;

    public FabulousClaimsHook(OmniCord plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        Plugin fabClaims = Bukkit.getPluginManager().getPlugin("FabulousClaims");
        if (fabClaims == null) {
            fabClaims = Bukkit.getPluginManager().getPlugin("Fabulous-Claims");
        }

        if (fabClaims != null && fabClaims.isEnabled()) {
            this.available = true;
            plugin.getLogger().info("✓ Hooked into Fabulous Claims v" + fabClaims.getDescription().getVersion() + " (by PantherYTac) - Claim Sentry & Discord Raid logger active.");
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    /**
     * Broadcasts claim events (creation, deletion, trust) to Discord.
     */
    public void notifyClaimAction(Player player, String action, String claimName, Location loc) {
        if (!available || player == null) return;

        if (plugin.getDiscordBotManager() != null && plugin.getDiscordBotManager().isConnected()) {
            String channelId = plugin.getConfigManager().getConfig().getString("channels.alerts", "");
            if (channelId.isEmpty() || "0".equals(channelId)) {
                channelId = plugin.getConfigManager().getConfig().getString("channels.chat", "0");
            }
            if (channelId.isEmpty() || "0".equals(channelId)) return;

            TextChannel channel = plugin.getDiscordBotManager().getChannel(channelId);
            if (channel == null || !channel.canTalk()) return;

            EmbedBuilder eb = new EmbedBuilder();
            eb.setColor(new Color(87, 242, 135)); // Green
            eb.setTitle("🏰 Fabulous Claims: " + action);
            eb.setDescription("**Player:** `" + player.getName() + "`\n" +
                    "**Claim:** `" + claimName + "`\n" +
                    "**Location:** `X: " + loc.getBlockX() + " | Y: " + loc.getBlockY() + " | Z: " + loc.getBlockZ() + " (" + loc.getWorld().getName() + ")`");
            eb.setFooter("Fabulous Claims Integration • Author: PantherYTac", null);
            eb.setTimestamp(Instant.now());

            channel.sendMessageEmbeds(eb.build()).queue();
        }
    }

    /**
     * Sentry Intrusion/Raid alert sent when an untrusted player enters a claim.
     */
    public void notifyIntrusion(Player owner, Player intruder, String claimName) {
        if (!available || owner == null || intruder == null) return;

        owner.sendMessage("§c§l[!] §e" + intruder.getName() + " §chas entered your claim: §6" + claimName + "§c!");

        // Send Discord notification to owner if linked
        plugin.getAccountLinkingManager().getLink(owner.getUniqueId()).thenAccept(opt -> {
            opt.ifPresent(link -> {
                if (plugin.getDiscordBotManager() != null && plugin.getDiscordBotManager().isConnected()) {
                    plugin.getDiscordBotManager().getJda().retrieveUserById(link.getDiscordId()).queue(user -> {
                        user.openPrivateChannel().queue(pc -> {
                            EmbedBuilder eb = new EmbedBuilder();
                            eb.setColor(new Color(237, 66, 69));
                            eb.setTitle("⚠️ Claim Intrusion Alert!");
                            eb.setDescription("Player **" + intruder.getName() + "** entered your claim **" + claimName + "** on the server!");
                            eb.setFooter("Fabulous Claims Sentry • Author: PantherYTac", null);
                            eb.setTimestamp(Instant.now());
                            pc.sendMessageEmbeds(eb.build()).queue();
                        }, err -> {});
                    }, err -> {});
                }
            });
        });
    }

    public boolean isAvailable() { return available; }
}
