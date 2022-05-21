package com.justixdev.eazynick.commands;

import com.justixdev.eazynick.EazyNick;
import com.justixdev.eazynick.api.NickManager;
import com.justixdev.eazynick.api.PlayerUnnickEvent;
import com.justixdev.eazynick.nms.fakegui.book.BookPage;
import com.justixdev.eazynick.nms.fakegui.book.NMSBookBuilder;
import com.justixdev.eazynick.nms.fakegui.book.NMSBookUtils;
import com.justixdev.eazynick.sql.MySQLNickManager;
import com.justixdev.eazynick.sql.MySQLPlayerDataManager;
import com.justixdev.eazynick.utilities.Utils;
import com.justixdev.eazynick.utilities.configuration.yaml.GUIYamlFile;
import com.justixdev.eazynick.utilities.configuration.yaml.LanguageYamlFile;
import com.justixdev.eazynick.utilities.configuration.yaml.SetupYamlFile;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

public class BookGUICommand implements CommandExecutor {

	@Override
	public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
		EazyNick eazyNick = EazyNick.getInstance();
		Utils utils = eazyNick.getUtils();
		SetupYamlFile setupYamlFile = eazyNick.getSetupYamlFile();
		LanguageYamlFile languageYamlFile = eazyNick.getLanguageYamlFile();
		GUIYamlFile guiYamlFile = eazyNick.getGUIYamlFile();
		NMSBookUtils nmsBookUtils = eazyNick.getNMSBookUtils();
		NMSBookBuilder nmsBookBuilder = eazyNick.getNMSBookBuilder();
		MySQLNickManager mysqlNickManager = eazyNick.getMySQLNickManager();
		MySQLPlayerDataManager mysqlPlayerDataManager = eazyNick.getMySQLPlayerDataManager();
		
		String prefix = utils.getPrefix();
		
