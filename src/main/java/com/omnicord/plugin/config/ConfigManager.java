package com.omnicord.plugin.config;

import com.omnicord.plugin.OmniCord;
import com.omnicord.plugin.utils.ColorUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles loading, saving, and querying of all OmniCord configuration files.
 * Designed to make prefix and message customizations ultra-easy and intuitive.
 */
public class ConfigManager {

    private final OmniCord plugin;

    private FileConfiguration config;
    private FileConfiguration messages;
    private FileConfiguration embeds;
    private FileConfiguration roleSync;
    private FileConfiguration twoFactor;
    private FileConfiguration dashboard;
    private FileConfiguration tickets;
    private FileConfiguration trivia;
    private FileConfiguration customCommands;

    private File configFile;
    private File messagesFile;
    private File embedsFile;
    private File roleSyncFile;
    private File twoFactorFile;
    private File dashboardFile;
    private File ticketsFile;
    private File triviaFile;
    private File customCommandsFile;

    private String globalPrefix;

    public ConfigManager(OmniCord plugin) {
        this.plugin = plugin;
    }

    public void loadConfigurations() {
        configFile = new File(plugin.getDataFolder(), "config.yml");
        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        embedsFile = new File(plugin.getDataFolder(), "embeds.yml");
        roleSyncFile = new File(plugin.getDataFolder(), "rolesync.yml");
        twoFactorFile = new File(plugin.getDataFolder(), "twofactor.yml");
        dashboardFile = new File(plugin.getDataFolder(), "dashboard.yml");
        ticketsFile = new File(plugin.getDataFolder(), "tickets.yml");
        triviaFile = new File(plugin.getDataFolder(), "trivia.yml");
        customCommandsFile = new File(plugin.getDataFolder(), "custom_commands.yml");

        if (!configFile.exists()) plugin.saveResource("config.yml", false);
        if (!messagesFile.exists()) plugin.saveResource("messages.yml", false);
        if (!embedsFile.exists()) plugin.saveResource("embeds.yml", false);
        if (!roleSyncFile.exists()) plugin.saveResource("rolesync.yml", false);
        if (!twoFactorFile.exists()) plugin.saveResource("twofactor.yml", false);
        if (!dashboardFile.exists()) plugin.saveResource("dashboard.yml", false);
        if (!ticketsFile.exists()) plugin.saveResource("tickets.yml", false);
        if (!triviaFile.exists()) plugin.saveResource("trivia.yml", false);
        if (!customCommandsFile.exists()) plugin.saveResource("custom_commands.yml", false);

        config = YamlConfiguration.loadConfiguration(configFile);
        messages = YamlConfiguration.loadConfiguration(messagesFile);
        embeds = YamlConfiguration.loadConfiguration(embedsFile);
        roleSync = YamlConfiguration.loadConfiguration(roleSyncFile);
        twoFactor = YamlConfiguration.loadConfiguration(twoFactorFile);
        dashboard = YamlConfiguration.loadConfiguration(dashboardFile);
        tickets = YamlConfiguration.loadConfiguration(ticketsFile);
        trivia = YamlConfiguration.loadConfiguration(triviaFile);
        customCommands = YamlConfiguration.loadConfiguration(customCommandsFile);

        // Cache prefix
        this.globalPrefix = ColorUtil.colorize(config.getString("prefix", "&#5865F2&lOmniCord &#7289DA»&r "));
    }

    public void reloadConfigurations() {
        loadConfigurations();
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public FileConfiguration getMessages() {
        return messages;
    }

    public FileConfiguration getEmbeds() {
        return embeds;
    }

    public FileConfiguration getRoleSync() {
        return roleSync;
    }

    public FileConfiguration getTwoFactor() {
        return twoFactor;
    }

    public FileConfiguration getDashboard() {
        return dashboard;
    }

    public FileConfiguration getTickets() {
        return tickets;
    }

    public FileConfiguration getTrivia() {
        return trivia;
    }

    public FileConfiguration getCustomCommands() {
        return customCommands;
    }

    public String getGlobalPrefix() {
        return globalPrefix;
    }

    public String getMessage(String path, String def) {
        String msg = messages.getString(path, def);
        if (msg == null) return "";
        msg = msg.replace("%prefix%", globalPrefix);
        return ColorUtil.colorize(msg);
    }

    public String getMessage(String path) {
        return getMessage(path, "");
    }

    public List<String> getMessageList(String path) {
        List<String> list = messages.getStringList(path);
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream()
                .map(line -> ColorUtil.colorize(line.replace("%prefix%", globalPrefix)))
                .collect(Collectors.toList());
    }

    public boolean isDebug() {
        return config.getBoolean("general.debug", false);
    }

    public void saveConfig() {
        if (config != null && configFile != null) {
            try {
                config.save(configFile);
            } catch (Exception e) {
                plugin.getLogger().warning("Could not save config.yml: " + e.getMessage());
            }
        }
    }

    public void saveRoleSync() {
        if (roleSync != null && roleSyncFile != null) {
            try {
                roleSync.save(roleSyncFile);
            } catch (Exception e) {
                plugin.getLogger().warning("Could not save rolesync.yml: " + e.getMessage());
            }
        }
    }
}
