package me.deadlight.ezchestshop;

import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import me.deadlight.ezchestshop.commands.CommandCheckProfits;
import me.deadlight.ezchestshop.commands.EcsAdmin;
import me.deadlight.ezchestshop.commands.MainCommands;
import me.deadlight.ezchestshop.data.Config;
import me.deadlight.ezchestshop.data.DatabaseManager;
import me.deadlight.ezchestshop.data.LanguageManager;
import me.deadlight.ezchestshop.data.ShopContainer;
import me.deadlight.ezchestshop.data.gui.GuiData;
import me.deadlight.ezchestshop.integrations.AdvancedRegionMarket;
import me.deadlight.ezchestshop.integrations.PebbleEconomyBridge;
import me.deadlight.ezchestshop.integrations.PebbleVaultEconomy;
import me.deadlight.ezchestshop.integrations.ShopEconomy;
import me.deadlight.ezchestshop.listeners.*;
import me.deadlight.ezchestshop.tasks.LoadedChunksTask;
import me.deadlight.ezchestshop.utils.ASHologram;
import me.deadlight.ezchestshop.utils.BlockOutline;
import me.deadlight.ezchestshop.utils.CommandRegister;
import me.deadlight.ezchestshop.utils.FloatingItem;
import me.deadlight.ezchestshop.utils.Utils;
import me.deadlight.ezchestshop.utils.exceptions.CommandFetchException;
import me.deadlight.ezchestshop.utils.signs.SignShopDisplay;
import me.deadlight.ezchestshop.utils.worldguard.FlagRegistry;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.Locale;

public final class EzChestShop extends JavaPlugin {

    private static EzChestShop plugin;
    private static ShopEconomy econ = null;
    private static boolean usingPebbleEconomy = false;

    public static boolean economyPluginFound = true;

    public static boolean slimefun = false;
    public static boolean towny = false;
    public static boolean worldguard = false;
    public static boolean advancedregionmarket = false;

    private static TaskScheduler scheduler;

    public static TaskScheduler getScheduler() {
        return scheduler;
    }

    @Override
    public void onLoad() {
        if (getServer().getPluginManager().getPlugin("WorldGuard") != null) {
            worldguard = true;
            FlagRegistry.onLoad();
        }
    }

