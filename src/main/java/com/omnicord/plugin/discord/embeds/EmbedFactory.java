package com.omnicord.plugin.discord.embeds;

import com.omnicord.plugin.OmniCord;
import com.omnicord.plugin.utils.ServerUtil;
import com.omnicord.plugin.utils.SkinUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;

import java.awt.Color;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Constructs Discord Embeds based on customizable settings from embeds.yml.
 */
public class EmbedFactory {

    private final OmniCord plugin;

    public EmbedFactory(OmniCord plugin) {
        this.plugin = plugin;
    }

    /**
     * Builds an embed from a specific key in embeds.yml with given variable replacements.
     */
    public MessageEmbed createEmbed(String key, Map<String, String> placeholders, UUID playerUuid, String playerName) {
        ConfigurationSection section = plugin.getConfigManager().getEmbeds().getConfigurationSection(key);
        if (section == null || !section.getBoolean("enabled", true)) {
            return null;
        }

        EmbedBuilder builder = new EmbedBuilder();

        // 1. Color
        String colorHex = section.getString("color", "#5865F2");
        try {
            builder.setColor(Color.decode(colorHex));
        } catch (Exception e) {
            builder.setColor(new Color(88, 101, 242));
        }

        // Avatar URL resolution
        String avatarService = plugin.getConfigManager().getConfig().getString("chat_bridge.avatar_service", "crafthead");
        String avatarUrl = SkinUtil.getAvatarUrl(avatarService, playerUuid, playerName);
        placeholders.putIfAbsent("%avatar_url%", avatarUrl);
        placeholders.putIfAbsent("%server_name%", plugin.getConfigManager().getConfig().getString("general.server_name", "Minecraft Server"));
        placeholders.putIfAbsent("%server_ip%", plugin.getConfigManager().getConfig().getString("general.server_ip", "play.example.com"));
        placeholders.putIfAbsent("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()));
        placeholders.putIfAbsent("%max%", String.valueOf(Bukkit.getMaxPlayers()));
        placeholders.putIfAbsent("%tps%", ServerUtil.getFormattedTPS());
        placeholders.putIfAbsent("%uptime%", ServerUtil.getUptime());
        placeholders.putIfAbsent("%time%", java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss").format(java.time.LocalTime.now()));

        // 2. Title
        String title = section.getString("title");
        if (title != null && !title.isEmpty()) {
            builder.setTitle(applyPlaceholders(title, placeholders));
        }

        // 3. Description
        String description = section.getString("description");
        if (description != null && !description.isEmpty()) {
            builder.setDescription(applyPlaceholders(description, placeholders));
        }

        // 4. Author
        if (section.isConfigurationSection("author")) {
            String authorName = applyPlaceholders(section.getString("author.name", ""), placeholders);
            String authorIcon = applyPlaceholders(section.getString("author.icon_url", ""), placeholders);
            if (!authorName.isEmpty()) {
                builder.setAuthor(authorName, null, authorIcon.isEmpty() ? null : authorIcon);
            }
        }

        // 5. Thumbnail
        String thumbnail = section.getString("thumbnail");
        if (thumbnail != null && !thumbnail.isEmpty()) {
            builder.setThumbnail(applyPlaceholders(thumbnail, placeholders));
        }

        // 6. Fields
        if (section.isList("fields")) {
            List<Map<?, ?>> fields = section.getMapList("fields");
            for (Map<?, ?> fieldMap : fields) {
                Object nameObj = fieldMap.get("name");
                Object valObj = fieldMap.get("value");
                Object inlineObj = fieldMap.get("inline");

                String name = applyPlaceholders(nameObj != null ? nameObj.toString() : "", placeholders);
                String value = applyPlaceholders(valObj != null ? valObj.toString() : "", placeholders);
                boolean inline = inlineObj != null && Boolean.parseBoolean(inlineObj.toString());

                if (!name.isEmpty() && !value.isEmpty()) {
                    builder.addField(name, value, inline);
                }
            }
        }

        // 7. Footer
        if (section.isConfigurationSection("footer")) {
            String footerText = applyPlaceholders(section.getString("footer.text", ""), placeholders);
            String footerIcon = applyPlaceholders(section.getString("footer.icon_url", ""), placeholders);
            if (!footerText.isEmpty()) {
                builder.setFooter(footerText, footerIcon.isEmpty() ? null : footerIcon);
            }
        }

        // 8. Timestamp
        if (section.getBoolean("timestamp", true)) {
            builder.setTimestamp(Instant.now());
        }

        return builder.build();
    }

    private String applyPlaceholders(String text, Map<String, String> map) {
        if (text == null) return "";
        for (Map.Entry<String, String> entry : map.entrySet()) {
            text = text.replace(entry.getKey(), entry.getValue() != null ? entry.getValue() : "");
        }
        return text;
    }
}
