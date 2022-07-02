package com.justixdev.eazynick.nms.fakegui.book;

import com.justixdev.eazynick.EazyNick;
import com.justixdev.eazynick.nms.ReflectionHelper;
import com.justixdev.eazynick.utilities.AsyncTask;
import com.justixdev.eazynick.utilities.AsyncTask.AsyncRunnable;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class NMSBookUtils extends ReflectionHelper {

	private final EazyNick eazyNick;
	
	public NMSBookUtils(EazyNick eazyNick) {
		this.eazyNick = eazyNick;
	}

	public void open(Player player, ItemStack book) {
		try {
			PlayerInventory playerInventory = player.getInventory();
			String version = eazyNick.getVersion();
			boolean noOffhand = version.startsWith("1_7") || version.startsWith("1_8");
			
			ItemStack hand = noOffhand
					? ((ItemStack) playerInventory.getClass().getMethod("getItemInHand").invoke(playerInventory))
					: playerInventory.getItemInMainHand();
			
			if(noOffhand)
				playerInventory.getClass().getMethod(
						"setItemInHand",
						ItemStack.class
				).invoke(playerInventory, book);
			else
				playerInventory.setItemInMainHand(book);
			
			new AsyncTask(new AsyncRunnable() {
				
				@Override
				public void run() {
					try {
						boolean is1_17 = version.startsWith("1_17"), is1_18 = version.startsWith("1_18"), is1_19 = version.startsWith("1_19");
						Object entityPlayer = player.getClass().getMethod("getHandle").invoke(player);
						Class<?> craftItemStackClass = getCraftClass("inventory.CraftItemStack");
						Object nmsItemStack = craftItemStackClass.getMethod(
								"asNMSCopy",
								ItemStack.class
						).invoke(null, book);
						
						if(!(version.startsWith("1_7")
								|| version.startsWith("1_8"))) {
							Class<?> enumHand = getNMSClass((is1_17 || is1_18 || is1_19) ? "world.EnumHand" : "EnumHand");
							Object mainHand = getField(enumHand, (is1_17 || is1_18 || is1_19) ? "a" : "MAIN_HAND").get(enumHand);
							
							if(Bukkit.getVersion().contains("1.14.4")
									|| version.startsWith("1_15")
									|| version.startsWith("1_16")
									|| is1_17
									|| is1_18
									|| is1_19) {
								Class<?> itemWrittenBook = getNMSClass((is1_17 || is1_18 || is1_19)
										? "world.item.ItemWrittenBook"
										: "ItemWrittenBook");
								
								if ((boolean) itemWrittenBook.getMethod(
										"a",
										getNMSClass(
												(is1_17 || is1_18 || is1_19)
														? "world.item.ItemStack"
														: "ItemStack"
										),
										getNMSClass(
												(is1_17 || is1_18 || is1_19)
														? "commands.CommandListenerWrapper"
														: "CommandListenerWrapper"
										),
										getNMSClass(
												(is1_17 || is1_18 || is1_19)
														? "world.entity.player.EntityHuman"
														: "EntityHuman"
										)
								).invoke(
										itemWrittenBook,
										nmsItemStack,
										entityPlayer
												.getClass()
												.getMethod(is1_19 ? "cU" : (is1_18 ? "cQ" : "getCommandListener"))
												.invoke(entityPlayer), entityPlayer)
								) {
									Object activeContainer = entityPlayer
											.getClass()
											.getField(
													is1_19
															? "bU"
															: (is1_17 || is1_18)
																	? "bV"
																	: "activeContainer"
											).get(entityPlayer);
									
					                activeContainer.getClass().getMethod("c").invoke(activeContainer);
					            }
								
					            Object packet = getNMSClass(
										(is1_17 || is1_18 || is1_19)
												? "network.protocol.game.PacketPlayOutOpenBook"
												: "PacketPlayOutOpenBook"
								).getConstructor(enumHand).newInstance(mainHand);
								Object playerConnection = entityPlayer.getClass().getField(
										(is1_17 || is1_18 || is1_19)
												? "b"
												: "playerConnection"
								).get(entityPlayer);
								playerConnection.getClass().getMethod(
										(is1_18 || is1_19)
												? "a"
												: "sendPacket",
										getNMSClass(
												(is1_17 || is1_18 || is1_19)
														? "network.protocol.Packet"
														: "Packet")
								).invoke(playerConnection, packet);
							} else
								entityPlayer.getClass().getMethod(
										"a",
										getNMSClass("ItemStack"),
										enumHand
								).invoke(entityPlayer, nmsItemStack, mainHand);
						} else {
							Object packet;
							
							if(version.startsWith("1_7"))
								packet = getNMSClass("PacketPlayOutCustomPayload")
										.getConstructor(
												String.class,
												net.minecraft.util.io.netty.buffer.ByteBuf.class
										).newInstance(
												"MC|BOpen",
												net.minecraft.util.io.netty.buffer.Unpooled.EMPTY_BUFFER
										);
							else
								packet = getNMSClass("PacketPlayOutCustomPayload")
										.getConstructor(
												String.class,
												getNMSClass("PacketDataSerializer")
										).newInstance(
												"MC|BOpen",
												getNMSClass("PacketDataSerializer")
														.getConstructor(ByteBuf.class)
														.newInstance(Unpooled.EMPTY_BUFFER)
										);
						
							Object playerConnection = entityPlayer
									.getClass()
									.getField("playerConnection")
									.get(entityPlayer);
							playerConnection.getClass()
									.getMethod("sendPacket", getNMSClass("Packet"))
									.invoke(playerConnection, packet);
						}
					} catch (Exception ex) {
						ex.printStackTrace();
					} finally {
						Bukkit.getScheduler().runTask(eazyNick, () -> {
							if(noOffhand) {
								try {
									playerInventory.getClass().getMethod(
											"setItemInHand",
											ItemStack.class
									).invoke(playerInventory, hand);
								} catch (Exception ex) {
									ex.printStackTrace();
								}
							} else
								playerInventory.setItemInMainHand(hand);
						});
					}
				}
			}, 50L * 2).run();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
}