    @Override
    public void onEnable() {
        plugin = this;
        scheduler = UniversalScheduler.getScheduler(this);

        if (!ensureDataFolder()) {
            getLogger().severe("PebbleShop data folder could not be created: " + getDataFolder().getAbsolutePath());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        logConsole("&d[&bPebbleShop&d] &aEnabling PebbleShop - version " + this.getDescription().getVersion());
        saveDefaultConfig();

        try {
            Config.checkForConfigYMLupdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
        Config.loadConfig();
        disableLegacyHolograms();

        if (Config.database_type != null) {
            Utils.recognizeDatabase();
        } else {
            logConsole("&d[&bPebbleShop&d] &cDatabase type is missing or invalid in config.yml. Disabling plugin...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (!isSupportedServerVersion()) {
            logConsole("&d[&bPebbleShop&d] &4Unsupported server build detected. Craft package: "
                    + Bukkit.getServer().getClass().getPackage().getName()
                    + ", Bukkit version: " + Bukkit.getBukkitVersion()
                    + ", Server version: " + Bukkit.getVersion()
                    + ". Self-disabling...");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        } else {
            logConsole("&d[&bPebbleShop&d] &eCurrent protocol version initialized.");
        }

        economyPluginFound = setupEconomy();
        if (!economyPluginFound) {
            Config.useXP = true;
            logConsole("&d[&bPebbleShop&d] &cCannot find PebbleCore Cash. Switching to XP based economy.");
        }

        LanguageManager.loadLanguages();
        try {
            LanguageManager.checkForLanguagesYMLupdate();
        } catch (IOException e) {
            e.printStackTrace();
        }

        GuiData.loadGuiData();
        try {
            GuiData.checkForGuiDataYMLupdate();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (getServer().getPluginManager().getPlugin("AdvancedRegionMarket") != null) {
            advancedregionmarket = true;
            logConsole("&d[&bPebbleShop&d] &eAdvancedRegionMarket integration initialized.");
        }

        registerListeners();
        registerCommands();
        registerTabCompleters();

        if (getServer().getPluginManager().getPlugin("Slimefun") != null) {
            slimefun = true;
            logConsole("&d[&bPebbleShop&d] &eSlimefun integration initialized.");
        }

        if (getServer().getPluginManager().getPlugin("Towny") != null) {
            towny = true;
            logConsole("&d[&bPebbleShop&d] &eTowny integration initialized.");
        }

        ShopContainer.queryShopsToMemory();
        SignShopDisplay.syncAll();
        ShopContainer.startSqlQueueTask();
        if (Config.check_for_removed_shops) {
            LoadedChunksTask.startTask();
        }

        UpdateChecker checker = new UpdateChecker();
        checker.check();
    }

    private void disableLegacyHolograms() {
        Config.showholo = false;
        Config.holodistancing = false;
        Config.holodistancing_show_item_first = false;
        Config.holo_rotation = false;
        Config.settings_hologram_message_enabled = false;
        Config.settings_hologram_message_show_always = false;
        Config.settings_hologram_message_show_empty_shop_always = false;
    }

    private boolean ensureDataFolder() {
        return getDataFolder().exists() || getDataFolder().mkdirs();
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new ChestOpeningListener(), this);
        getServer().getPluginManager().registerEvents(new SignShopListener(), this);
        getServer().getPluginManager().registerEvents(new BlockBreakListener(), this);
        getServer().getPluginManager().registerEvents(new BlockPlaceListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerTransactionListener(), this);
        getServer().getPluginManager().registerEvents(new ChatListener(), this);
        getServer().getPluginManager().registerEvents(new BlockPistonExtendListener(), this);
        getServer().getPluginManager().registerEvents(new CommandCheckProfits(), this);
        getServer().getPluginManager().registerEvents(new UpdateChecker(), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(), this);
        getServer().getPluginManager().registerEvents(new ChestShopBreakPrevention(), this);
        getServer().getPluginManager().registerEvents(new HologramLifecycleListener(), this);

        if (Config.showholo) {
            if (Config.holodistancing) {
                getServer().getPluginManager().registerEvents(new PlayerCloseToChestListener(), this);
            } else {
                getServer().getPluginManager().registerEvents(new PlayerLookingAtChestShop(), this);
                getServer().getPluginManager().registerEvents(new PlayerLeavingListener(), this);
            }
        } else {
            logConsole("&d[&bPebbleShop&d] &eFloating holograms disabled. Using physical sign shops.");
        }

        if (advancedregionmarket) {
            getServer().getPluginManager().registerEvents(new AdvancedRegionMarket(), this);
        }
    }

    private void registerCommands() {
        PluginCommand ecs = getCommand("ecs");
        PluginCommand ecsadmin = getCommand("ecsadmin");
        CommandRegister register = new CommandRegister();
        try {
            if (Config.command_shop_alias) {
                register.registerCommandAlias(ecs, "shop");
            }
            if (Config.command_adminshop_alias) {
                register.registerCommandAlias(ecsadmin, "adminshop");
            }
        } catch (CommandFetchException e) {
            e.printStackTrace();
        }
        ecs.setExecutor(new MainCommands());
        ecsadmin.setExecutor(new EcsAdmin());
        getCommand("checkprofits").setExecutor(new CommandCheckProfits());
    }

    private void registerTabCompleters() {
        getCommand("ecs").setTabCompleter(new MainCommands());
        getCommand("ecsadmin").setTabCompleter(new EcsAdmin());
        getCommand("checkprofits").setTabCompleter(new CommandCheckProfits());
    }

    @Override
    public void onDisable() {
        if (scheduler != null) {
            scheduler.cancelTasks();
        }
        logConsole("&d[&bPebbleShop&d] &bSaving remaining SQL cache...");
        ShopContainer.saveSqlQueueCache();

        if (getDatabase() != null) {
            getDatabase().disconnect();
        }

        logConsole("&d[&bPebbleShop&d] &aCompleted.");

        try {
            for (Object object : Utils.onlinePackets) {
                if (object instanceof ASHologram) {
                    ASHologram hologram = (ASHologram) object;
                    hologram.destroy();
                    continue;
                }
                if (object instanceof FloatingItem) {
                    FloatingItem floatingItem = (FloatingItem) object;
                    floatingItem.destroy();
                }
            }
        } catch (Exception ignored) {
        }

        try {
            for (BlockOutline outline : Utils.activeOutlines.values()) {
                outline.hideOutline();
            }

            Utils.activeOutlines.clear();
            Utils.enabledOutlines.clear();
        } catch (Exception ignored) {
        }

        logConsole("&d[&bPebbleShop&d] &4Plugin is now disabled.");
    }

    private boolean isSupportedServerVersion() {
        String version = getServer().getBukkitVersion().toLowerCase(Locale.ROOT);
        return version.contains("1.21") || version.contains("26.");
    }

    private boolean setupEconomy() {
        ShopEconomy pebbleBridge = new PebbleEconomyBridge();
        if (pebbleBridge.isReady()) {
            econ = pebbleBridge;
            usingPebbleEconomy = true;
            logConsole("&d[&bPebbleShop&d] &aHooked into PebbleCore Cash economy.");
            return true;
        }

        PebbleVaultEconomy vaultBridge = new PebbleVaultEconomy();
        if (vaultBridge.isReady()) {
            econ = vaultBridge;
            usingPebbleEconomy = false;
            logConsole("&d[&bPebbleShop&d] &aHooked into Vault economy.");
            return true;
        }

        econ = null;
        usingPebbleEconomy = false;
        return false;
    }

    public static ShopEconomy getEconomy() {
        return econ;
    }

    public static boolean isUsingPebbleEconomy() {
        return usingPebbleEconomy;
    }

    public static EzChestShop getPlugin() {
        return plugin;
    }

    public DatabaseManager getDatabase() {
        return Utils.database;
    }

    public static void logConsole(String str) {
        if (Bukkit.getConsoleSender() == null)
            return;
        Bukkit.getConsoleSender().sendMessage(Utils.colorify(str));
    }
}
