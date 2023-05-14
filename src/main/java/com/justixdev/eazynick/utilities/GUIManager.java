package com.justixdev.eazynick.utilities;

import com.justixdev.eazynick.EazyNick;
import com.justixdev.eazynick.nms.guis.AnvilGUI;
import com.justixdev.eazynick.nms.guis.SignGUI;
import com.justixdev.eazynick.utilities.configuration.yaml.GUIYamlFile;
import com.justixdev.eazynick.utilities.configuration.yaml.SetupYamlFile;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

import static com.justixdev.eazynick.nms.ReflectionHelper.NMS_VERSION;
import static com.justixdev.eazynick.nms.ReflectionHelper.VERSION_13_OR_LATER;

public class GUIManager {

    private final Utils utils;
    private final GUIYamlFile guiYamlFile;
    private final SetupYamlFile setupYamlFile;
    private final SignGUI signGUI;

    public GUIManager(EazyNick eazyNick) {
        this.utils = eazyNick.getUtils();
        this.guiYamlFile = eazyNick.getGuiYamlFile();
        this.setupYamlFile = eazyNick.getSetupYamlFile();
        this.signGUI = eazyNick.getSignGUI();
    }

    public void openNickList(Player player, int page) {
        Inventory inventory = Bukkit.createInventory(
                null,
                45,
                this.guiYamlFile.getConfigString(player, "NickNameGUI.InventoryTitle")
                        .replace("%currentPage%", String.valueOf(page + 1))
                        .replace("%currentpage%", String.valueOf(page + 1))
        );
        ArrayList<String> toShow = new ArrayList<>();

        player.openInventory(inventory);

        for (int i = 36 * page; i < this.utils.getNickNames().size(); i++) {
            if(toShow.size() >= 36)
                break;

            toShow.add(this.utils.getNickNames().get(i));
        }

        int i = 0;

        for (String nickName : toShow) {
            inventory.setItem(
                    i,
                    new ItemBuilder(1)
                            .setDisplayName(
                                    this.guiYamlFile.getConfigString(player, "NickNameGUI.NickName.DisplayName")
                                            .replace("%nickName%", nickName)
                                            .replace("%nickname%", nickName)
                            )
                            .setSkullOwner(toShow.size() > 12
                                    ? "MHF_Question"
                                    : nickName
                            )
                            .build()
            );

            i++;
        }

        if(page != 0) {
            inventory.setItem(
                    36,
                    new ItemBuilder(Material.ARROW)
                            .setDisplayName(this.guiYamlFile.getConfigString(player, "NickNameGUI.Previous.DisplayName"))
                            .build()
            );
        }

        if(this.utils.getNickNames().size() > ((page + 1) * 36)) {
            inventory.setItem(
                    44,
                    new ItemBuilder(Material.ARROW)
                            .setDisplayName(this.guiYamlFile.getConfigString(player, "NickNameGUI.Next.DisplayName"))
                            .build()
            );
        }

        this.utils.getNickNameListPages().put(player.getUniqueId(), page);
    }

    public void openCustomGUI(Player player, String rankName, String skinType) {
        if(this.setupYamlFile.getConfiguration().getBoolean("UseSignGUIForCustomName")) {
            this.signGUI.open(
                    player,
                    this.guiYamlFile.getConfigString(player, "SignGUI.Line1"),
                    this.guiYamlFile.getConfigString(player, "SignGUI.Line2"),
                    this.guiYamlFile.getConfigString(player, "SignGUI.Line3"),
                    this.guiYamlFile.getConfigString(player, "SignGUI.Line4"),
                    event -> {
                        String name = event.getLines()[0];
                        int nameLengthMin = Math.max(Math.min(this.setupYamlFile.getConfiguration().getInt("Settings.NameLength.Min"), 16), 1),
                                nameLengthMax = Math.max(Math.min(this.setupYamlFile.getConfiguration().getInt("Settings.NameLength.Max"), 16), 1);

                        if(!name.isEmpty() && (name.length() <= nameLengthMax) && (name.length() >= nameLengthMin))
                            this.utils.performRankedNick(player, rankName, skinType, name);
                    }
            );
        } else {
            AnvilGUI gui = new AnvilGUI(player, event -> {
                if (event.getSlot() == AnvilGUI.AnvilSlot.OUTPUT) {
                    event.setWillClose(true);
                    event.setWillDestroy(true);

                    this.utils.performRankedNick(player, rankName, skinType, event.getName());
                } else {
                    event.setWillClose(false);
                    event.setWillDestroy(false);
                }
            });

            gui.setSlot(
                    AnvilGUI.AnvilSlot.INPUT_LEFT,
                    new ItemBuilder(Material.PAPER)
                            .setDisplayName(this.guiYamlFile.getConfigString(player, "AnvilGUI.Title"))
                            .build()
            );

            try {
                gui.open();
            } catch (IllegalAccessException | InvocationTargetException | InstantiationException ex) {
                ex.printStackTrace();
            }
        }
    }

