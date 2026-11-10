package com.omnicord.plugin.addon;

import com.omnicord.plugin.OmniCord;
import com.omnicord.plugin.api.OmniCordAPI;
import com.omnicord.plugin.api.addon.OmniCordAddon;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;

/**
 * Manages discovery, lifecycle, loading, and event dispatching for OmniCord Addons.
 */
public class AddonManager {

    private final OmniCord plugin;
    private final File addonsFolder;

    private final Map<String, OmniCordAddon> loadedAddons = new LinkedHashMap<>();
    private final Map<String, OmniCordAddon.AddonSlashHandler> addonSlashHandlers = new ConcurrentHashMap<>();
    private final Map<String, SlashCommandData> addonSlashCommands = new ConcurrentHashMap<>();
    private final Map<String, OmniCordAddon.AddonButtonHandler> addonButtonHandlers = new ConcurrentHashMap<>();
    private final Map<OmniCordAddon, List<Listener>> addonBukkitListeners = new ConcurrentHashMap<>();
    private final Map<OmniCordAddon, List<ListenerAdapter>> addonDiscordListeners = new ConcurrentHashMap<>();
    private final List<URLClassLoader> classLoaders = new ArrayList<>();

    public AddonManager(OmniCord plugin) {
        this.plugin = plugin;
        this.addonsFolder = new File(plugin.getDataFolder(), "addons");
    }

