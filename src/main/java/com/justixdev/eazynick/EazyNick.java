package com.justixdev.eazynick;

import com.justixdev.eazynick.commands.*;
import com.justixdev.eazynick.hooks.PlaceHolderExpansion;
import com.justixdev.eazynick.listeners.*;
import com.justixdev.eazynick.nms.ReflectionHelper;
import com.justixdev.eazynick.nms.fakegui.book.NMSBookBuilder;
import com.justixdev.eazynick.nms.fakegui.book.NMSBookUtils;
import com.justixdev.eazynick.nms.fakegui.sign.SignGUI;
import com.justixdev.eazynick.nms.netty.client.IncomingPacketInjector;
import com.justixdev.eazynick.nms.netty.client.IncomingPacketInjector_1_7;
import com.justixdev.eazynick.nms.netty.server.OutgoingPacketInjector;
import com.justixdev.eazynick.nms.netty.server.OutgoingPacketInjector_1_7;
import com.justixdev.eazynick.sql.MySQL;
import com.justixdev.eazynick.sql.MySQLNickManager;
import com.justixdev.eazynick.sql.MySQLPlayerDataManager;
import com.justixdev.eazynick.updater.Updater;
import com.justixdev.eazynick.utilities.*;
import com.justixdev.eazynick.utilities.AsyncTask.AsyncRunnable;
import com.justixdev.eazynick.utilities.configuration.BaseFileFactory;
import com.justixdev.eazynick.utilities.configuration.YamlFileFactory;
import com.justixdev.eazynick.utilities.configuration.yaml.*;
import com.justixdev.eazynick.utilities.mojang.*;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.stream.Stream;

public class EazyNick extends JavaPlugin {

    private static EazyNick instance;

    public static EazyNick getInstance() {
        return instance;
    }

    private File pluginFile;
    private String version;
    private boolean isCancelled;

    private MySQL mysql;
    private MySQLNickManager mysqlNickManager;
    private MySQLPlayerDataManager mysqlPlayerDataManager;

    private Updater updater;
    private Utils utils;
    private GUIManager guiManager;
    private ActionBarUtils actionBarUtils;
    private SetupYamlFile setupYamlFile;
    private NickNameYamlFile nickNameYamlFile;
    private SavedNickDataYamlFile savedNickDataYamlFile;
    private GUIYamlFile guiYamlFile;
    private LanguageYamlFile languageYamlFile;
    private ReflectionHelper reflectionHelper;
    private NMSBookBuilder nmsBookBuilder;
    private NMSBookUtils nmsBookUtils;
    private GameProfileBuilder_1_7 gameProfileBuilder_1_7;
    private GameProfileBuilder_1_8_R1 gameProfileBuilder_1_8_R1;
    private GameProfileBuilder gameProfileBuilder;
    private UUIDFetcher_1_7 uuidFetcher_1_7;
    private UUIDFetcher_1_8_R1 uuidFetcher_1_8_R1;
    private UUIDFetcher uuidFetcher;
    private SignGUI signGUI;
    private MineSkinAPI mineSkinAPI;
    private Object outgoingPacketInjector;

    @Override
    public void onEnable() {
        instance = this;

        reflectionHelper = new ReflectionHelper();

        version = reflectionHelper.getVersion().substring(1);
        pluginFile = getFile();

        // Initialize class instances
        utils = new Utils();
        actionBarUtils = new ActionBarUtils(this);

        final BaseFileFactory<YamlConfiguration> configurationFactory = new YamlFileFactory();
        setupYamlFile = configurationFactory.createConfigurationFile(this, SetupYamlFile.class);
        nickNameYamlFile = configurationFactory.createConfigurationFile(this, NickNameYamlFile.class);
        savedNickDataYamlFile = configurationFactory.createConfigurationFile(this, SavedNickDataYamlFile.class);
        guiYamlFile = configurationFactory.createConfigurationFile(this, GUIYamlFile.class);
        languageYamlFile = configurationFactory.createConfigurationFile(this, LanguageYamlFile.class);

        updater = new Updater(this);
        mineSkinAPI = new MineSkinAPI(getName(), getVersion());

        signGUI = new SignGUI(this);
        nmsBookBuilder = new NMSBookBuilder(this);
        nmsBookUtils = new NMSBookUtils(this);
        guiManager = new GUIManager(this);

        // Fix essentials '/nick' command bug
        if(utils.isPluginInstalled("Essentials"))
            Bukkit.getScheduler().runTaskLater(this, this::initiatePlugin, 20 * 3);
        else
            initiatePlugin();
    }

