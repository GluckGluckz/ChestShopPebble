package me.deadlight.ezchestshop.guis;

import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import me.deadlight.ezchestshop.data.Config;
import me.deadlight.ezchestshop.data.gui.ContainerGui;
import me.deadlight.ezchestshop.data.gui.ContainerGuiItem;
import me.deadlight.ezchestshop.data.gui.GuiData;
import me.deadlight.ezchestshop.data.LanguageManager;
import me.deadlight.ezchestshop.data.ShopContainer;
import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.utils.objects.EzShop;
import me.deadlight.ezchestshop.utils.ShopItemUtils;
import me.deadlight.ezchestshop.utils.SignMenuFactory;
import me.deadlight.ezchestshop.utils.Utils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class AdminShopGUI {
    public AdminShopGUI() {

    }

    public void showGUI(Player player, PersistentDataContainer data, Block containerBlock) {
        LanguageManager lm = new LanguageManager();
        OfflinePlayer offlinePlayerOwner = Bukkit.getOfflinePlayer(UUID
                .fromString(data.get(new NamespacedKey(EzChestShop.getPlugin(), "owner"), PersistentDataType.STRING)));
        String shopOwner = offlinePlayerOwner.getName();
        if (shopOwner == null) {
            boolean result = Utils.reInstallNamespacedKeyValues(data, containerBlock.getLocation());
            if (!result) {
                player.sendMessage(lm.chestShopProblem());
                return;
            }
            containerBlock.getState().update();
            EzShop shop = ShopContainer.getShop(containerBlock.getLocation());
            shopOwner = Bukkit.getOfflinePlayer(shop.getOwnerID()).getName();
            if (shopOwner == null) {
                player.sendMessage(lm.chestShopProblem());
                System.out.println(
                        "EzChestShop ERROR: Shop owner is STILL null. Please report this to the EzChestShop developer for furthur investigation.");
                return;
            }
        }
        double sellPrice = data.get(new NamespacedKey(EzChestShop.getPlugin(), "sell"), PersistentDataType.DOUBLE);
        double buyPrice = data.get(new NamespacedKey(EzChestShop.getPlugin(), "buy"), PersistentDataType.DOUBLE);
        boolean disabledBuy = data.get(new NamespacedKey(EzChestShop.getPlugin(), "dbuy"),
                PersistentDataType.INTEGER) == 1;
        boolean disabledSell = data.get(new NamespacedKey(EzChestShop.getPlugin(), "dsell"),
                PersistentDataType.INTEGER) == 1;
        boolean emptyShopItem = ShopItemUtils.isEmptyShopItem(data);

        ContainerGui container = GuiData.getShop();

        Gui gui = new Gui(container.getRows(), lm.guiAdminTitle(shopOwner));
        gui.getFiller().fill(container.getBackground());

        ItemStack mainitem = ShopItemUtils.getShopItem(data);
        if (container.hasItem("shop-item")) {
            ItemStack guiMainItem = emptyShopItem ? ShopItemUtils.emptyShopItem() : mainitem.clone();
            ItemMeta mainmeta = guiMainItem.getItemMeta();
            if (mainmeta != null) {
                if (emptyShopItem) {
                    mainmeta.setDisplayName(Utils.colorify("&e&lSelect Shop Item"));
                    mainmeta.setLore(Arrays.asList(
                            Utils.colorify("&7This shop is empty and cannot trade yet."),
                            Utils.colorify(""),
                            Utils.colorify("&fPick up the item you want to sell,"),
                            Utils.colorify("&fthen click this slot to select it."),
                            Utils.colorify(""),
                            Utils.colorify("&aAfter selecting an item, open settings"),
                            Utils.colorify("&ato set buy and sell prices.")
                    ));
                } else if (mainmeta.hasLore()) {
                    List<String> prevLore = mainmeta.getLore();
                    prevLore.add("");
                    prevLore.addAll(Arrays.asList(lm.initialBuyPrice(buyPrice), lm.initialSellPrice(sellPrice),
                            Utils.colorify(""), Utils.colorify("&eClick with another item on your cursor to replace it.")));
                    mainmeta.setLore(prevLore);
                } else {
                    mainmeta.setLore(Arrays.asList(lm.initialBuyPrice(buyPrice), lm.initialSellPrice(sellPrice),
                            Utils.colorify(""), Utils.colorify("&eClick with another item on your cursor to replace it.")));
                }
                guiMainItem.setItemMeta(mainmeta);
            }
            GuiItem guiitem = new GuiItem(guiMainItem, event -> {
                event.setCancelled(true);
                ItemStack selected = event.getCursor();
                if (selected == null || selected.getType() == Material.AIR) {
                    player.sendMessage(ChatColor.YELLOW + "Pick up the item you want this shop to trade, then click the shop item slot.");
                    return;
                }
                if (Utils.isShulkerBox(selected.getType()) && Utils.isShulkerBox(containerBlock)) {
                    player.sendMessage(lm.invalidShopItem());
                    return;
                }
                if (ShopItemUtils.setShopItem(containerBlock, selected)) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.25f);
                    player.sendMessage(ChatColor.GREEN + "Shop item set to " + ChatColor.AQUA
                            + ChatColor.stripColor(Utils.getFinalItemName(selected)) + ChatColor.GREEN + ".");
                    PersistentDataContainer refreshedData = ((TileState) containerBlock.getState()).getPersistentDataContainer();
                    showGUI(player, refreshedData, containerBlock);
                } else {
                    player.sendMessage(ChatColor.RED + "PebbleShop could not save that shop item.");
                }
            });
            Utils.addItemIfEnoughSlots(gui, container.getItem("shop-item").getSlot(), guiitem);
        }

        container.getItemKeys().forEach(key -> {
            if (emptyShopItem && (key.startsWith("sell-") || key.startsWith("buy-"))) {
                return;
            }
            if (key.startsWith("sell-")) {
                String amountString = key.split("-")[1];
                int amount = 1;
                if (amountString.equals("all")) {
                    amount = Integer.parseInt(Utils.calculateSellPossibleAmount(
                            Bukkit.getOfflinePlayer(player.getUniqueId()), player.getInventory().getStorageContents(),
                            Utils.getBlockInventory(containerBlock).getStorageContents(), sellPrice, mainitem));
                } else if (amountString.equals("maxStackSize")) {
                    amount = mainitem.getMaxStackSize();
                    container.getItem(key).setAmount(amount);
                } else {
                    try {
                        amount = Integer.parseInt(amountString);
                    } catch (NumberFormatException e) {
                    }
                }

                ContainerGuiItem sellItemStack = container.getItem(key)
                        .setLore(lm.buttonSellXLore(sellPrice * amount, amount)).setName(lm.buttonSellXTitle(amount));

                final int finalAmount = amount;
                GuiItem sellItem = new GuiItem(disablingCheck(sellItemStack.getItem(), disabledSell), event -> {
                    event.setCancelled(true);
                    if (disabledSell) {
                        return;
                    }
                    if (offlinePlayerOwner.getUniqueId().equals(player.getUniqueId())) {
                        player.sendMessage(lm.selfTransaction());
                        return;
                    }
                    ShopContainer.sellItem(containerBlock, sellPrice * finalAmount, finalAmount, mainitem, player,
                            offlinePlayerOwner, data);
                    showGUI(player, data, containerBlock);
                });

                Utils.addItemIfEnoughSlots(gui, sellItemStack.getSlot(), sellItem);
            } else if (key.startsWith("buy-")) {
                String amountString = key.split("-")[1];
                int amount = 1;
                if (amountString.equals("all")) {
                    amount = Integer.parseInt(Utils.calculateBuyPossibleAmount(
                            Bukkit.getOfflinePlayer(player.getUniqueId()), player.getInventory().getStorageContents(),
                            Utils.getBlockInventory(containerBlock).getStorageContents(), buyPrice, mainitem));
                } else if (amountString.equals("maxStackSize")) {
                    amount = mainitem.getMaxStackSize();
                    container.getItem(key).setAmount(amount);
                } else {
                    try {
                        amount = Integer.parseInt(amountString);
                    } catch (NumberFormatException e) {
                    }
                }

                ContainerGuiItem buyItemStack = container.getItem(key)
                        .setLore(lm.buttonBuyXLore(buyPrice * amount, amount)).setName(lm.buttonBuyXTitle(amount));

                final int finalAmount = amount;
                GuiItem buyItem = new GuiItem(disablingCheck(buyItemStack.getItem(), disabledBuy), event -> {
                    event.setCancelled(true);
                    if (disabledBuy) {
                        return;
                    }
                    if (offlinePlayerOwner.getUniqueId().equals(player.getUniqueId())) {
                        player.sendMessage(lm.selfTransaction());
                        return;
                    }
                    ShopContainer.buyItem(containerBlock, buyPrice * finalAmount, finalAmount, mainitem, player,
                            offlinePlayerOwner, data);
                    showGUI(player, data, containerBlock);
                });

                Utils.addItemIfEnoughSlots(gui, buyItemStack.getSlot(), buyItem);
            } else if (key.startsWith("decorative-")) {

                ContainerGuiItem decorativeItemStack = container.getItem(key).setName(Utils.colorify("&d"));

                GuiItem buyItem = new GuiItem(decorativeItemStack.getItem(), event -> {
                    event.setCancelled(true);
                });

                Utils.addItemIfEnoughSlots(gui, decorativeItemStack.getSlot(), buyItem);
            }
        });

        if (container.hasItem("admin-view")) {
            ContainerGuiItem guiStorageItem = container.getItem("admin-view").setName(lm.buttonAdminView());

            GuiItem storageGUI = new GuiItem(guiStorageItem.getItem(), event -> {
                event.setCancelled(true);

                if (!ShopContainer.isShop(containerBlock.getLocation())) {
                    player.sendMessage(lm.chestShopProblem());
                    player.closeInventory();
                    return;
                }

                Inventory lastinv = Utils.getBlockInventory(containerBlock);
                if (lastinv instanceof DoubleChestInventory) {
                    DoubleChest doubleChest = (DoubleChest) lastinv.getHolder();
                    lastinv = doubleChest.getInventory();
                }

                if (player.hasPermission("ecs.admin") || player.hasPermission("ecs.admin.view")) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.5f, 0.5f);
                    player.openInventory(lastinv);
                }
            });

            Utils.addItemIfEnoughSlots(gui, guiStorageItem.getSlot(), storageGUI);
        }

        if (container.hasItem("settings")) {
            ContainerGuiItem settingsItemStack = container.getItem("settings");
            settingsItemStack.setName(lm.settingsButton());
            GuiItem settingsGui = new GuiItem(settingsItemStack.getItem(), event -> {
                event.setCancelled(true);
                SettingsGUI settingsGUI = new SettingsGUI();
                settingsGUI.showGUI(player, containerBlock, false);
                player.playSound(player.getLocation(), Sound.BLOCK_PISTON_EXTEND, 0.5f, 0.5f);
            });
            Utils.addItemIfEnoughSlots(gui, settingsItemStack.getSlot(), settingsGui);
        }

        if (!emptyShopItem && container.hasItem("custom-buy-sell")) {
            List<String> possibleCounts = Utils.calculatePossibleAmount(Bukkit.getOfflinePlayer(player.getUniqueId()),
                    offlinePlayerOwner, player.getInventory().getStorageContents(),
                    Utils.getBlockInventory(containerBlock).getStorageContents(),
                    buyPrice, sellPrice, mainitem);
            ContainerGuiItem customBuySellItemStack = container.getItem("custom-buy-sell")
                    .setName(lm.customAmountSignTitle())
                    .setLore(lm.customAmountSignLore(possibleCounts.get(0), possibleCounts.get(1)));

            GuiItem guiSignItem = new GuiItem(customBuySellItemStack.getItem(), event -> {
                event.setCancelled(true);
                if (offlinePlayerOwner.getUniqueId().equals(player.getUniqueId())) {
                    player.sendMessage(lm.selfTransaction());
                    return;
                }

                if (event.isRightClick()) {
                    if (disabledBuy) {
                        player.sendMessage(lm.disabledBuyingMessage());
                        return;
                    }
                    player.closeInventory();
                    player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
                    SignMenuFactory signMenuFactory = new SignMenuFactory(EzChestShop.getPlugin());
                    SignMenuFactory.Menu menu = signMenuFactory.newMenu(lm.signEditorGuiBuy(possibleCounts.get(0)))
                            .reopenIfFail(false).response((thatplayer, strings) -> {
                                try {
                                    if (strings[0].equalsIgnoreCase("")) {
                                        return false;
                                    }
                                    if (Utils.isInteger(strings[0])) {
                                        int amount = Integer.parseInt(strings[0]);
                                        if (!Utils.amountCheck(amount)) {
                                            player.sendMessage(lm.unsupportedInteger());
                                            return false;
                                        }
                                        EzChestShop.getScheduler().scheduleSyncDelayedTask(
                                                () -> ShopContainer.buyItem(containerBlock, buyPrice * amount, amount,
                                                        mainitem, player, offlinePlayerOwner, data));
                                    } else {
                                        thatplayer.sendMessage(lm.wrongInput());
                                    }

                                } catch (Exception e) {

                                    return false;
                                }
                                return true;
                            });
                    menu.open(player);
                    player.sendMessage(lm.enterTheAmount());

                } else if (event.isLeftClick()) {
                    if (disabledSell) {
                        player.sendMessage(lm.disabledSellingMessage());
                        return;
                    }
                    player.closeInventory();
                    player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
                    SignMenuFactory signMenuFactory = new SignMenuFactory(EzChestShop.getPlugin());
                    SignMenuFactory.Menu menu = signMenuFactory.newMenu(lm.signEditorGuiSell(possibleCounts.get(1)))
                            .reopenIfFail(false).response((thatplayer, strings) -> {
                                try {
                                    if (strings[0].equalsIgnoreCase("")) {
                                        return false;
                                    }
                                    if (Utils.isInteger(strings[0])) {
                                        int amount = Integer.parseInt(strings[0]);
                                        if (!Utils.amountCheck(amount)) {
                                            player.sendMessage(lm.unsupportedInteger());
                                            return false;
                                        }
                                        EzChestShop.getScheduler().scheduleSyncDelayedTask(
                                                () -> ShopContainer.sellItem(containerBlock, sellPrice * amount, amount,
                                                        mainitem, player, offlinePlayerOwner, data));
                                    } else {
                                        thatplayer.sendMessage(lm.wrongInput());
                                    }

                                } catch (Exception e) {
                                    return false;
                                }
                                return true;
                            });
                    menu.open(player);
                    player.sendMessage(lm.enterTheAmount());

                }
            });

            if (Config.settings_custom_amout_transactions) {
                Utils.addItemIfEnoughSlots(gui, customBuySellItemStack.getSlot(), guiSignItem);
            }
        }

        gui.open(player);
    }

    private ItemStack disablingCheck(ItemStack mainItem, boolean disabling) {
        if (disabling) {
            LanguageManager lm = new LanguageManager();
            ItemStack disabledItemStack = new ItemStack(Material.BARRIER, mainItem.getAmount());
            ItemMeta disabledItemMeta = disabledItemStack.getItemMeta();
            disabledItemMeta.setDisplayName(lm.disabledButtonTitle());
            disabledItemMeta.setLore(lm.disabledButtonLore());
            disabledItemStack.setItemMeta(disabledItemMeta);
            return disabledItemStack;
        } else {
            return mainItem;
        }
    }

}