    /**
     * Scans the addons/ directory and loads all .jar addons + built-in addons.
     */
    public void loadAddons() {
        if (!addonsFolder.exists()) {
            addonsFolder.mkdirs();
        }

        plugin.getLogger().info("Scanning for OmniCord addons in: " + addonsFolder.getAbsolutePath());

        // 1. Load standalone JAR files from plugins/OmniCord/addons/
        File[] files = addonsFolder.listFiles((dir, name) -> name.endsWith(".jar"));
        if (files != null) {
            for (File jarFile : files) {
                try {
                    loadAddonFromJar(jarFile);
                } catch (Exception e) {
                    plugin.getLogger().severe("Failed to load addon from " + jarFile.getName() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        plugin.getLogger().info("Loaded " + loadedAddons.size() + " OmniCord addon(s).");
    }

    /**
     * Enables all loaded addons.
     */
    public void enableAddons() {
        for (OmniCordAddon addon : loadedAddons.values()) {
            if (!addon.isEnabled()) {
                // Check if addon is disabled in master config or its own config
                boolean enabledInMaster = plugin.getConfigManager().getConfig().getBoolean("modules." + addon.getName().toLowerCase() + "_addon", true);
                boolean enabledInConfig = addon.getConfig().getBoolean("enabled", true);
                if (!enabledInMaster || !enabledInConfig) {
                    plugin.getLogger().info("Addon " + addon.getName() + " is disabled in configuration.");
                    continue;
                }

                try {
                    addon.onEnable();
                    addon.setEnabled(true);
                    plugin.getLogger().info("Enabled addon: " + addon.getName() + " v" + addon.getVersion() + " by " + addon.getAuthor());
                } catch (Throwable t) {
                    plugin.getLogger().severe("Error enabling addon " + addon.getName() + ": " + t.getMessage());
                    t.printStackTrace();
                }
            }
        }
    }

    /**
     * Disables all loaded addons.
     */
    public void disableAddons() {
        for (OmniCordAddon addon : new ArrayList<>(loadedAddons.values())) {
            disableAddon(addon);
        }
        loadedAddons.clear();
        addonSlashHandlers.clear();
        addonSlashCommands.clear();
        addonButtonHandlers.clear();

        for (URLClassLoader cl : classLoaders) {
            try {
                cl.close();
            } catch (Exception ignored) {}
        }
        classLoaders.clear();
    }

    public void disableAddon(OmniCordAddon addon) {
        if (addon.isEnabled()) {
            try {
                addon.onDisable();
            } catch (Throwable t) {
                plugin.getLogger().severe("Error disabling addon " + addon.getName() + ": " + t.getMessage());
            }
            addon.setEnabled(false);

            // Unregister Bukkit listeners
            List<Listener> bukkitListeners = addonBukkitListeners.remove(addon);
            if (bukkitListeners != null) {
                for (Listener l : bukkitListeners) {
                    HandlerList.unregisterAll(l);
                }
            }

            // Unregister Discord listeners
            List<ListenerAdapter> discordListeners = addonDiscordListeners.remove(addon);
            if (discordListeners != null && plugin.getDiscordBotManager().getJda() != null) {
                for (ListenerAdapter adapter : discordListeners) {
                    plugin.getDiscordBotManager().getJda().removeEventListener(adapter);
                }
            }
        }
    }

    public void reloadAddons() {
        for (OmniCordAddon addon : loadedAddons.values()) {
            try {
                addon.onReload();
            } catch (Throwable t) {
                plugin.getLogger().severe("Error reloading addon " + addon.getName() + ": " + t.getMessage());
            }
        }
    }

    /**
     * Programmatically registers an addon (e.g. from an external plugin or built-in).
     */
    public void registerAddon(OmniCordAddon addon) {
        if (loadedAddons.containsKey(addon.getName().toLowerCase())) {
            plugin.getLogger().warning("Addon with name '" + addon.getName() + "' is already registered.");
            return;
        }

        File addonFolder = new File(addonsFolder, addon.getName());
        Logger addonLogger = Logger.getLogger("OmniCord-" + addon.getName());
        addon.init(OmniCordAPI.get(), addonFolder, addonLogger);

        loadedAddons.put(addon.getName().toLowerCase(), addon);
        try {
            addon.onEnable();
            addon.setEnabled(true);
            plugin.getLogger().info("Registered & enabled addon: " + addon.getName() + " v" + addon.getVersion());
        } catch (Throwable t) {
            plugin.getLogger().severe("Error enabling addon " + addon.getName() + ": " + t.getMessage());
            t.printStackTrace();
        }
    }

    /**
     * Loads an addon from a JAR file by reading its addon.yml manifest.
     */
    private void loadAddonFromJar(File jarFile) throws Exception {
        try (JarFile jar = new JarFile(jarFile)) {
            JarEntry entry = jar.getJarEntry("addon.yml");
            if (entry == null) {
                plugin.getLogger().warning("Jar " + jarFile.getName() + " does not contain addon.yml! Skipping.");
                return;
            }

            YamlConfiguration yaml;
            try (InputStream is = jar.getInputStream(entry)) {
                yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(is, StandardCharsets.UTF_8));
            }

            String mainClass = yaml.getString("main");
            String name = yaml.getString("name", jarFile.getName().replace(".jar", ""));

            if (mainClass == null) {
                plugin.getLogger().warning("addon.yml in " + jarFile.getName() + " does not specify 'main' class!");
                return;
            }

            URLClassLoader classLoader = new URLClassLoader(new URL[]{jarFile.toURI().toURL()}, plugin.getClass().getClassLoader());
            classLoaders.add(classLoader);

            Class<?> clazz = Class.forName(mainClass, true, classLoader);
            if (!OmniCordAddon.class.isAssignableFrom(clazz)) {
                plugin.getLogger().warning("Main class " + mainClass + " in " + jarFile.getName() + " does not extend OmniCordAddon!");
                return;
            }

            OmniCordAddon addon = (OmniCordAddon) clazz.getDeclaredConstructor().newInstance();
            File addonFolder = new File(addonsFolder, name);
            Logger addonLogger = Logger.getLogger("OmniCord-" + name);
            addon.init(OmniCordAPI.get(), addonFolder, addonLogger);

            loadedAddons.put(name.toLowerCase(), addon);
        }
    }

    // --------------------------------------------------------------------------
    // Addon Registration Handlers
    // --------------------------------------------------------------------------

    public void registerSlashCommand(OmniCordAddon addon, SlashCommandData commandData, OmniCordAddon.AddonSlashHandler handler) {
        String name = commandData.getName().toLowerCase();
        addonSlashCommands.put(name, commandData);
        addonSlashHandlers.put(name, handler);

        // Register to live JDA if connected
        JDA jda = plugin.getDiscordBotManager().getJda();
        if (jda != null) {
            String guildId = plugin.getConfigManager().getConfig().getString("bot.guild_id", "");
            if (guildId != null && !guildId.isEmpty() && !"000000000000000000".equals(guildId)) {
                var guild = jda.getGuildById(guildId);
                if (guild != null) {
                    guild.upsertCommand(commandData).queue();
                }
            } else {
                jda.upsertCommand(commandData).queue();
            }
        }
    }

    public void registerButtonHandler(OmniCordAddon addon, String buttonPrefix, OmniCordAddon.AddonButtonHandler handler) {
        addonButtonHandlers.put(buttonPrefix.toLowerCase(), handler);
    }

    public void registerBukkitListener(OmniCordAddon addon, Listener listener) {
        Bukkit.getPluginManager().registerEvents(listener, plugin);
        addonBukkitListeners.computeIfAbsent(addon, k -> new ArrayList<>()).add(listener);
    }

    public void registerDiscordListener(OmniCordAddon addon, ListenerAdapter listener) {
        if (plugin.getDiscordBotManager().getJda() != null) {
            plugin.getDiscordBotManager().getJda().addEventListener(listener);
        }
        addonDiscordListeners.computeIfAbsent(addon, k -> new ArrayList<>()).add(listener);
    }

    // --------------------------------------------------------------------------
    // Event Routing from Core
    // --------------------------------------------------------------------------

    public boolean handleSlashCommand(SlashCommandInteractionEvent event) {
        String name = event.getName().toLowerCase();
        OmniCordAddon.AddonSlashHandler handler = addonSlashHandlers.get(name);
        if (handler != null) {
            try {
                handler.handle(event);
            } catch (Throwable t) {
                plugin.getLogger().severe("Error handling addon slash command '/" + name + "': " + t.getMessage());
                t.printStackTrace();
                event.getHook().sendMessage("❌ An internal error occurred while executing this addon command.").setEphemeral(true).queue();
            }
            return true;
        }
        return false;
    }

    public boolean handleButtonInteraction(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId().toLowerCase();
        for (Map.Entry<String, OmniCordAddon.AddonButtonHandler> entry : addonButtonHandlers.entrySet()) {
            if (buttonId.startsWith(entry.getKey())) {
                try {
                    entry.getValue().handle(event);
                } catch (Throwable t) {
                    plugin.getLogger().severe("Error handling addon button '" + buttonId + "': " + t.getMessage());
                    t.printStackTrace();
                    event.getHook().sendMessage("❌ An internal error occurred handling this button.").setEphemeral(true).queue();
                }
                return true;
            }
        }
        return false;
    }

    public Collection<SlashCommandData> getAddonSlashCommands() {
        return addonSlashCommands.values();
    }

    public Map<String, OmniCordAddon> getLoadedAddons() {
        return Collections.unmodifiableMap(loadedAddons);
    }

    public OmniCordAddon getAddon(String name) {
        return loadedAddons.get(name.toLowerCase());
    }
}