    public void openRankedNickGUI(Player player, String text) {
        EazyNick eazyNick = EazyNick.getInstance();
        GUIYamlFile guiYamlFile = eazyNick.getGuiYamlFile();

        this.utils.getLastGUITexts().put(player.getUniqueId(), text);

        String[] args = text.isEmpty() ? new String[0] : text.split(" ");

        if(args.length == 0) {
            Inventory inv = Bukkit.createInventory(
                    null,
                    27,
                    guiYamlFile.getConfigString(player, "RankedNickGUI.Step1.InventoryTitle"));

            for (int i = 0; i < inv.getSize(); i++)
                inv.setItem(
                        i,
                        new ItemBuilder(
                                Material.getMaterial(VERSION_13_OR_LATER
                                        ? "BLACK_STAINED_GLASS_PANE"
                                        : "STAINED_GLASS_PANE"
                                ),
                                1,
                                VERSION_13_OR_LATER ? 0 : 15
                        )
                                .setDisplayName("§r")
                                .build()
                );

            ArrayList<ItemStack> availableRanks = new ArrayList<>();

            for (int i = 1; i <= 18; i++) {
                String permission = guiYamlFile.getConfigString(player, "RankGUI.Rank" + i + ".Permission");

                if(guiYamlFile.getConfiguration().getBoolean("RankGUI.Rank" + i + ".Enabled") && (permission.equalsIgnoreCase("NONE") || player.hasPermission(permission)))
                    availableRanks.add(
                            new ItemBuilder(
                                    Material.valueOf(
                                            guiYamlFile.getConfigString(player, "RankedNickGUI.Step1.Rank" + i + ".ItemType")
                                    ),
                                    1,
                                    guiYamlFile.getConfiguration().getInt("RankedNickGUI.Step1.Rank" + i + ".MetaData")
                            )
                                    .setDisplayName(guiYamlFile.getConfigString(player, "RankGUI.Rank" + i + ".Rank"))
                                    .build()
                    );
            }

            switch (availableRanks.size()) {
                case 1:
                    inv.setItem(13, availableRanks.get(0));
                    break;
                case 2:
                    inv.setItem(11, availableRanks.get(0));
                    inv.setItem(15, availableRanks.get(1));
                    break;
                case 3:
                    inv.setItem(10, availableRanks.get(0));
                    inv.setItem(13, availableRanks.get(1));
                    inv.setItem(16, availableRanks.get(2));
                    break;
                case 4:
                    inv.setItem(10, availableRanks.get(0));
                    inv.setItem(12, availableRanks.get(1));
                    inv.setItem(14, availableRanks.get(2));
                    inv.setItem(16, availableRanks.get(3));
                    break;
                case 5:
                    inv.setItem(9, availableRanks.get(0));
                    inv.setItem(11, availableRanks.get(1));
                    inv.setItem(13, availableRanks.get(2));
                    inv.setItem(15, availableRanks.get(3));
                    inv.setItem(17, availableRanks.get(4));
                    break;
                case 6:
                    inv.setItem(4, availableRanks.get(0));
                    inv.setItem(9, availableRanks.get(1));
                    inv.setItem(11, availableRanks.get(2));
                    inv.setItem(15, availableRanks.get(3));
                    inv.setItem(17, availableRanks.get(4));
                    inv.setItem(22, availableRanks.get(5));
                    break;
                case 7:
                    inv.setItem(2, availableRanks.get(0));
                    inv.setItem(6, availableRanks.get(1));
                    inv.setItem(10, availableRanks.get(2));
                    inv.setItem(13, availableRanks.get(3));
                    inv.setItem(16, availableRanks.get(4));
                    inv.setItem(20, availableRanks.get(5));
                    inv.setItem(24, availableRanks.get(6));
                    break;
                case 8:
                    inv.setItem(2, availableRanks.get(0));
                    inv.setItem(6, availableRanks.get(1));
                    inv.setItem(10, availableRanks.get(2));
                    inv.setItem(12, availableRanks.get(3));
                    inv.setItem(14, availableRanks.get(4));
                    inv.setItem(16, availableRanks.get(5));
                    inv.setItem(20, availableRanks.get(6));
                    inv.setItem(24, availableRanks.get(7));
                    break;
                case 9:
                    inv.setItem(2, availableRanks.get(0));
                    inv.setItem(6, availableRanks.get(1));
                    inv.setItem(9, availableRanks.get(2));
                    inv.setItem(11, availableRanks.get(3));
                    inv.setItem(13, availableRanks.get(4));
                    inv.setItem(15, availableRanks.get(5));
                    inv.setItem(17, availableRanks.get(6));
                    inv.setItem(20, availableRanks.get(7));
                    inv.setItem(24, availableRanks.get(8));
                    break;
                case 10:
                    inv.setItem(2, availableRanks.get(0));
                    inv.setItem(4, availableRanks.get(1));
                    inv.setItem(6, availableRanks.get(2));
                    inv.setItem(10, availableRanks.get(3));
                    inv.setItem(12, availableRanks.get(4));
                    inv.setItem(14, availableRanks.get(5));
                    inv.setItem(16, availableRanks.get(6));
                    inv.setItem(20, availableRanks.get(7));
                    inv.setItem(22, availableRanks.get(8));
                    inv.setItem(24, availableRanks.get(9));
                    break;
                case 11:
                    inv.setItem(1, availableRanks.get(0));
                    inv.setItem(3, availableRanks.get(1));
                    inv.setItem(5, availableRanks.get(2));
                    inv.setItem(7, availableRanks.get(3));
                    inv.setItem(11, availableRanks.get(4));
                    inv.setItem(13, availableRanks.get(5));
                    inv.setItem(15, availableRanks.get(6));
                    inv.setItem(19, availableRanks.get(7));
                    inv.setItem(21, availableRanks.get(8));
                    inv.setItem(23, availableRanks.get(9));
                    inv.setItem(25, availableRanks.get(10));
                    break;
                case 12:
                    inv.setItem(2, availableRanks.get(0));
                    inv.setItem(3, availableRanks.get(1));
                    inv.setItem(5, availableRanks.get(2));
                    inv.setItem(6, availableRanks.get(3));
                    inv.setItem(10, availableRanks.get(4));
                    inv.setItem(12, availableRanks.get(5));
                    inv.setItem(14, availableRanks.get(6));
                    inv.setItem(16, availableRanks.get(7));
                    inv.setItem(20, availableRanks.get(8));
                    inv.setItem(21, availableRanks.get(9));
                    inv.setItem(23, availableRanks.get(10));
                    inv.setItem(24, availableRanks.get(11));
                    break;
                case 13:
                    inv.setItem(1, availableRanks.get(0));
                    inv.setItem(3, availableRanks.get(1));
                    inv.setItem(5, availableRanks.get(2));
                    inv.setItem(7, availableRanks.get(3));
                    inv.setItem(9, availableRanks.get(4));
                    inv.setItem(11, availableRanks.get(5));
                    inv.setItem(13, availableRanks.get(6));
                    inv.setItem(15, availableRanks.get(7));
                    inv.setItem(17, availableRanks.get(8));
                    inv.setItem(19, availableRanks.get(9));
                    inv.setItem(21, availableRanks.get(10));
                    inv.setItem(23, availableRanks.get(11));
                    inv.setItem(25, availableRanks.get(12));
                    break;
                case 14:
                    inv.setItem(0, availableRanks.get(0));
                    inv.setItem(2, availableRanks.get(1));
                    inv.setItem(4, availableRanks.get(2));
                    inv.setItem(6, availableRanks.get(3));
                    inv.setItem(8, availableRanks.get(4));
                    inv.setItem(10, availableRanks.get(5));
                    inv.setItem(12, availableRanks.get(6));
                    inv.setItem(14, availableRanks.get(7));
                    inv.setItem(16, availableRanks.get(8));
                    inv.setItem(18, availableRanks.get(9));
                    inv.setItem(20, availableRanks.get(10));
                    inv.setItem(22, availableRanks.get(11));
                    inv.setItem(24, availableRanks.get(12));
                    inv.setItem(26, availableRanks.get(13));
                    break;
                case 15:
                    inv.setItem(0, availableRanks.get(0));
                    inv.setItem(2, availableRanks.get(1));
                    inv.setItem(4, availableRanks.get(2));
                    inv.setItem(6, availableRanks.get(3));
                    inv.setItem(8, availableRanks.get(4));
                    inv.setItem(9, availableRanks.get(5));
                    inv.setItem(11, availableRanks.get(6));
                    inv.setItem(13, availableRanks.get(7));
                    inv.setItem(15, availableRanks.get(8));
                    inv.setItem(17, availableRanks.get(9));
                    inv.setItem(18, availableRanks.get(10));
                    inv.setItem(20, availableRanks.get(11));
                    inv.setItem(22, availableRanks.get(12));
                    inv.setItem(24, availableRanks.get(13));
                    inv.setItem(26, availableRanks.get(14));
                    break;
                case 16:
                    inv.setItem(0, availableRanks.get(0));
                    inv.setItem(2, availableRanks.get(1));
                    inv.setItem(4, availableRanks.get(2));
                    inv.setItem(6, availableRanks.get(3));
                    inv.setItem(8, availableRanks.get(4));
                    inv.setItem(9, availableRanks.get(5));
                    inv.setItem(11, availableRanks.get(6));
                    inv.setItem(12, availableRanks.get(7));
                    inv.setItem(14, availableRanks.get(8));
                    inv.setItem(15, availableRanks.get(9));
                    inv.setItem(17, availableRanks.get(10));
                    inv.setItem(18, availableRanks.get(11));
                    inv.setItem(20, availableRanks.get(12));
                    inv.setItem(22, availableRanks.get(13));
                    inv.setItem(24, availableRanks.get(14));
                    inv.setItem(26, availableRanks.get(15));
                    break;
                case 17:
                    inv.setItem(0, availableRanks.get(0));
                    inv.setItem(2, availableRanks.get(1));
                    inv.setItem(4, availableRanks.get(2));
                    inv.setItem(6, availableRanks.get(3));
                    inv.setItem(8, availableRanks.get(4));
                    inv.setItem(9, availableRanks.get(5));
                    inv.setItem(10, availableRanks.get(6));
                    inv.setItem(12, availableRanks.get(7));
                    inv.setItem(13, availableRanks.get(8));
                    inv.setItem(14, availableRanks.get(9));
                    inv.setItem(16, availableRanks.get(10));
                    inv.setItem(17, availableRanks.get(11));
                    inv.setItem(18, availableRanks.get(12));
                    inv.setItem(20, availableRanks.get(13));
                    inv.setItem(22, availableRanks.get(14));
                    inv.setItem(24, availableRanks.get(15));
                    inv.setItem(26, availableRanks.get(16));
                    break;
                case 18:
                    inv.setItem(0, availableRanks.get(0));
                    inv.setItem(2, availableRanks.get(1));
                    inv.setItem(4, availableRanks.get(2));
                    inv.setItem(6, availableRanks.get(3));
                    inv.setItem(8, availableRanks.get(4));
                    inv.setItem(9, availableRanks.get(5));
                    inv.setItem(10, availableRanks.get(6));
                    inv.setItem(11, availableRanks.get(7));
                    inv.setItem(12, availableRanks.get(8));
                    inv.setItem(14, availableRanks.get(9));
                    inv.setItem(15, availableRanks.get(10));
                    inv.setItem(16, availableRanks.get(11));
                    inv.setItem(17, availableRanks.get(12));
                    inv.setItem(18, availableRanks.get(13));
                    inv.setItem(20, availableRanks.get(14));
                    inv.setItem(22, availableRanks.get(15));
                    inv.setItem(24, availableRanks.get(16));
                    inv.setItem(26, availableRanks.get(17));
                    break;
                default:
                    inv.setItem(
                            13,
                            new ItemBuilder(
                                    Material.valueOf(VERSION_13_OR_LATER
                                            ? "RED_STAINED_GLASS"
                                            : "GLASS"
                                    ),
                                    1,
                                    VERSION_13_OR_LATER ? 0 : 14
                            )
                                    .setDisplayName(guiYamlFile.getConfigString(player, "RankedNickGUI.Step1.NoRankAvailable.DisplayName"))
                                    .build()
                    );
                    break;
            }

            player.openInventory(inv);
        } else if(args.length == 1) {
            if(this.setupYamlFile.getConfiguration().getBoolean("Settings.ChangeOptions.Skin")) {
                Inventory inv = Bukkit.createInventory(
                        null,
                        27,
                        guiYamlFile.getConfigString(player, "RankedNickGUI.Step2.InventoryTitle")
                );

                for (int i = 0; i < inv.getSize(); i++)
                    inv.setItem(
                            i,
                            new ItemBuilder(
                                    Material.getMaterial(VERSION_13_OR_LATER
                                            ? "BLACK_STAINED_GLASS_PANE"
                                            : "STAINED_GLASS_PANE"
                                    ),
                                    1,
                                    VERSION_13_OR_LATER ? 0 : 15
                            )
                                    .setDisplayName("§r")
                                    .build()
                    );

                inv.setItem(
                        10,
                        new ItemBuilder(1)
                                .setDisplayName(guiYamlFile.getConfigString(player, "RankedNickGUI.Step2.Default.DisplayName"))
                                .setSkullOwner(player.getName())
                                .build()
                );
                inv.setItem(
                        12,
                        new ItemBuilder(1)
                                .setDisplayName(guiYamlFile.getConfigString(player, "RankedNickGUI.Step2.Normal.DisplayName"))
                                .build()
                );
                inv.setItem(
                        14,
                        new ItemBuilder(1)
                                .setDisplayName(guiYamlFile.getConfigString(player, "RankedNickGUI.Step2.Random.DisplayName"))
                                .setSkullOwner("MHF_Question")
                                .build()
                );
                inv.setItem(
                        16,
                        new ItemBuilder(1)
                                .setDisplayName(guiYamlFile.getConfigString(player, "RankedNickGUI.Step2.SkinFromName.DisplayName"))
                                .setSkullOwner("Steve")
                                .build()
                );

                player.openInventory(inv);
            } else
                this.openRankedNickGUI(player, text + " DEFAULT");
        } else if(args.length == 2) {
            if(player.hasPermission("eazynick.nick.custom")) {
                Inventory inv = Bukkit.createInventory(
                        null,
                        27,
                        guiYamlFile.getConfigString(player, "RankedNickGUI.Step3.InventoryTitle")
                );

                for (int i = 0; i < inv.getSize(); i++) {
                    inv.setItem(i, new ItemBuilder(
                            Material.getMaterial(VERSION_13_OR_LATER ? "BLACK_STAINED_GLASS_PANE" : "STAINED_GLASS_PANE"),
                            1,
                            VERSION_13_OR_LATER ? 0 : 15
                    ).setDisplayName("§r").build());
                }

                inv.setItem(
                        12,
                        new ItemBuilder(Material.valueOf(
                                VERSION_13_OR_LATER && !NMS_VERSION.startsWith("v1_13")
                                        ? "OAK_SIGN"
                                        : "SIGN"
                        ))
                                .setDisplayName(guiYamlFile.getConfigString(player, "RankedNickGUI.Step3.Custom.DisplayName"))
                                .build()
                );
                inv.setItem(
                        14,
                        new ItemBuilder(1)
                                .setDisplayName(guiYamlFile.getConfigString(player, "RankedNickGUI.Step3.Random.DisplayName"))
                                .setSkullOwner("MHF_Question")
                                .build()
                );

                player.openInventory(inv);
            } else
                this.openRankedNickGUI(player, text + " RANDOM");
        } else {
            Inventory inv = Bukkit.createInventory(
                    null,
                    27,
                    guiYamlFile.getConfigString(player, "RankedNickGUI.Step4.InventoryTitle")
                            .replace("%nickName%", args[2])
                            .replace("%nickname%", args[2])
            );

            for (int i = 0; i < inv.getSize(); i++) {
                inv.setItem(
                        i,
                        new ItemBuilder(
                                Material.getMaterial(VERSION_13_OR_LATER
                                        ? "BLACK_STAINED_GLASS_PANE"
                                        : "STAINED_GLASS_PANE"
                                ),
                                1,
                                VERSION_13_OR_LATER ? 0 : 15
                        )
                                .setDisplayName("§r")
                                .build()
                );
            }

            inv.setItem(
                    11,
                    new ItemBuilder(
                            Material.valueOf(VERSION_13_OR_LATER
                                    ? "LIME_WOOL"
                                    : "WOOL"
                            ),
                            1,
                            VERSION_13_OR_LATER ? 0 : 5
                    )
                            .setDisplayName(guiYamlFile.getConfigString(player, "RankedNickGUI.Step4.Use.DisplayName"))
                            .build()
            );
            inv.setItem(
                    13,
                    new ItemBuilder(1)
                            .setDisplayName(guiYamlFile.getConfigString(player, "RankedNickGUI.Step4.Retry.DisplayName"))
                            .build()
            );
            inv.setItem(
                    15,
                    new ItemBuilder(Material.valueOf(
                            VERSION_13_OR_LATER && !NMS_VERSION.startsWith("v1_13")
                                    ? "OAK_SIGN"
                                    : "SIGN"
                    ))
                            .setDisplayName(guiYamlFile.getConfigString(player, "RankedNickGUI.Step4.Custom.DisplayName"))
                            .build()
            );

            player.openInventory(inv);
        }
    }

}
