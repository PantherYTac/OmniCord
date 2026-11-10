package com.omnicord.plugin.api.addon;

import com.omnicord.plugin.api.OmniCordAPI;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * Base abstract class for all OmniCord Addons.
 * Addons can register custom Discord Slash Commands, Button handlers, Bukkit listeners,
 * background tasks, and interact with the OmniCord Core API.
 */
public abstract class OmniCordAddon {

    private OmniCordAPI api;
    private File dataFolder;
    private File configFile;
    private FileConfiguration config;
    private Logger logger;
    private boolean enabled = false;

    /**
     * Internal initialization called by AddonManager.
     */
    public final void init(OmniCordAPI api, File dataFolder, Logger logger) {
        this.api = api;
        this.dataFolder = dataFolder;
        this.logger = logger;
        this.configFile = new File(dataFolder, "config.yml");
    }

    /**
     * Called when the addon is loaded and enabled.
     */
    public abstract void onEnable();

    /**
     * Called when the addon is disabled.
     */
    public abstract void onDisable();

    /**
     * Called when the plugin or addon is reloaded.
     */
    public void onReload() {
        reloadConfig();
    }

    /**
     * Addon Metadata
     */
    public abstract String getName();
    public abstract String getVersion();
    public abstract String getAuthor();
    public abstract String getDescription();

    // --------------------------------------------------------------------------
    // Registration Helpers
    // --------------------------------------------------------------------------

    /**
     * Registers a Discord Slash Command handled by this addon.
     */
    public void registerSlashCommand(SlashCommandData commandData, AddonSlashHandler handler) {
        api.getAddonManager().registerSlashCommand(this, commandData, handler);
    }

    /**
     * Registers a Discord Button handler with a specific button ID prefix.
     */
    public void registerButtonHandler(String buttonIdPrefix, AddonButtonHandler handler) {
        api.getAddonManager().registerButtonHandler(this, buttonIdPrefix, handler);
    }

    /**
     * Registers a Bukkit Event Listener.
     */
    public void registerBukkitListener(Listener listener) {
        api.getAddonManager().registerBukkitListener(this, listener);
    }

    /**
     * Registers a Discord JDA ListenerAdapter.
     */
    public void registerDiscordListener(ListenerAdapter listener) {
        api.getAddonManager().registerDiscordListener(this, listener);
    }

    // --------------------------------------------------------------------------
    // Configuration Management
    // --------------------------------------------------------------------------

    public FileConfiguration getConfig() {
        if (config == null) {
            reloadConfig();
        }
        return config;
    }

    public void reloadConfig() {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        config = YamlConfiguration.loadConfiguration(configFile);

        InputStream defConfigStream = getResource("config.yml");
        if (defConfigStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defConfigStream, StandardCharsets.UTF_8));
            config.setDefaults(defConfig);
        }
    }

    public void saveConfig() {
        if (config == null || configFile == null) return;
        try {
            getConfig().save(configFile);
        } catch (Exception e) {
            logger.severe("Could not save config to " + configFile + ": " + e.getMessage());
        }
    }

    public void saveDefaultConfig() {
        if (!configFile.exists()) {
            saveResource("config.yml", false);
            if (!configFile.exists()) {
                try {
                    if (!dataFolder.exists()) dataFolder.mkdirs();
                    configFile.createNewFile();
                    config = YamlConfiguration.loadConfiguration(configFile);
                    config.set("enabled", true);
                    config.save(configFile);
                } catch (Exception ignored) {}
            }
        }
    }

    public void saveResource(String resourcePath, boolean replace) {
        if (resourcePath == null || resourcePath.isEmpty()) return;
        resourcePath = resourcePath.replace('\\', '/');
        InputStream in = getResource(resourcePath);
        if (in == null) {
            return;
        }

        File outFile = new File(dataFolder, resourcePath);
        int lastIndex = resourcePath.lastIndexOf('/');
        File outDir = new File(dataFolder, resourcePath.substring(0, Math.max(lastIndex, 0)));

        if (!outDir.exists()) {
            outDir.mkdirs();
        }

        try {
            if (!outFile.exists() || replace) {
                java.nio.file.Files.copy(in, outFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ex) {
            logger.severe("Could not save " + outFile.getName() + " to " + outFile + ": " + ex.getMessage());
        }
    }

    public InputStream getResource(String filename) {
        return getClass().getClassLoader().getResourceAsStream(filename);
    }

    // --------------------------------------------------------------------------
    // Getters
    // --------------------------------------------------------------------------

    public OmniCordAPI getApi() {
        return api;
    }

    public com.omnicord.plugin.OmniCord getPlugin() {
        return api != null ? api.getPlugin() : com.omnicord.plugin.OmniCord.getInstance();
    }

    public File getDataFolder() {
        return dataFolder;
    }

    public Logger getLogger() {
        return logger;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    // --------------------------------------------------------------------------
    // Functional Interfaces for Addon Handlers
    // --------------------------------------------------------------------------

    @FunctionalInterface
    public interface AddonSlashHandler {
        void handle(SlashCommandInteractionEvent event);
    }

    @FunctionalInterface
    public interface AddonButtonHandler {
        void handle(ButtonInteractionEvent event);
    }
}
