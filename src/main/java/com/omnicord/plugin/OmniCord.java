package com.omnicord.plugin;

import com.omnicord.plugin.addon.AddonManager;
import com.omnicord.plugin.api.OmniCordAPI;
import com.omnicord.plugin.config.ConfigManager;
import com.omnicord.plugin.customcmd.CustomCommandManager;
import com.omnicord.plugin.dashboard.LiveDashboardManager;
import com.omnicord.plugin.discord.DiscordBotManager;
import com.omnicord.plugin.integrations.LuckPermsHook;
import com.omnicord.plugin.integrations.PlaceholderHook;
import com.omnicord.plugin.integrations.VaultHook;
import com.omnicord.plugin.linking.AccountLinkingManager;
import com.omnicord.plugin.linking.RoleSyncManager;
import com.omnicord.plugin.minecraft.command.*;
import com.omnicord.plugin.minecraft.console.ConsoleAppender;
import com.omnicord.plugin.minecraft.listener.PlayerActivityListener;
import com.omnicord.plugin.minecraft.listener.PlayerChatListener;
import com.omnicord.plugin.minecraft.listener.ServerLifecycleListener;
import com.omnicord.plugin.minecraft.listener.TwoFactorListener;
import com.omnicord.plugin.minigames.TriviaManager;
import com.omnicord.plugin.security.TwoFactorAuthManager;
import com.omnicord.plugin.storage.DatabaseManager;
import com.omnicord.plugin.tickets.TicketManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;

/**
 * OmniCord — The Next-Generation Minecraft to Discord Bridge & Suite.
 */
public class OmniCord extends JavaPlugin {

    protected static OmniCord instance;

    protected ConfigManager configManager;
    protected DatabaseManager databaseManager;
    protected DiscordBotManager discordBotManager;
    protected AccountLinkingManager accountLinkingManager;
    protected RoleSyncManager roleSyncManager;
    protected ConsoleAppender consoleAppender;
    protected ServerLifecycleListener lifecycleListener;

    // Advanced Subsystems
    protected TwoFactorAuthManager twoFactorAuthManager;
    protected LiveDashboardManager liveDashboardManager;
    protected TicketManager ticketManager;
    protected TriviaManager triviaManager;
    protected CustomCommandManager customCommandManager;
    protected AddonManager addonManager;

    protected VaultHook vaultHook;
    protected LuckPermsHook luckPermsHook;
    protected PlaceholderHook placeholderHook;
    protected com.omnicord.plugin.integrations.IntegrationManager integrationManager;

    protected BukkitTask recurringSyncTask;
    protected long loadStartTime;

    @Override
    public void onLoad() {
        instance = this;
        this.loadStartTime = System.currentTimeMillis();
    }

