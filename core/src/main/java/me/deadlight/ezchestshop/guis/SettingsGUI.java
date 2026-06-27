package me.deadlight.ezchestshop.guis;

import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.data.Config;
import me.deadlight.ezchestshop.data.LanguageManager;
import me.deadlight.ezchestshop.data.ShopContainer;
import me.deadlight.ezchestshop.data.gui.ContainerGui;
import me.deadlight.ezchestshop.data.gui.ContainerGuiItem;
import me.deadlight.ezchestshop.data.gui.GuiData;
import me.deadlight.ezchestshop.listeners.ChatListener;
import me.deadlight.ezchestshop.utils.Utils;
import me.deadlight.ezchestshop.utils.objects.ChatWaitObject;
import me.deadlight.ezchestshop.utils.objects.EzShop;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.UUID;

public class SettingsGUI {
    public static LanguageManager lm = new LanguageManager();

    public void showGUI(Player player, Block containerBlock, boolean isAdmin) {
        ContainerGui container = GuiData.getSettings();
        PersistentDataContainer dataContainer = ((TileState) containerBlock.getState()).getPersistentDataContainer();
        boolean isAdminShop = dataContainer.get(new NamespacedKey(EzChestShop.getPlugin(), "adminshop"), PersistentDataType.INTEGER) == 1;

        Gui gui = new Gui(container.getRows(), lm.settingsGuiTitle());
        gui.getFiller().fill(container.getBackground());

        if (container.hasItem("toggle-transaction-message-on") && container.hasItem("toggle-transaction-message-off")) {
            boolean isToggleMessageOn = dataContainer.get(new NamespacedKey(EzChestShop.getPlugin(), "msgtoggle"), PersistentDataType.INTEGER) == 1;
            ContainerGuiItem messageToggleItem = container.getItem(isToggleMessageOn ? "toggle-transaction-message-on" : "toggle-transaction-message-off")
                    .setName(lm.toggleTransactionMessageButton()).setLore(toggleMessageChooser(isToggleMessageOn, lm));
            GuiItem messageToggle = new GuiItem(messageToggleItem.getItem(), event -> {
                event.setCancelled(true);
                boolean currentlyOn = dataContainer.get(new NamespacedKey(EzChestShop.getPlugin(), "msgtoggle"), PersistentDataType.INTEGER) == 1;
                TileState state = ((TileState) containerBlock.getState());
                state.getPersistentDataContainer().set(new NamespacedKey(EzChestShop.getPlugin(), "msgtoggle"), PersistentDataType.INTEGER, currentlyOn ? 0 : 1);
                state.update();
                player.sendMessage(currentlyOn ? lm.toggleTransactionMessageOffInChat() : lm.toggleTransactionMessageOnInChat());
                ShopContainer.getShopSettings(containerBlock.getLocation()).setMsgtoggle(!currentlyOn);
                showGUI(player, containerBlock, isAdmin);
            });
            Utils.addItemIfEnoughSlots(gui, messageToggleItem.getSlot(), messageToggle);
        }

        if (container.hasItem("disable-buy-on") && container.hasItem("disable-buy-off")) {
            boolean isBuyDisabled = dataContainer.get(new NamespacedKey(EzChestShop.getPlugin(), "dbuy"), PersistentDataType.INTEGER) == 1;
            ContainerGuiItem buyDisabledItem = container.getItem(isBuyDisabled ? "disable-buy-on" : "disable-buy-off")
                    .setName(lm.disableBuyingButtonTitle()).setLore(buyMessageChooser(isBuyDisabled, lm));
            GuiItem buyDisabled = new GuiItem(buyDisabledItem.getItem(), event -> {
                event.setCancelled(true);
                boolean currentlyDisabled = dataContainer.get(new NamespacedKey(EzChestShop.getPlugin(), "dbuy"), PersistentDataType.INTEGER) == 1;
                TileState state = ((TileState) containerBlock.getState());
                state.getPersistentDataContainer().set(new NamespacedKey(EzChestShop.getPlugin(), "dbuy"), PersistentDataType.INTEGER, currentlyDisabled ? 0 : 1);
                state.update();
                player.sendMessage(currentlyDisabled ? lm.disableBuyingOffInChat() : lm.disableBuyingOnInChat());
                ShopContainer.getShopSettings(containerBlock.getLocation()).setDbuy(!currentlyDisabled);
                showGUI(player, containerBlock, isAdmin);
            });
            Utils.addItemIfEnoughSlots(gui, buyDisabledItem.getSlot(), buyDisabled);
        }

        if (container.hasItem("disable-sell-on") && container.hasItem("disable-sell-off")) {
            boolean isSellDisabled = dataContainer.get(new NamespacedKey(EzChestShop.getPlugin(), "dsell"), PersistentDataType.INTEGER) == 1;
            ContainerGuiItem sellDisabledItem = container.getItem(isSellDisabled ? "disable-sell-on" : "disable-sell-off")
                    .setName(lm.disableSellingButtonTitle()).setLore(sellMessageChooser(isSellDisabled, lm));
            GuiItem sellDisabled = new GuiItem(sellDisabledItem.getItem(), event -> {
                event.setCancelled(true);
                boolean currentlyDisabled = dataContainer.get(new NamespacedKey(EzChestShop.getPlugin(), "dsell"), PersistentDataType.INTEGER) == 1;
                TileState state = ((TileState) containerBlock.getState());
                state.getPersistentDataContainer().set(new NamespacedKey(EzChestShop.getPlugin(), "dsell"), PersistentDataType.INTEGER, currentlyDisabled ? 0 : 1);
                state.update();
                player.sendMessage(currentlyDisabled ? lm.disableSellingOffInChat() : lm.disableSellingOnInChat());
                ShopContainer.getShopSettings(containerBlock.getLocation()).setDsell(!currentlyDisabled);
                showGUI(player, containerBlock, isAdmin);
            });
            Utils.addItemIfEnoughSlots(gui, sellDisabledItem.getSlot(), sellDisabled);
        }

        if (!isAdmin) {
            boolean hasAtLeastOneAdmin = !dataContainer.get(new NamespacedKey(EzChestShop.getPlugin(), "admins"), PersistentDataType.STRING).equals("none");
            if (container.hasItem("shop-admins")) {
                ContainerGuiItem signItem = container.getItem("shop-admins").setName(lm.shopAdminsButtonTitle()).setLore(signLoreChooser(hasAtLeastOneAdmin, dataContainer, lm));
                GuiItem signItemGui = new GuiItem(signItem.getItem(), event -> {
                    event.setCancelled(true);
                    if (event.getClick() == ClickType.RIGHT) {
                        ChatListener.chatmap.put(player.getUniqueId(), new ChatWaitObject("none", "remove", containerBlock));
                        player.closeInventory();
                        player.sendMessage(lm.removingAdminWaiting());
                    } else {
                        ChatListener.chatmap.put(player.getUniqueId(), new ChatWaitObject("none", "add", containerBlock));
                        player.closeInventory();
                        player.sendMessage(lm.addingAdminWaiting());
                    }
                });
                Utils.addItemIfEnoughSlots(gui, signItem.getSlot(), signItemGui);
            }

            if (container.hasItem("share-income-on") && container.hasItem("share-income-off")) {
                boolean isSharedIncome = dataContainer.get(new NamespacedKey(EzChestShop.getPlugin(), "shareincome"), PersistentDataType.INTEGER) == 1;
                ContainerGuiItem sharedIncomeItem = container.getItem(isSharedIncome ? "share-income-on" : "share-income-off")
                        .setName(lm.shareIncomeButtonTitle()).setLore(shareIncomeLoreChooser(isSharedIncome, lm));
                GuiItem sharedIncome = new GuiItem(sharedIncomeItem.getItem(), event -> {
                    event.setCancelled(true);
                    boolean currentlyShared = dataContainer.get(new NamespacedKey(EzChestShop.getPlugin(), "shareincome"), PersistentDataType.INTEGER) == 1;
                    TileState state = ((TileState) containerBlock.getState());
                    state.getPersistentDataContainer().set(new NamespacedKey(EzChestShop.getPlugin(), "shareincome"), PersistentDataType.INTEGER, currentlyShared ? 0 : 1);
                    state.update();
                    player.sendMessage(currentlyShared ? lm.sharedIncomeOffInChat() : lm.sharedIncomeOnInChat());
                    ShopContainer.getShopSettings(containerBlock.getLocation()).setShareincome(!currentlyShared);
                    showGUI(player, containerBlock, isAdmin);
                });
                if (hasAtLeastOneAdmin && dataContainer.get(new NamespacedKey(EzChestShop.getPlugin(), "adminshop"), PersistentDataType.INTEGER) == 0) {
                    Utils.addItemIfEnoughSlots(gui, sharedIncomeItem.getSlot(), sharedIncome);
                }
            }
        }

        if (container.hasItem("change-price")) {
            ContainerGuiItem priceItemStack = container.getItem("change-price")
                    .setName(lm.changePricesButtonTitle()).setLore(lm.changePricesButtonLore());
            GuiItem priceItem = new GuiItem(priceItemStack.getItem(), event -> {
                event.setCancelled(true);
                player.closeInventory();
                player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
                ChatListener.startPriceEditor(player, containerBlock);
            });
            Utils.addItemIfEnoughSlots(gui, priceItemStack.getSlot(), priceItem);
        }

        if (container.hasItem("back")) {
            ContainerGuiItem backItemStack = container.getItem("back").setName(lm.backToShopGuiButton());
            GuiItem backItem = new GuiItem(backItemStack.getItem(), event -> {
                event.setCancelled(true);
                String owneruuid = dataContainer.get(new NamespacedKey(EzChestShop.getPlugin(), "owner"), PersistentDataType.STRING);

                if (isAdminShop) {
                    ServerShopGUI serverShopGUI = new ServerShopGUI();
                    serverShopGUI.showGUI(player, dataContainer, containerBlock);
                    return;
                }

                if (player.getUniqueId().toString().equalsIgnoreCase(owneruuid) || isAdmin) {
                    if (player.hasPermission("ecs.admin")) {
                        AdminShopGUI adminShopGUI = new AdminShopGUI();
                        adminShopGUI.showGUI(player, dataContainer, containerBlock);
                    } else {
                        OwnerShopGUI ownerShopGUI = new OwnerShopGUI();
                        ownerShopGUI.showGUI(player, dataContainer, containerBlock, isAdmin);
                    }
                } else if (player.hasPermission("ecs.admin")) {
                    AdminShopGUI adminShopGUI = new AdminShopGUI();
                    adminShopGUI.showGUI(player, dataContainer, containerBlock);
                } else {
                    player.closeInventory();
                }
            });
            Utils.addItemIfEnoughSlots(gui, backItemStack.getSlot(), backItem);
        }

        gui.open(player);
    }

