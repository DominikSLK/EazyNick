package com.justixdev.eazynick.listeners;

import com.justixdev.eazynick.EazyNick;
import com.justixdev.eazynick.api.NickManager;
import com.justixdev.eazynick.api.NickedPlayerData;
import com.justixdev.eazynick.hooks.LuckPermsHook;
import com.justixdev.eazynick.hooks.TABHook;
import com.justixdev.eazynick.sql.MySQLNickManager;
import com.justixdev.eazynick.sql.MySQLPlayerDataManager;
import com.justixdev.eazynick.utilities.Utils;
import com.justixdev.eazynick.utilities.configuration.yaml.SetupYamlFile;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;
import java.util.UUID;

public class PlayerQuitListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        EazyNick eazyNick = EazyNick.getInstance();
        Utils utils = eazyNick.getUtils();
        SetupYamlFile setupYamlFile = eazyNick.getSetupYamlFile();
        MySQLNickManager mysqlNickManager = eazyNick.getMysqlNickManager();
        MySQLPlayerDataManager mysqlPlayerDataManager = eazyNick.getMysqlPlayerDataManager();

        Player player = event.getPlayer();
        UUID uniqueId = player.getUniqueId();
        NickManager api = new NickManager(player);

        if (api.isNicked() && !setupYamlFile.getConfiguration().getBoolean("APIMode")) {
            boolean isBungeeCord = setupYamlFile.getConfiguration().getBoolean("BungeeCord");
            NickedPlayerData nickedPlayerData = utils.getNickedPlayers().get(uniqueId).copy();

            if(setupYamlFile.getConfiguration().getBoolean("NickCommands.OnUnnick")) {
                if(utils.isPluginInstalled("PlaceholderAPI"))
                    setupYamlFile.getConfiguration().getStringList("NickCommands.Unnick")
                            .forEach(command -> Bukkit.dispatchCommand(
                                    setupYamlFile.getConfiguration().getBoolean("NickCommands.SendAsConsole")
                                            ? Bukkit.getConsoleSender()
                                            : player,
                                    PlaceholderAPI.setPlaceholders(
                                            player,
                                            command
                                                    .replace("%player%", player.getName())
                                                    .replace("%nickName%", nickedPlayerData.getNickName()))));
                else
                    setupYamlFile.getConfiguration().getStringList("NickCommands.Unnick")
                            .forEach(command -> Bukkit.dispatchCommand(
                                    setupYamlFile.getConfiguration().getBoolean("NickCommands.SendAsConsole")
                                            ? Bukkit.getConsoleSender()
                                            : player,
                                    command
                                            .replace("%player%", player.getName())
                                            .replace("%nickName%", nickedPlayerData.getNickName())));
            }

            if(utils.getOldExperienceLevels().containsKey(player.getUniqueId())) {
                player.setLevel(utils.getOldExperienceLevels().get(player.getUniqueId()));

                utils.getOldExperienceLevels().remove(player.getUniqueId());
            }

            api.unnickPlayerWithoutRemovingMySQL(
                    !(setupYamlFile.getConfiguration().getBoolean("DisconnectUnnick") || isBungeeCord),
                    false);

            if(utils.isPluginInstalled("LuckPerms"))
                new LuckPermsHook(player).resetNodes();

            if(utils.isPluginInstalled("TAB", "NEZNAMY")
                    && setupYamlFile.getConfiguration().getBoolean("ChangeGroupAndPrefixAndSuffixInTAB"))
                new TABHook(player).reset();

            if(setupYamlFile.getConfiguration().getBoolean("OverwriteJoinQuitMessages")) {
                String message = setupYamlFile.getConfigString(player, "OverwrittenMessages.Quit");

                if(isBungeeCord && mysqlNickManager.isNicked(uniqueId))
                    message = message.replace("%name%", mysqlNickManager.getNickName(uniqueId))
                            .replace(
                                    "%displayName%",
                                    mysqlPlayerDataManager.getChatPrefix(uniqueId)
                                            + mysqlNickManager.getNickName(uniqueId)
                                            + mysqlPlayerDataManager.getChatSuffix(uniqueId))
                            .replace(
                                    "%displayname%",
                                    mysqlPlayerDataManager.getChatPrefix(uniqueId)
                                            + mysqlNickManager.getNickName(uniqueId)
                                            + mysqlPlayerDataManager.getChatSuffix(uniqueId));
                else
                    message = message.replace("%name%", nickedPlayerData.getNickName())
                            .replace(
                                    "%displayName%",
                                    nickedPlayerData.getChatPrefix()
                                            + nickedPlayerData.getNickName()
                                            + nickedPlayerData.getChatSuffix())
                            .replace(
                                    "%displayname%",
                                    nickedPlayerData.getChatPrefix()
                                            + nickedPlayerData.getNickName()
                                            + nickedPlayerData.getChatSuffix());

                event.setQuitMessage(message);
            } else if (!Objects.requireNonNull(event.getQuitMessage()).isEmpty()) {
                if (event.getQuitMessage().contains("formerly known as"))
                    event.setQuitMessage("§e" + player.getName() + " left the game.");

                event.setQuitMessage(event.getQuitMessage().replace(player.getName(), nickedPlayerData.getNickName()));
            }

            if(isBungeeCord) {
                mysqlNickManager.clearCachedData(uniqueId);
                mysqlPlayerDataManager.clearCachedData(uniqueId);
            }
        }

        eazyNick.getPacketInjectorManager().remove(player);
    }

}
