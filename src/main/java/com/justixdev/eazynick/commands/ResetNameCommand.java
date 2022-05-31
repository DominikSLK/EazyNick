package com.justixdev.eazynick.commands;

import com.justixdev.eazynick.EazyNick;
import com.justixdev.eazynick.api.NickManager;
import com.justixdev.eazynick.sql.MySQLNickManager;
import com.justixdev.eazynick.utilities.Utils;
import com.justixdev.eazynick.utilities.configuration.yaml.LanguageYamlFile;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ResetNameCommand implements CommandExecutor {

	@Override
	public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
		EazyNick eazyNick = EazyNick.getInstance();
		Utils utils = eazyNick.getUtils();
		LanguageYamlFile languageYamlFile = eazyNick.getLanguageYamlFile();
		MySQLNickManager mysqlNickManager = eazyNick.getMySQLNickManager();

		if(!(sender instanceof Player)) {
			utils.sendConsole(utils.getNotPlayer());
			return true;
		}

		Player player = (Player) sender;

		if(!(player.hasPermission("eazynick.nick.reset"))) {
			languageYamlFile.sendMessage(player, utils.getNoPerm());
			return true;
		}

		NickManager api = new NickManager(player);
		api.setName(api.getRealName());

		if(mysqlNickManager != null)
			mysqlNickManager.removePlayer(player.getUniqueId());

		languageYamlFile.sendMessage(
				player,
				languageYamlFile.getConfigString(player, "Messages.ResetName")
						.replace("%prefix%", utils.getPrefix())
		);
		
		return true;
	}
	
}