    private List<String> toggleMessageChooser(boolean data, LanguageManager lm) {
        String status = data ? lm.statusOn() : lm.statusOff();
        return lm.toggleTransactionMessageButtonLore(status);
    }

    private List<String> buyMessageChooser(boolean data, LanguageManager lm) {
        String status = data ? lm.statusOn() : lm.statusOff();
        return lm.disableBuyingButtonLore(status);
    }

    private List<String> sellMessageChooser(boolean data, LanguageManager lm) {
        String status = data ? lm.statusOn() : lm.statusOff();
        return lm.disableSellingButtonLore(status);
    }

    private List<String> shareIncomeLoreChooser(boolean data, LanguageManager lm) {
        String status = data ? lm.statusOn() : lm.statusOff();
        return lm.shareIncomeButtonLore(status);
    }

    private List<String> signLoreChooser(boolean data, PersistentDataContainer container, LanguageManager lm) {
        String status;
        if (data) {
            StringBuilder adminsListString = new StringBuilder("&a");
            List<UUID> admins = Utils.getAdminsList(container);
            boolean first = false;
            for (UUID admin : admins) {
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(admin);
                if (first) {
                    adminsListString.append(", ").append(offlinePlayer.getName());
                } else {
                    adminsListString.append(offlinePlayer.getName());
                    first = true;
                }
            }
            status = adminsListString.toString();
        } else {
            status = lm.nobodyStatusAdmins();
        }
        return lm.shopAdminsButtonLore(status);
    }

