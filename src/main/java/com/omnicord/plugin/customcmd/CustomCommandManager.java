package com.omnicord.plugin.customcmd;

import com.omnicord.plugin.OmniCord;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads and dynamically responds to user-defined Discord Slash Commands from custom_commands.yml.
 */
public class CustomCommandManager {

    private final OmniCord plugin;

    public CustomCommandManager(OmniCord plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfigManager().getConfig().getBoolean("modules.custom_commands", true) &&
               plugin.getConfigManager().getCustomCommands().getBoolean("enabled", true);
    }

    public List<SlashCommandData> getCustomSlashCommands() {
        List<SlashCommandData> list = new ArrayList<>();
        if (!isEnabled()) return list;

        ConfigurationSection section = plugin.getConfigManager().getCustomCommands().getConfigurationSection("commands");
        if (section == null) return list;

        for (String cmdName : section.getKeys(false)) {
            String desc = section.getString(cmdName + ".description", "Custom OmniCord command");
            list.add(Commands.slash(cmdName.toLowerCase(), desc));
        }

        return list;
    }

    public boolean handleCustomCommand(SlashCommandInteractionEvent event) {
        if (!isEnabled()) return false;

        String cmdName = event.getName().toLowerCase();
        ConfigurationSection section = plugin.getConfigManager().getCustomCommands().getConfigurationSection("commands." + cmdName);
        if (section == null) return false;

        ConfigurationSection embedSec = section.getConfigurationSection("embed");
        if (embedSec == null) {
            event.reply("Command executed.").queue();
            return true;
        }

        String serverName = plugin.getConfigManager().getConfig().getString("general.server_name", "Minecraft Server");
        String serverIp = plugin.getConfigManager().getConfig().getString("general.server_ip", "play.example.com");

        EmbedBuilder eb = new EmbedBuilder();
        String colorHex = embedSec.getString("color", "#5865F2");
        try {
            eb.setColor(Color.decode(colorHex));
        } catch (Exception e) {
            eb.setColor(new Color(88, 101, 242));
        }

        String title = embedSec.getString("title");
        if (title != null) eb.setTitle(title.replace("%server_name%", serverName).replace("%server_ip%", serverIp));

        String desc = embedSec.getString("description");
        if (desc != null) eb.setDescription(desc.replace("%server_name%", serverName).replace("%server_ip%", serverIp));

        String footer = embedSec.getString("footer");
        if (footer != null) eb.setFooter(footer.replace("%server_name%", serverName).replace("%server_ip%", serverIp));

        eb.setTimestamp(Instant.now());

        event.replyEmbeds(eb.build()).queue();
        return true;
    }
}