		if(sender instanceof Player) {
			Player player = (Player) sender;
			
			if(new NickManager(player).isNicked()) {
				if(player.hasPermission("eazynick.nick.reset"))
					Bukkit.getPluginManager().callEvent(new PlayerUnnickEvent(player));
			} else if((mysqlNickManager != null) && mysqlNickManager.isPlayerNicked(player.getUniqueId()) && setupYamlFile.getConfiguration().getBoolean("LobbyMode") && setupYamlFile.getConfiguration().getBoolean("RemoveMySQLNickOnUnnickWhenLobbyModeEnabled")) {
				if(player.hasPermission("eazynick.nick.reset")) {
					mysqlNickManager.removePlayer(player.getUniqueId());
					mysqlPlayerDataManager.removeData(player.getUniqueId());
					
					languageYamlFile.sendMessage(player, languageYamlFile.getConfigString(player, "Messages.Unnick").replace("%prefix%", prefix));
				}
			} else if(player.hasPermission("eazynick.gui.book")) {
				if(!(setupYamlFile.getConfiguration().getStringList("DisabledNickWorlds").contains(player.getWorld().getName()))) {
					if(args.length == 0) {
						if(guiYamlFile.getConfiguration().getBoolean("BookGUI.Page1.Enabled")) {
							ArrayList<TextComponent> textComponents = new ArrayList<>();
							
							for(String s : guiYamlFile.getConfigString(player, "BookGUI.Page1.Text").split("%nl%"))
								textComponents.add(new TextComponent(s + "\n"));
							
							for(String s : guiYamlFile.getConfigString(player, "BookGUI.Accept.Text").split("%nl%")) {
								TextComponent option = new TextComponent(s + "\n");
								option.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/bookgui accept"));
								option.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Collections.singletonList(new Text(guiYamlFile.getConfigString(player, "BookGUI.Accept.Hover")))));
								
								textComponents.add(option);
							}
							
							nmsBookUtils.open(player, nmsBookBuilder.create(guiYamlFile.getConfigString(player, "BookGUI.Page1.Title"), new BookPage(textComponents)));
							return true;
						} else
							args = new String[] { "accept" };
					}
					
					if(args.length == 1) {
						if(args[0].equalsIgnoreCase("accept")) {
							List<String> ranks = new ArrayList<>();
							List<TextComponent> textComponentsOfFirstPage = new ArrayList<>(), textComponentsOfSecondPage = new ArrayList<>();
							
							for(String s : guiYamlFile.getConfigString(player, "BookGUI.Page2.Text").split("%nl%"))
								textComponentsOfFirstPage.add(new TextComponent(s + "\n"));
							
							for (int i = 1; i <= 18; i++) {
								String permission = guiYamlFile.getConfigString(player, "RankGUI.Rank" + i + ".Permission");
								
								if(guiYamlFile.getConfiguration().getBoolean("RankGUI.Rank" + i + ".Enabled") && (permission.equalsIgnoreCase("NONE") || player.hasPermission(permission))) {
									String rank = guiYamlFile.getConfigString(player, "RankGUI.Rank" + i + ".Rank"), rankName = guiYamlFile.getConfigString(player, "RankGUI.Rank" + i + ".RankName");
									TextComponent textComponent = new TextComponent(guiYamlFile.getConfigString(player, "BookGUI.Rank.Text").replace("%rank%", rank).replace("%nl%", "\n"));
									textComponent.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/bookgui " + args[0] + " " + rankName));
									textComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Collections.singletonList(new Text(guiYamlFile.getConfigString(player, "BookGUI.Rank.Hover").replace("%rank%", rank)))));
									
									if(textComponentsOfFirstPage.size() < 14)
										textComponentsOfFirstPage.add(textComponent);
									else
										textComponentsOfSecondPage.add(textComponent);
									
									ranks.add(rankName);
								}
							}
							
							if(ranks.size() > 1)
								nmsBookUtils.open(player, nmsBookBuilder.create(guiYamlFile.getConfigString(player, "BookGUI.Page2.Title"), new BookPage(textComponentsOfFirstPage), new BookPage(textComponentsOfSecondPage)));
							else if(!(ranks.isEmpty()))
								Bukkit.dispatchCommand(player, "bookgui " + args[0] + " " + ranks.get(0));
						}
					} else if(args.length == 2) {
						if(setupYamlFile.getConfiguration().getBoolean("Settings.ChangeOptions.Skin")) {
							ArrayList<TextComponent> textComponents = new ArrayList<>();
							
							for(String s : guiYamlFile.getConfigString(player, "BookGUI.Page3.Text").split("%nl%"))
								textComponents.add(new TextComponent(s + "\n"));
							
							TextComponent option1 = new TextComponent(guiYamlFile.getConfigString(player, "BookGUI.NormalSkin.Text").replace("%nl%", "\n"));
							option1.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/bookgui " + args[0] + " " + args[1] + " DEFAULT"));
							option1.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Collections.singletonList(new Text(guiYamlFile.getConfigString(player, "BookGUI.NormalSkin.Hover")))));
							
							textComponents.add(option1);
							
							TextComponent option2 = new TextComponent(guiYamlFile.getConfigString(player, "BookGUI.SteveAlexSkin.Text").replace("%nl%", "\n"));
							option2.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/bookgui " + args[0] + " " + args[1] + " NORMAL"));
							option2.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Collections.singletonList(new Text(guiYamlFile.getConfigString(player, "BookGUI.SteveAlexSkin.Hover")))));
							
							textComponents.add(option2);
							
							TextComponent option3 = new TextComponent(guiYamlFile.getConfigString(player, "BookGUI.RandomSkin.Text").replace("%nl%", "\n"));
							option3.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/bookgui " + args[0] + " " + args[1] + " RANDOM"));
							option3.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Collections.singletonList(new Text(guiYamlFile.getConfigString(player, "BookGUI.RandomSkin.Hover")))));
							
							textComponents.add(option3);
							
							TextComponent option4 = new TextComponent(setupYamlFile.getConfiguration().getBoolean("AllowBookGUISkinFromName") ? guiYamlFile.getConfigString(player, "BookGUI.SkinFromName.Text").replace("%nl%", "\n") : "");
							option4.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/bookgui " + args[0] + " " + args[1] + " SKINFROMNAME"));
							option4.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Collections.singletonList(new Text(guiYamlFile.getConfigString(player, "BookGUI.SkinFromName.Hover")))));
							
							textComponents.add(option4);
							
							if(utils.getLastSkinNames().containsKey(player.getUniqueId())) {
								TextComponent option5 = new TextComponent(utils.getLastSkinNames().containsKey(player.getUniqueId()) ? (guiYamlFile.getConfigString(player, "BookGUI.ReuseSkin.Text").replace("%skin%", utils.getLastSkinNames().get(player.getUniqueId()))).replace("%nl%", "\n") : "");
								option5.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/bookgui " + args[0] + " " + args[1] + " " + utils.getLastSkinNames().get(player.getUniqueId())));
								option5.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Collections.singletonList(new Text(guiYamlFile.getConfigString(player, "BookGUI.ReuseSkin.Hover")))));
							
								textComponents.add(option5);
							}
								
							nmsBookUtils.open(player, nmsBookBuilder.create(guiYamlFile.getConfigString(player, "BookGUI.Page3.Title"), new BookPage(textComponents)));
						} else
							player.chat("/bookgui " + args[0] + " " + args[1] + " DEFAULT");
					} else if(args.length == 3) {
						ArrayList<TextComponent> textComponents = new ArrayList<>();
						
						for(String s : guiYamlFile.getConfigString(player, "BookGUI.Page4.Text").split("%nl%"))
							textComponents.add(new TextComponent(s + "\n"));
						
						if(setupYamlFile.getConfiguration().getBoolean("AllowBookGUICustomName") && (player.hasPermission("eazynick.nick.custom"))) {
							TextComponent option1 = new TextComponent(guiYamlFile.getConfigString(player, "BookGUI.EnterName.Text").replace("%nl%", "\n"));
							option1.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/bookgui " + args[0] + " " + args[1] + " " + args[2] + " ENTERNAME"));
							option1.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Collections.singletonList(new Text(guiYamlFile.getConfigString(player, "BookGUI.EnterName.Hover")))));
							
							textComponents.add(option1);
						} else if(!(utils.getLastNickNames().containsKey(player.getUniqueId()))) {
							player.chat("/bookgui " + args[0] + " " + args[1] + " " + args[2] + " RANDOM");
							
							return true;
						}
						
						TextComponent option2 = new TextComponent(guiYamlFile.getConfigString(player, "BookGUI.RandomName.Text").replace("%nl%", "\n"));
						option2.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/bookgui " + args[0] + " " + args[1] + " " + args[2] + " RANDOM"));
						option2.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Collections.singletonList(new Text(guiYamlFile.getConfigString(player, "BookGUI.RandomName.Hover")))));
						
						textComponents.add(option2);
						
						TextComponent option3 = new TextComponent(utils.getLastSkinNames().containsKey(player.getUniqueId()) ? guiYamlFile.getConfigString(player, "BookGUI.ReuseName.Text").replace("%name%", utils.getLastNickNames().get(player.getUniqueId())).replace("%nl%", "\n") : "");
						
						if(utils.getLastNickNames().containsKey(player.getUniqueId())) {
							option3.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/guinick " + args[1] + " " + args[2] + " " + utils.getLastNickNames().get(player.getUniqueId())));
							option3.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Collections.singletonList(new Text(guiYamlFile.getConfigString(player, "BookGUI.ReuseName.Hover").replace("%name%", utils.getLastNickNames().get(player.getUniqueId()))))));
						
							textComponents.add(option3);
						}
	
						nmsBookUtils.open(player, nmsBookBuilder.create(guiYamlFile.getConfigString(player, "BookGUI.Page4.Title"), new BookPage(textComponents)));
					} else if(args[3].equalsIgnoreCase("RANDOM")) {
						AtomicReference<String> currentName = new AtomicReference<>();

						do {
							currentName.set(utils.getNickNames().get((new Random().nextInt(utils.getNickNames().size()))));
						} while (utils.getNickedPlayers().values().stream().anyMatch(currentNickedPlayerData -> currentNickedPlayerData.getNickName().equalsIgnoreCase(currentName.get())));

						String name = currentName.get();

						ArrayList<TextComponent> textComponents = new ArrayList<>();
						
						for(String s : guiYamlFile.getConfigString(player, "BookGUI.Page5.Text").replace("%name%", name).split("%nl%"))
							textComponents.add(new TextComponent(s + "\n"));
					
						TextComponent option1 = new TextComponent(guiYamlFile.getConfigString(player, "BookGUI.OptionUseName.Text").replace("%nl%", "\n"));
						option1.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/guinick " + args[1] + " " + args[2]  + " " + name));
						option1.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Collections.singletonList(new Text(guiYamlFile.getConfigString(player, "BookGUI.OptionUseName.Hover")))));
						
						textComponents.add(option1);
						
						TextComponent option2 = new TextComponent(guiYamlFile.getConfigString(player, "BookGUI.OptionTryAgain.Text").replace("%nl%", "\n"));
						option2.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/bookgui " + args[0] + " " + args[1] + " " + args[2]  + " RANDOM"));
						option2.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Collections.singletonList(new Text(guiYamlFile.getConfigString(player, "BookGUI.OptionTryAgain.Hover")))));
						
						textComponents.add(option2);
						
						TextComponent option3 = new TextComponent(guiYamlFile.getConfigString(player, "BookGUI.OptionEnterName.Text").replace("%nl%", "\n"));
						option3.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/bookgui " + args[0] + " " + args[1] + " " + args[2]  + " ENTERNAME"));
						option3.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Collections.singletonList(new Text(guiYamlFile.getConfigString(player, "BookGUI.OptionEnterName.Hover")))));
						
						if(setupYamlFile.getConfiguration().getBoolean("AllowBookGUICustomName") && (player.hasPermission("eazynick.nick.custom")))
							textComponents.add(option3);
						
						nmsBookUtils.open(player, nmsBookBuilder.create(guiYamlFile.getConfigString(player, "BookGUI.Page5.Title"), new BookPage(textComponents)));
					} else if(args[3].equalsIgnoreCase("ENTERNAME")) {
						if(setupYamlFile.getConfiguration().getBoolean("AllowBookGUICustomName") && (player.hasPermission("eazynick.nick.custom"))) {
							if(eazyNick.getVersion().equals("1_7_R4") || eazyNick.getVersion().equals("1_8_R1") || !(setupYamlFile.getConfiguration().getBoolean("UseSignGUIForCustomName") || setupYamlFile.getConfiguration().getBoolean("UseAnvilGUIForCustomName"))) {
								utils.getPlayersTypingNameInChat().put(player.getUniqueId(), args[1] + " " + args[2]);
								
								player.closeInventory();
								languageYamlFile.sendMessage(player, languageYamlFile.getConfigString(player, "Messages.TypeNameInChat").replace("%prefix%", prefix));
							} else
								eazyNick.getGUIManager().openCustomGUI(player, args[1], args[2]);
						}
					}
				} else
					languageYamlFile.sendMessage(player, languageYamlFile.getConfigString(player, "Messages.DisabledWorld").replace("%prefix%", prefix));
			} else
				languageYamlFile.sendMessage(player, utils.getNoPerm());
		} else
			utils.sendConsole(utils.getNotPlayer());
		
		return true;
	}
	
}