    @Override
    public void onEnable() {
        OmniCordAPI.setPlugin(this);

        // 1. Load Configurations
        this.configManager = new ConfigManager(this);
        this.configManager.loadConfigurations();

        // 2. Initialize Hooks
        this.vaultHook = new VaultHook(this);
        this.vaultHook.initialize();

        this.luckPermsHook = new LuckPermsHook(this);
        this.luckPermsHook.initialize();

        this.placeholderHook = new PlaceholderHook(this);
        this.placeholderHook.initialize();

        this.integrationManager = new com.omnicord.plugin.integrations.IntegrationManager(this);
        this.integrationManager.initialize();

        // 3. Initialize Storage
        this.databaseManager = new DatabaseManager(this);
        this.databaseManager.initialize();

        // 4. Initialize Core & Advanced Managers
        this.accountLinkingManager = new AccountLinkingManager(this);
        this.roleSyncManager = new RoleSyncManager(this);
        this.customCommandManager = new CustomCommandManager(this);
        this.discordBotManager = new DiscordBotManager(this);
        this.lifecycleListener = new ServerLifecycleListener(this);
        this.twoFactorAuthManager = new TwoFactorAuthManager(this);
        this.liveDashboardManager = new LiveDashboardManager(this);
        this.ticketManager = new TicketManager(this);
        this.triviaManager = new TriviaManager(this);
        this.addonManager = new AddonManager(this);

        // 5. Load and enable Addons
        this.addonManager.loadAddons();

        // Register built-in official addon suites if not loaded via external JAR and enabled in config
        registerBuiltInAddons();

        this.addonManager.enableAddons();

        // 6. Connect Discord Bot Asynchronously
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            discordBotManager.startBot();

            // FIX Bug #20: Only start dashboard/trivia if bot successfully connected.
            // Previously these started even if startBot() failed, causing them to fire every tick with no effect.
            if (discordBotManager.isConnected()) {
                liveDashboardManager.startDashboard();
                triviaManager.start();
            }

            // Broadcast server startup after bot is connected
            long bootTime = System.currentTimeMillis() - loadStartTime;
            Bukkit.getScheduler().runTaskLater(this, () -> lifecycleListener.broadcastServerStart(bootTime), 40L);
        });

        // 7. Attach Console Bridge Appender
        this.consoleAppender = new ConsoleAppender(this);
        this.consoleAppender.attach();

        // 8. Register Minecraft Listeners
        Bukkit.getPluginManager().registerEvents(new PlayerChatListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerActivityListener(this), this);
        Bukkit.getPluginManager().registerEvents(new TwoFactorListener(this), this);

        // 9. Register Commands
        Objects.requireNonNull(getCommand("omnicord")).setExecutor(new OmniCordCommand(this));
        Objects.requireNonNull(getCommand("discord")).setExecutor(new DiscordCommand(this));
        Objects.requireNonNull(getCommand("staffchat")).setExecutor(new StaffChatCommand(this));
        Objects.requireNonNull(getCommand("report")).setExecutor(new ReportCommand(this));
        Objects.requireNonNull(getCommand("ticket")).setExecutor(new TicketCommand(this));

        // 10. Schedule Recurring Role Sync Task
        startRecurringSync();

        getLogger().info("=========================================================");
        getLogger().info(" OmniCord v" + getDescription().getVersion() + " has been successfully enabled!");
        getLogger().info(" Cross-version support active for 1.16 - 1.21+");
        getLogger().info(" Active Modules: 2FA, Dashboard, ModMail Tickets, Trivia, Addon Engine");
        getLogger().info("=========================================================");
    }

    @Override
    public void onDisable() {
        if (lifecycleListener != null) {
            lifecycleListener.broadcastServerStop();
        }

        if (addonManager != null) {
            addonManager.disableAddons();
        }

        if (liveDashboardManager != null) {
            liveDashboardManager.stopDashboard();
        }

        if (triviaManager != null) {
            triviaManager.stop();
        }

        if (recurringSyncTask != null) {
            recurringSyncTask.cancel();
        }

        if (consoleAppender != null) {
            consoleAppender.detach();
        }

        if (discordBotManager != null) {
            discordBotManager.stopBot();
        }

        if (databaseManager != null) {
            databaseManager.shutdown();
        }

        getLogger().info("OmniCord has been safely disabled.");
    }

    protected void registerBuiltInAddons() {
        if (getConfigManager().getConfig().getBoolean("modules.admin_addon", true) && this.addonManager.getAddon("Admin") == null) {
            this.addonManager.registerAddon(new com.omnicord.addons.admin.AdminAddon());
        }
        if (getConfigManager().getConfig().getBoolean("modules.engagement_addon", true) && this.addonManager.getAddon("Engagement") == null) {
            this.addonManager.registerAddon(new com.omnicord.addons.engagement.EngagementAddon());
        }
        if (getConfigManager().getConfig().getBoolean("modules.community_addon", true) && this.addonManager.getAddon("Community") == null) {
            this.addonManager.registerAddon(new com.omnicord.addons.community.CommunityAddon());
        }
    }

    public void reloadPlugin() {
        configManager.reloadConfigurations();

        if (addonManager != null) {
            addonManager.reloadAddons();
        }

        if (liveDashboardManager != null) {
            liveDashboardManager.stopDashboard();
        }
        if (triviaManager != null) {
            triviaManager.stop();
        }

        if (discordBotManager != null) {
            discordBotManager.stopBot();
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                discordBotManager.startBot();
                if (liveDashboardManager != null) liveDashboardManager.startDashboard();
                if (triviaManager != null) triviaManager.start();
            });
        }

        startRecurringSync();
        getLogger().info("OmniCord configuration and Discord modules reloaded.");
    }

    private void startRecurringSync() {
        if (recurringSyncTask != null) {
            recurringSyncTask.cancel();
        }

        int intervalMinutes = configManager.getRoleSync().getInt("sync.recurring_sync_interval_minutes", 30);
        if (intervalMinutes > 0) {
            long ticks = intervalMinutes * 60L * 20L;
            this.recurringSyncTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
                if (roleSyncManager != null) {
                    roleSyncManager.syncAllOnline();
                }
            }, ticks, ticks);
        }
    }

    public static OmniCord getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public DiscordBotManager getDiscordBotManager() {
        return discordBotManager;
    }

    public AccountLinkingManager getAccountLinkingManager() {
        return accountLinkingManager;
    }

    public RoleSyncManager getRoleSyncManager() {
        return roleSyncManager;
    }

    public TwoFactorAuthManager getTwoFactorAuthManager() {
        return twoFactorAuthManager;
    }

    public LiveDashboardManager getLiveDashboardManager() {
        return liveDashboardManager;
    }

    public TicketManager getTicketManager() {
        return ticketManager;
    }

    public TriviaManager getTriviaManager() {
        return triviaManager;
    }

    public CustomCommandManager getCustomCommandManager() {
        return customCommandManager;
    }

    public AddonManager getAddonManager() {
        return addonManager;
    }

    public VaultHook getVaultHook() {
        return vaultHook;
    }

    public LuckPermsHook getLuckPermsHook() {
        return luckPermsHook;
    }

    public PlaceholderHook getPlaceholderHook() {
        return placeholderHook;
    }

    public com.omnicord.plugin.integrations.IntegrationManager getIntegrationManager() {
        return integrationManager;
    }

    @Override
    public FileConfiguration getConfig() {
        return configManager != null ? configManager.getConfig() : super.getConfig();
    }

    @Override
    public void saveConfig() {
        if (configManager != null) {
            configManager.saveConfig();
        } else {
            super.saveConfig();
        }
    }

    @Override
    public void reloadConfig() {
        if (configManager != null) {
            configManager.reloadConfigurations();
        } else {
            super.reloadConfig();
        }
    }
}