    @Override
    public void onDisable() {
        // Save nicked players
        savedNickDataYamlFile.save();

        // Fix buggy players
        utils.getNickedPlayers().keySet().forEach(uuid -> {
            Player player = Bukkit.getPlayer(uuid);

            if(player != null) {
                // Destroy scoreboard teams (for nametag prefixes and suffixes)
                if(utils.getScoreboardTeamManagers().containsKey(uuid))
                    utils.getScoreboardTeamManagers().get(uuid).destroyTeam();

                // Unregister injected packet handlers (client -> server)
                if(utils.getIncomingPacketInjectors().containsKey(uuid)) {
                    Object incomingPacketInjector = utils.getIncomingPacketInjectors().get(uuid);

                    try {
                        incomingPacketInjector
                                .getClass()
                                .getMethod("unregister")
                                .invoke(incomingPacketInjector);
                    } catch (Exception ignore) {
                    }
                }

                player.kickPlayer("§cYou will need to reconnect in order to be able to play properly");
            }
        });

        if(outgoingPacketInjector != null) {
            // Unregister injected packet handlers (server -> client)
            try {
                outgoingPacketInjector
                        .getClass()
                        .getMethod("unregister")
                        .invoke(outgoingPacketInjector);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        // Disconnect MySQL
        if (setupYamlFile.getConfiguration().getBoolean("BungeeCord"))
            mysql.disconnect();

        utils.sendConsole("§8§m----------§r§8 [ §5§l" + getName() + "§r §8] §m----------§r");
        utils.sendConsole("");
        utils.sendConsole("§7Plugin by§8: §a" + getDescription().getAuthors().toString().replace("[", "").replace("]", ""));
        utils.sendConsole("§7Version§8: §a" + getDescription().getVersion());
        utils.sendConsole("");
        utils.sendConsole("§8§m----------§r§8 [ §5§l" + getName() + "§r §8] §m----------§r");
    }

    @SuppressWarnings("ConstantConditions")
    private void initiatePlugin() {
        PluginManager pluginManager = Bukkit.getPluginManager();

        utils.reloadConfigs();
        utils.sendConsole("§8§m----------§r§8 [ §5§l" + getName() + "§r §8] §m----------§r");
        utils.sendConsole("");

        int versionNumber = Integer.parseInt(version.split("_")[1]);

        // Check if bukkit version is compatible
        if ((versionNumber < 7) || (versionNumber > 19)) {
            utils.sendConsole("§cYour version is currently not supported");

            isCancelled = true;
        } else {
            if (version.equals("1_7_R4")) {
                gameProfileBuilder_1_7 = new GameProfileBuilder_1_7();
                uuidFetcher_1_7 = new UUIDFetcher_1_7();
            } else {
                if(version.equals("1_8_R1")) {
                    gameProfileBuilder_1_8_R1 = new GameProfileBuilder_1_8_R1();
                    uuidFetcher_1_8_R1 = new UUIDFetcher_1_8_R1();
                } else {
                    gameProfileBuilder = new GameProfileBuilder();
                    uuidFetcher = new UUIDFetcher();
                }
            }

            new AsyncTask(new AsyncRunnable() {

                @Override
                public void run() {
                    // Inject packet handlers (server -> client)
                    if(version.equals("1_7_R4"))
                        outgoingPacketInjector = new OutgoingPacketInjector_1_7();
                    else
                        outgoingPacketInjector = new OutgoingPacketInjector();

                    try {
                        outgoingPacketInjector.getClass().getMethod("init").invoke(outgoingPacketInjector);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }

                    // Check for plugin updates
                    if (updater.checkForUpdates() && setupYamlFile.getConfiguration().getBoolean("AutoUpdater")) {
                        Bukkit.getScheduler().runTask(instance, () -> pluginManager.disablePlugin(instance));
                        return;
                    }

                    // Cache loaded plugins
                    Stream.of(Bukkit.getPluginManager().getPlugins())
                            .filter(Plugin::isEnabled)
                            .forEach(currentPlugin -> utils.getLoadedPlugins().put(currentPlugin.getName(), currentPlugin.getDescription().getAuthors()));

                    Bukkit.getScheduler().runTask(instance, () -> {
                        // Prepare PlaceholderAPI placeholders
                        if(utils.isPluginInstalled("PlaceholderAPI")) {
                            new PlaceHolderExpansion(instance).register();

                            utils.sendConsole("§7PlaceholderAPI hooked successfully");
                        }

                        if(utils.isPluginInstalled("SkinsRestorer") && setupYamlFile.getConfiguration().getBoolean("ChangeSkinsRestorerSkin")) {
                            try {
                                Plugin skinsRestorer = Bukkit.getPluginManager().getPlugin("SkinsRestorer");

                                if (reflectionHelper.getField(skinsRestorer.getClass(), "proxyMode").getBoolean(skinsRestorer))
                                    Bukkit.getMessenger().registerOutgoingPluginChannel(instance, "sr:messagechannel");
                            } catch (Exception ignore) {
                            }
                        }
                    });
                }
            }, 100).run();

            // Check if plugin features should be enabled -> APIMode: false
            if (!(setupYamlFile.getConfiguration().getBoolean("APIMode"))) {
                // Register all plugin commands
                getCommand("eazynick").setExecutor(new PluginCommand());
                getCommand("nick").setExecutor(new NickCommand());
                getCommand("unnick").setExecutor(new UnnickCommand());
                getCommand("name").setExecutor(new NameCommand());
                getCommand("resetname").setExecutor(new ResetNameCommand());
                getCommand("reloadconfig").setExecutor(new ReloadConfigCommand());
                getCommand("nickupdatecheck").setExecutor(new NickUpdateCheckCommand());
                getCommand("nickother").setExecutor(new NickOtherCommand());
                getCommand("changeskin").setExecutor(new ChangeSkinCommand());
                getCommand("changeskinother").setExecutor(new ChangeSkinOtherCommand());
                getCommand("resetskinother").setExecutor(new ResetSkinOtherCommand());
                getCommand("resetskin").setExecutor(new ResetSkinCommand());
                getCommand("fixskin").setExecutor(new FixSkinCommand());
                getCommand("nicklist").setExecutor(new NickListCommand());
                getCommand("bookgui").setExecutor(
                        version.startsWith("1_7")
                                ? new RankedNickGUICommand()
                                : new BookGUICommand()
                );
                getCommand("nickgui").setExecutor(
                        setupYamlFile.getConfiguration().getBoolean("OpenRankedNickGUIOnNickGUICommand")
                                ? new RankedNickGUICommand()
                                : new NickGUICommand()
                );
                getCommand("nickedplayers").setExecutor(new NickedPlayersCommand());
                getCommand("togglebungeenick").setExecutor(new ToggleBungeeNickCommand());
                getCommand("realname").setExecutor(new RealNameCommand());
                getCommand("renick").setExecutor(new ReNickCommand());
                getCommand("guinick").setExecutor(new GuiNickCommand());

                // Register listeners for plugin events
                pluginManager.registerEvents(new PlayerNickListener(), this);
                pluginManager.registerEvents(new PlayerUnnickListener(), this);

                // Register other event listeners
                pluginManager.registerEvents(new AsyncPlayerChatListener(), this);
                pluginManager.registerEvents(new PlayerCommandPreprocessListener(), this);
                pluginManager.registerEvents(new PlayerDropItemListener(), this);
                pluginManager.registerEvents(new InventoryClickListener(), this);
                pluginManager.registerEvents(new InventoryCloseListener(), this);
                pluginManager.registerEvents(new PlayerInteractListener(), this);
                pluginManager.registerEvents(new PlayerChangedWorldListener(), this);
                pluginManager.registerEvents(new PlayerDeathListener(), this);
                pluginManager.registerEvents(new PlayerRespawnListener(), this);
                pluginManager.registerEvents(new PlayerLoginListener(), this);

                //Allow every player to use nick + initialize IncomingPacketInjector
                Bukkit.getOnlinePlayers().forEach(currentPlayer -> {
                    utils.getCanUseNick().put(currentPlayer.getUniqueId(), 0L);
                    utils.getIncomingPacketInjectors().put(
                            currentPlayer.getUniqueId(),
                            version.startsWith("1_7")
                                    ? new IncomingPacketInjector_1_7(currentPlayer)
                                    : new IncomingPacketInjector(currentPlayer)
                    );
                });

                //Start action bar scheduler
                if(setupYamlFile.getConfiguration().getBoolean("NickActionBarMessage")
                        && setupYamlFile.getConfiguration().getBoolean("ShowNickActionBarWhenMySQLNicked")
                        && setupYamlFile.getConfiguration().getBoolean("BungeeCord")
                        && setupYamlFile.getConfiguration().getBoolean("LobbyMode")) {
                    new AsyncTask(new AsyncRunnable() {

                        @Override
                        public void run() {
                            //Display action bar to players that are nicked in mysql
                            Bukkit.getOnlinePlayers()
                                    .stream()
                                    .filter(currentPlayer -> (
                                            mysqlNickManager.isPlayerNicked(currentPlayer.getUniqueId())
                                                    && !(utils.getNickedPlayers().containsKey(currentPlayer.getUniqueId())))
                                    ).forEach(currentNickedPlayer -> {
                                        String nickName = mysqlNickManager.getNickName(currentNickedPlayer.getUniqueId()),
                                                prefix = mysqlPlayerDataManager.getChatPrefix(currentNickedPlayer.getUniqueId()),
                                                suffix = mysqlPlayerDataManager.getChatSuffix(currentNickedPlayer.getUniqueId());

                                        if(utils.getWorldsWithDisabledActionBar().stream().noneMatch(world -> world.equalsIgnoreCase(currentNickedPlayer.getWorld().getName())))
                                            actionBarUtils.sendActionBar(
                                                    currentNickedPlayer,
                                                    languageYamlFile.getConfigString(currentNickedPlayer,
                                                                    currentNickedPlayer.hasPermission("eazynick.actionbar.other")
                                                                            ? "NickActionBarMessageOther"
                                                                            : "NickActionBarMessage"
                                                            )
                                                            .replace("%nickName%", nickName)
                                                            .replace("%nickname%", nickName)
                                                            .replace("%nickPrefix%", prefix)
                                                            .replace("%nickprefix%", prefix)
                                                            .replace("%nickSuffix%", suffix)
                                                            .replace("%nicksuffix%", suffix)
                                                            .replace("%prefix%", utils.getPrefix())
                                            );
                                    });
                        }
                    }, 1000, 1000).run();
                }
            }

            //Register important event listeners
            pluginManager.registerEvents(new PlayerJoinListener(), this);
            pluginManager.registerEvents(new PlayerKickListener(), this);
            pluginManager.registerEvents(new PlayerQuitListener(), this);
            pluginManager.registerEvents(new WorldInitListener(), this);
            pluginManager.registerEvents(new ServerListPingListener(), this);

            utils.sendConsole("§7Version §d" + version + " §7was loaded successfully");

            // Prepare BungeeCord/MySQL mode
            if (setupYamlFile.getConfiguration().getBoolean("BungeeCord")) {
                // Open mysql connection
                mysql = new MySQL(
                        setupYamlFile.getConfiguration().getString("BungeeMySQL.hostname"),
                        setupYamlFile.getConfiguration().getString("BungeeMySQL.port"),
                        setupYamlFile.getConfiguration().getString("BungeeMySQL.database"),
                        setupYamlFile.getConfiguration().getString("BungeeMySQL.username"),
                        setupYamlFile.getConfiguration().getString("BungeeMySQL.password")
                );

                // Create default tables
                mysql.update("CREATE TABLE IF NOT EXISTS NickedPlayers (UUID varchar(64), NickName varchar(64), SkinName varchar(64))");
                mysql.update("CREATE TABLE IF NOT EXISTS NickedPlayerDatas (UUID varchar(64), GroupName varchar(64), ChatPrefix varchar(64), ChatSuffix varchar(64), TabPrefix varchar(64), TabSuffix varchar(64), TagPrefix varchar(64), TagSuffix varchar(64))");

                // Initialize mysql managers
                mysqlNickManager = new MySQLNickManager(mysql);
                mysqlPlayerDataManager = new MySQLPlayerDataManager(mysql);
            }

            // Initialize bStats
            BStatsMetrics bStatsMetrics = new BStatsMetrics(this, 11663);
            bStatsMetrics.addCustomChart(new BStatsMetrics.SimplePie(
                    "mysql",
                    () -> (setupYamlFile.getConfiguration().getBoolean("BungeeCord") ? "yes" : "no")
            ));

            utils.sendConsole("");
            utils.sendConsole("§7Plugin by§8: §a" + getDescription().getAuthors().toString().replace("[", "").replace("]", ""));
            utils.sendConsole("§7Version§8: §a" + getDescription().getVersion());
        }

        utils.sendConsole("");
        utils.sendConsole("§8§m----------§r§8 [ §5§l" + getName() + "§r §8] §m----------§r");

        if (isCancelled)
            pluginManager.disablePlugin(this);
    }

    public File getPluginFile() {
        return pluginFile;
    }

    public MySQLNickManager getMySQLNickManager() {
        return mysqlNickManager;
    }

    public MySQLPlayerDataManager getMySQLPlayerDataManager() {
        return mysqlPlayerDataManager;
    }

    public String getVersion() {
        return version;
    }

    public Updater getUpdater() {
        return updater;
    }

    public Utils getUtils() {
        return utils;
    }

    public GUIManager getGUIManager() {
        return guiManager;
    }

    public ActionBarUtils getActionBarUtils() {
        return actionBarUtils;
    }

    public SetupYamlFile getSetupYamlFile() {
        return setupYamlFile;
    }

    public NickNameYamlFile getNickNameYamlFile() {
        return nickNameYamlFile;
    }

    public SavedNickDataYamlFile getsavedNickDataYamlFile() {
        return savedNickDataYamlFile;
    }

    public GUIYamlFile getGUIYamlFile() {
        return guiYamlFile;
    }

    public LanguageYamlFile getLanguageYamlFile() {
        return languageYamlFile;
    }

    public ReflectionHelper getReflectionHelper() {
        return reflectionHelper;
    }

    public NMSBookBuilder getNMSBookBuilder() {
        return nmsBookBuilder;
    }

    public NMSBookUtils getNMSBookUtils() {
        return nmsBookUtils;
    }

    public GameProfileBuilder_1_7 getGameProfileBuilder_1_7() {
        return gameProfileBuilder_1_7;
    }

    public GameProfileBuilder_1_8_R1 getGameProfileBuilder_1_8_R1() {
        return gameProfileBuilder_1_8_R1;
    }

    public GameProfileBuilder getGameProfileBuilder() {
        return gameProfileBuilder;
    }

    public UUIDFetcher_1_7 getUUIDFetcher_1_7() {
        return uuidFetcher_1_7;
    }

    public UUIDFetcher_1_8_R1 getUUIDFetcher_1_8_R1() {
        return uuidFetcher_1_8_R1;
    }

    public UUIDFetcher getUUIDFetcher() {
        return uuidFetcher;
    }

    public SignGUI getSignGUI() {
        return signGUI;
    }

    public MineSkinAPI getMineSkinAPI() {
        return mineSkinAPI;
    }

    public Object getOutgoingPacketInjector() {
        return outgoingPacketInjector;
    }

}