    public boolean changePrices(Block containerBlock, Player player, double buyPrice, double sellPrice) {
        EzShop shop = ShopContainer.getShop(containerBlock.getLocation());
        if (shop == null) {
            player.sendMessage(lm.chestShopProblem());
            return false;
        }
        if (Config.settings_buy_greater_than_sell && buyPrice != 0 && sellPrice > buyPrice) {
            player.sendMessage(lm.buyGreaterThanSellRequired());
            return false;
        }
        if (!changePrice(containerBlock.getState(), true, buyPrice, player, containerBlock)) {
            return false;
        }
        ShopContainer.changePrice(containerBlock.getState(), buyPrice, true);
        if (!changePrice(containerBlock.getState(), false, sellPrice, player, containerBlock)) {
            return false;
        }
        ShopContainer.changePrice(containerBlock.getState(), sellPrice, false);
        return true;
    }

    public boolean changePrice(BlockState blockState, boolean isBuy, double price, Player player, Block containerBlock) {
        EzShop shop = ShopContainer.getShop(blockState.getLocation());
        if (shop == null) {
            player.sendMessage(lm.chestShopProblem());
            return false;
        }

        if (Config.settings_buy_greater_than_sell) {
            if ((isBuy && shop.getSellPrice() > price && price != 0)
                    || (!isBuy && price > shop.getBuyPrice() && shop.getBuyPrice() != 0)) {
                player.sendMessage(lm.buyGreaterThanSellRequired());
                return false;
            }
        }

        if (Config.settings_zero_equals_disabled && isBuy && shop.getBuyPrice() == 0 && price != 0) {
            TileState state = ((TileState) containerBlock.getState());
            state.getPersistentDataContainer().set(new NamespacedKey(EzChestShop.getPlugin(), "dbuy"), PersistentDataType.INTEGER, 0);
            state.update();
            player.sendMessage(lm.disableBuyingOffInChat());
            ShopContainer.getShopSettings(containerBlock.getLocation()).setDbuy(false);
        }
        if (Config.settings_zero_equals_disabled && !isBuy && shop.getSellPrice() == 0 && price != 0) {
            TileState state = ((TileState) containerBlock.getState());
            state.getPersistentDataContainer().set(new NamespacedKey(EzChestShop.getPlugin(), "dsell"), PersistentDataType.INTEGER, 0);
            state.update();
            player.sendMessage(lm.disableSellingOffInChat());
            ShopContainer.getShopSettings(containerBlock.getLocation()).setDsell(false);
        }

        if (price == 0 && Config.settings_zero_equals_disabled) {
            if (isBuy && shop.getBuyPrice() != 0) {
                TileState state = ((TileState) containerBlock.getState());
                state.getPersistentDataContainer().set(new NamespacedKey(EzChestShop.getPlugin(), "dbuy"), PersistentDataType.INTEGER, 1);
                state.update();
                player.sendMessage(lm.disableBuyingOnInChat());
                ShopContainer.getShopSettings(containerBlock.getLocation()).setDbuy(true);
            }
            if (!isBuy && shop.getSellPrice() != 0) {
                TileState state = ((TileState) containerBlock.getState());
                state.getPersistentDataContainer().set(new NamespacedKey(EzChestShop.getPlugin(), "dsell"), PersistentDataType.INTEGER, 1);
                state.update();
                player.sendMessage(lm.disableSellingOnInChat());
                ShopContainer.getShopSettings(containerBlock.getLocation()).setDsell(true);
            }
        }
        return true;
    }

    public static void openCustomMessageEditor(Player player, org.bukkit.Location location) {
        player.closeInventory();
        player.sendMessage(Utils.colorify("&cPebbleShop hologram messages are disabled because shops now use physical signs."));
    }
}
