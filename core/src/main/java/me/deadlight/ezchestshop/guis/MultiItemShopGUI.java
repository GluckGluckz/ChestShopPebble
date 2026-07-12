package me.deadlight.ezchestshop.guis;

import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import dev.triumphteam.gui.guis.PaginatedGui;
import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.data.ShopContainer;
import me.deadlight.ezchestshop.listeners.ChatListener;
import me.deadlight.ezchestshop.utils.ShopItemUtils;
import me.deadlight.ezchestshop.utils.Utils;
import me.deadlight.ezchestshop.utils.objects.ChatWaitObject;
import me.deadlight.ezchestshop.utils.objects.ShopOffer;
import me.deadlight.ezchestshop.utils.objects.ShopSettings;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
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

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Unified multi-item storefront and management interface.
 *
 * Every visible action uses a normal click/tap. Customer trading opens a
 * dedicated action menu instead of relying on right-click or shift-click, which
 * keeps the complete flow usable through Geyser on Minecraft Bedrock Edition.
 */
public class MultiItemShopGUI {

    private static final int ROWS = 6;
    private static final int PAGE_SIZE = 45;
    private static final DecimalFormat PRICE_FORMAT = new DecimalFormat("#,##0.##");

    public void showGUI(Player player, Block containerBlock) {
        if (!isValidShop(containerBlock)) {
            player.sendMessage(Utils.colorify("&cThat chest is no longer a PebbleShop."));
            player.closeInventory();
            return;
        }

        PersistentDataContainer data = data(containerBlock);
        List<ShopOffer> offers = ShopItemUtils.getOffers(containerBlock);
        OfflinePlayer owner = getOwner(data);
        boolean canManage = canManage(player, data);
        boolean adminShop = flag(data, "adminshop", false);

        String ownerName = owner == null || owner.getName() == null ? "Unknown" : owner.getName();
        String title = canManage
                ? Utils.colorify("&0Manage Shop &8• &d" + ownerName)
                : Utils.colorify("&0PebbleShop &8• &d" + ownerName);

        PaginatedGui gui = Gui.paginated()
                .title(Component.text(ChatColor.stripColor(title)))
                .rows(ROWS)
                .pageSize(PAGE_SIZE)
                .create();
        gui.setDefaultClickAction(event -> event.setCancelled(true));
        gui.getFiller().fillBottom(filler());

        if (offers.isEmpty()) {
            ItemStack empty = canManage ? ShopItemUtils.emptyShopItem() : ShopItemUtils.shopNotReadyItem();
            gui.addItem(new GuiItem(empty, event -> event.setCancelled(true)));
        } else {
            for (ShopOffer offer : offers) {
                gui.addItem(createListingItem(player, containerBlock, data, owner, offer, canManage, adminShop));
            }
        }

        addNavigation(gui);
        addInformation(gui, offers.size(), ShopItemUtils.getOfferCapacity(containerBlock), canManage);

        if (canManage) {
            addManagementControls(gui, player, containerBlock);
        } else if (player.hasPermission("ecs.admin.view")) {
            addStorageButton(gui, player, containerBlock, 6, 8);
        }

        gui.open(player);
    }

    /**
     * Bedrock-safe customer transaction screen. A customer taps the listing once,
     * then taps a dedicated Buy/Sell button. No action depends on right-click,
     * shift-click, or click-type translation.
     */
    public void showTradeMenu(Player player, Block containerBlock, String offerId) {
        if (!isValidShop(containerBlock)) {
            player.sendMessage(Utils.colorify("&cThat shop is no longer available."));
            player.closeInventory();
            return;
        }

        PersistentDataContainer data = data(containerBlock);
        ShopOffer offer = ShopItemUtils.getOffer(containerBlock, offerId);
        if (offer == null) {
            player.sendMessage(Utils.colorify("&cThat listing no longer exists."));
            showGUI(player, containerBlock);
            return;
        }

        OfflinePlayer owner = getOwner(data);
        boolean adminShop = flag(data, "adminshop", false);
        int stackAmount = Math.max(1, offer.getItem().getMaxStackSize());
        String itemName = cleanItemName(offer.getItem());

        Gui gui = new Gui(4, Utils.colorify("&0Trade &8• &d" + itemName));
        gui.setDefaultClickAction(event -> event.setCancelled(true));
        gui.getFiller().fill(filler());

        ItemStack preview = listingDisplay(containerBlock, data, offer, false);
        gui.setItem(1, 5, new GuiItem(preview, event -> event.setCancelled(true)));

        String buyDisabledReason = buyDisabledReason(player, data, owner, offer, adminShop);
        String sellDisabledReason = sellDisabledReason(player, data, owner, offer, adminShop);

        ItemStack buyOne = tradeActionItem(
                buyDisabledReason == null,
                Material.EMERALD,
                "&a&lBuy 1",
                Arrays.asList(
                        "&7Receive: &f1x " + itemName,
                        "&7Total cost: &a$" + price(offer.getBuyPrice()),
                        "",
                        "&eTap to purchase."
                ),
                buyDisabledReason
        );
        gui.setItem(2, 2, new GuiItem(buyOne, event -> {
            event.setCancelled(true);
            handleBuy(player, containerBlock, data(containerBlock), owner, offer, 1, adminShop);
        }));

        ItemStack buyStack = tradeActionItem(
                buyDisabledReason == null,
                Material.EMERALD_BLOCK,
                "&a&lBuy " + stackAmount,
                Arrays.asList(
                        "&7Receive: &f" + stackAmount + "x " + itemName,
                        "&7Total cost: &a$" + price(offer.getBuyPrice() * stackAmount),
                        "",
                        "&eTap to purchase a stack."
                ),
                buyDisabledReason
        );
        gui.setItem(2, 4, new GuiItem(buyStack, event -> {
            event.setCancelled(true);
            handleBuy(player, containerBlock, data(containerBlock), owner, offer, stackAmount, adminShop);
        }));

        ItemStack sellOne = tradeActionItem(
                sellDisabledReason == null,
                Material.GOLD_INGOT,
                "&6&lSell 1",
                Arrays.asList(
                        "&7Give: &f1x " + itemName,
                        "&7You receive: &6$" + price(offer.getSellPrice()),
                        "",
                        "&eTap to sell."
                ),
                sellDisabledReason
        );
        gui.setItem(2, 6, new GuiItem(sellOne, event -> {
            event.setCancelled(true);
            handleSell(player, containerBlock, data(containerBlock), owner, offer, 1, adminShop);
        }));

        ItemStack sellStack = tradeActionItem(
                sellDisabledReason == null,
                Material.GOLD_BLOCK,
                "&6&lSell " + stackAmount,
                Arrays.asList(
                        "&7Give: &f" + stackAmount + "x " + itemName,
                        "&7You receive: &6$" + price(offer.getSellPrice() * stackAmount),
                        "",
                        "&eTap to sell a stack."
                ),
                sellDisabledReason
        );
        gui.setItem(2, 8, new GuiItem(sellStack, event -> {
            event.setCancelled(true);
            handleSell(player, containerBlock, data(containerBlock), owner, offer, stackAmount, adminShop);
        }));

        ItemStack help = namedItem(Material.WRITABLE_BOOK, "&b&lBedrock-Friendly Trading",
                Arrays.asList(
                        "&7Every action has its own button.",
                        "&7Only a normal click or tap is required.",
                        "",
                        "&fNo right-click or shift-click controls."
                ));
        gui.setItem(3, 5, new GuiItem(help, event -> event.setCancelled(true)));

        ItemStack back = namedItem(Material.ARROW, "&e&lBack to Shop",
                Arrays.asList("&7Return to all listings."));
        gui.setItem(4, 1, new GuiItem(back, event -> {
            event.setCancelled(true);
            showGUI(player, containerBlock);
        }));

        ItemStack refresh = namedItem(Material.CLOCK, "&b&lRefresh Listing",
                Arrays.asList("&7Refresh prices and current stock."));
        gui.setItem(4, 9, new GuiItem(refresh, event -> {
            event.setCancelled(true);
            showTradeMenu(player, containerBlock, offerId);
        }));

        gui.open(player);
    }

    public void showOfferEditor(Player player, Block containerBlock, String offerId) {
        if (!isValidShop(containerBlock) || !canManage(player, data(containerBlock))) {
            player.closeInventory();
            return;
        }

        ShopOffer offer = ShopItemUtils.getOffer(containerBlock, offerId);
        if (offer == null) {
            player.sendMessage(Utils.colorify("&cThat listing no longer exists."));
            showGUI(player, containerBlock);
            return;
        }

        Gui gui = new Gui(4, Utils.colorify("&0Edit Listing &8• &d" + cleanItemName(offer.getItem())));
        gui.setDefaultClickAction(event -> event.setCancelled(true));
        gui.getFiller().fill(filler());

        ItemStack preview = listingDisplay(containerBlock, data(containerBlock), offer, true);
        gui.setItem(1, 5, new GuiItem(preview, event -> event.setCancelled(true)));

        ItemStack editPrices = namedItem(Material.EMERALD,
                "&a&lEdit Buy & Sell Prices",
                Arrays.asList(
                        "&7Buy: &a$" + price(offer.getBuyPrice()),
                        "&7Sell: &6$" + price(offer.getSellPrice()),
                        "",
                        "&eTap to enter both prices in chat.",
                        "&7Works for Java and Bedrock players."
                ));
        gui.setItem(2, 3, new GuiItem(editPrices, event -> {
            event.setCancelled(true);
            ChatListener.startOfferPriceEditor(player, containerBlock, offerId);
        }));

        ItemStack buyToggle = namedItem(
                offer.isBuyingEnabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                offer.isBuyingEnabled() ? "&a&lBuying Enabled" : "&7&lBuying Disabled",
                Arrays.asList(
                        "&7Customers " + (offer.isBuyingEnabled() ? "can" : "cannot") + " buy this item.",
                        "&7Price: &a$" + price(offer.getBuyPrice()),
                        "",
                        "&eTap to toggle."
                ));
        gui.setItem(2, 4, new GuiItem(buyToggle, event -> {
            event.setCancelled(true);
            if (!offer.isBuyingEnabled() && offer.getBuyPrice() <= 0D) {
                player.sendMessage(Utils.colorify("&eSet a buy price above zero before enabling buying."));
                return;
            }
            ShopItemUtils.toggleOfferBuying(containerBlock, offerId);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            showOfferEditor(player, containerBlock, offerId);
        }));

        ItemStack sellToggle = namedItem(
                offer.isSellingEnabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                offer.isSellingEnabled() ? "&a&lSelling Enabled" : "&7&lSelling Disabled",
                Arrays.asList(
                        "&7Customers " + (offer.isSellingEnabled() ? "can" : "cannot") + " sell this item.",
                        "&7Price: &6$" + price(offer.getSellPrice()),
                        "",
                        "&eTap to toggle."
                ));
        gui.setItem(2, 6, new GuiItem(sellToggle, event -> {
            event.setCancelled(true);
            if (!offer.isSellingEnabled() && offer.getSellPrice() <= 0D) {
                player.sendMessage(Utils.colorify("&eSet a sell price above zero before enabling selling."));
                return;
            }
            ShopItemUtils.toggleOfferSelling(containerBlock, offerId);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            showOfferEditor(player, containerBlock, offerId);
        }));

        ItemStack replace = namedItem(Material.HOPPER,
                "&b&lReplace Listing Item",
                Arrays.asList(
                        "&7Put the replacement item on your cursor",
                        "&7or hold it in your main hand.",
                        "",
                        "&fExisting prices are preserved.",
                        "&7Exact duplicate listings are blocked.",
                        "",
                        "&eTap to replace."
                ));
        gui.setItem(2, 7, new GuiItem(replace, event -> {
            event.setCancelled(true);
            ItemStack selected = selectedItem(player, event.getCursor());
            if (selected == null) {
                player.sendMessage(Utils.colorify("&ePut the replacement item on your cursor or hold it in your main hand."));
                return;
            }
            if (!ShopItemUtils.replaceOfferItem(containerBlock, offerId, selected)) {
                player.sendMessage(Utils.colorify("&cThat item is invalid or already has a listing."));
                return;
            }
            player.sendMessage(Utils.colorify("&aListing item replaced."));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.25f);
            showOfferEditor(player, containerBlock, offerId);
        }));

        ItemStack back = namedItem(Material.ARROW, "&e&lBack to Listings",
                Arrays.asList("&7Return to the shop manager."));
        gui.setItem(4, 1, new GuiItem(back, event -> {
            event.setCancelled(true);
            showGUI(player, containerBlock);
        }));

        ItemStack remove = namedItem(Material.RED_CONCRETE, "&c&lRemove Listing",
                Arrays.asList(
                        "&7Stops trading this exact item.",
                        "&7Stock remains inside the chest.",
                        "",
                        "&cTap to continue."
                ));
        gui.setItem(4, 9, new GuiItem(remove, event -> {
            event.setCancelled(true);
            showRemoveConfirmation(player, containerBlock, offerId);
        }));

        gui.open(player);
    }

    public void showSettings(Player player, Block containerBlock) {
        if (!isValidShop(containerBlock) || !canManage(player, data(containerBlock))) {
            player.closeInventory();
            return;
        }

        PersistentDataContainer data = data(containerBlock);
        boolean buyDisabled = flag(data, "dbuy", false);
        boolean sellDisabled = flag(data, "dsell", false);
        boolean messages = flag(data, "msgtoggle", true);
        boolean shareIncome = flag(data, "shareincome", false);
        boolean adminShop = flag(data, "adminshop", false);

        Gui gui = new Gui(4, Utils.colorify("&0PebbleShop Settings"));
        gui.setDefaultClickAction(event -> event.setCancelled(true));
        gui.getFiller().fill(filler());

        gui.setItem(2, 2, new GuiItem(toggleItem(
                buyDisabled ? Material.RED_DYE : Material.LIME_DYE,
                buyDisabled ? "&c&lShop Buying Disabled" : "&a&lShop Buying Enabled",
                "&7Global switch for customers buying", "&7any listing from this shop."), event -> {
            event.setCancelled(true);
            setGlobalFlag(containerBlock, "dbuy", !buyDisabled);
            ShopSettings settings = ShopContainer.getShopSettings(containerBlock.getLocation());
            if (settings != null) settings.setDbuy(!buyDisabled);
            showSettings(player, containerBlock);
        }));

        gui.setItem(2, 4, new GuiItem(toggleItem(
                sellDisabled ? Material.RED_DYE : Material.LIME_DYE,
                sellDisabled ? "&c&lShop Selling Disabled" : "&a&lShop Selling Enabled",
                "&7Global switch for customers selling", "&7any listing to this shop."), event -> {
            event.setCancelled(true);
            setGlobalFlag(containerBlock, "dsell", !sellDisabled);
            ShopSettings settings = ShopContainer.getShopSettings(containerBlock.getLocation());
            if (settings != null) settings.setDsell(!sellDisabled);
            showSettings(player, containerBlock);
        }));

        gui.setItem(2, 6, new GuiItem(toggleItem(
                messages ? Material.LIME_DYE : Material.GRAY_DYE,
                messages ? "&a&lTransaction Messages On" : "&7&lTransaction Messages Off",
                "&7Controls owner transaction alerts.", "&eTap to toggle."), event -> {
            event.setCancelled(true);
            setGlobalFlag(containerBlock, "msgtoggle", !messages);
            ShopSettings settings = ShopContainer.getShopSettings(containerBlock.getLocation());
            if (settings != null) settings.setMsgtoggle(!messages);
            showSettings(player, containerBlock);
        }));

        gui.setItem(2, 8, new GuiItem(toggleItem(
                shareIncome ? Material.LIME_DYE : Material.GRAY_DYE,
                shareIncome ? "&a&lShared Income On" : "&7&lShared Income Off",
                "&7Splits eligible income with shop staff.", "&eTap to toggle."), event -> {
            event.setCancelled(true);
            if (adminShop) {
                player.sendMessage(Utils.colorify("&cShared income is not used by admin shops."));
                return;
            }
            setGlobalFlag(containerBlock, "shareincome", !shareIncome);
            ShopSettings settings = ShopContainer.getShopSettings(containerBlock.getLocation());
            if (settings != null) settings.setShareincome(!shareIncome);
            showSettings(player, containerBlock);
        }));

        if (!adminShop) {
            ItemStack staff = namedItem(Material.PLAYER_HEAD, "&b&lManage Shop Staff",
                    Arrays.asList(
                            "&7Add and remove staff from separate buttons.",
                            "&7Staff can manage listings and stock.",
                            "",
                            "&eTap to open staff controls.",
                            "&8Bedrock-safe: no right-click required."
                    ));
            gui.setItem(3, 5, new GuiItem(staff, event -> {
                event.setCancelled(true);
                showStaffManager(player, containerBlock);
            }));
        }

        ItemStack back = namedItem(Material.ARROW, "&e&lBack to Listings",
                Arrays.asList("&7Return to the shop manager."));
        gui.setItem(4, 1, new GuiItem(back, event -> {
            event.setCancelled(true);
            showGUI(player, containerBlock);
        }));

        gui.open(player);
    }

    public void showStaffManager(Player player, Block containerBlock) {
        if (!isValidShop(containerBlock) || !canManage(player, data(containerBlock))) {
            player.closeInventory();
            return;
        }

        PersistentDataContainer data = data(containerBlock);
        if (flag(data, "adminshop", false)) {
            player.sendMessage(Utils.colorify("&cAdmin shops do not use player shop staff."));
            showSettings(player, containerBlock);
            return;
        }

        Gui gui = new Gui(3, Utils.colorify("&0Manage Shop Staff"));
        gui.setDefaultClickAction(event -> event.setCancelled(true));
        gui.getFiller().fill(filler());

        ItemStack current = namedItem(Material.BOOK, "&b&lCurrent Shop Staff", staffLore(data));
        gui.setItem(1, 5, new GuiItem(current, event -> event.setCancelled(true)));

        ItemStack add = namedItem(Material.LIME_CONCRETE, "&a&lAdd Staff Member",
                Arrays.asList(
                        "&7Enter a player's name in chat.",
                        "&7They will be able to manage listings",
                        "&7and open the shared shop storage.",
                        "",
                        "&eTap to add a player."
                ));
        gui.setItem(2, 3, new GuiItem(add, event -> {
            event.setCancelled(true);
            beginStaffUpdate(player, containerBlock, "add");
        }));

        ItemStack remove = namedItem(Material.RED_CONCRETE, "&c&lRemove Staff Member",
                Arrays.asList(
                        "&7Enter a player's name in chat.",
                        "&7Their shop-management access is removed.",
                        "",
                        "&eTap to remove a player."
                ));
        gui.setItem(2, 7, new GuiItem(remove, event -> {
            event.setCancelled(true);
            beginStaffUpdate(player, containerBlock, "remove");
        }));

        ItemStack back = namedItem(Material.ARROW, "&e&lBack to Settings",
                Arrays.asList("&7Return to shop settings."));
        gui.setItem(3, 1, new GuiItem(back, event -> {
            event.setCancelled(true);
            showSettings(player, containerBlock);
        }));

        gui.open(player);
    }

    private GuiItem createListingItem(Player player, Block containerBlock,
                                      PersistentDataContainer data, OfflinePlayer owner, ShopOffer offer,
                                      boolean canManage, boolean adminShop) {
        ItemStack display = listingDisplay(containerBlock, data, offer, canManage);
        return new GuiItem(display, event -> {
            event.setCancelled(true);
            if (canManage) {
                showOfferEditor(player, containerBlock, offer.getId());
            } else {
                showTradeMenu(player, containerBlock, offer.getId());
            }
        });
    }

    private void handleBuy(Player player, Block containerBlock, PersistentDataContainer data,
                           OfflinePlayer owner, ShopOffer offer, int amount, boolean adminShop) {
        String disabledReason = buyDisabledReason(player, data, owner, offer, adminShop);
        if (disabledReason != null) {
            player.sendMessage(Utils.colorify("&c" + disabledReason));
            return;
        }

        double total = offer.getBuyPrice() * amount;
        if (adminShop) {
            ShopContainer.buyServerItem(containerBlock, total, amount, player, offer.getItem(), data);
        } else if (owner != null) {
            ShopContainer.buyItem(containerBlock, total, amount, offer.getItem(), player, owner, data);
        }
        refreshTradeLater(player, containerBlock, offer.getId());
    }

    private void handleSell(Player player, Block containerBlock, PersistentDataContainer data,
                            OfflinePlayer owner, ShopOffer offer, int amount, boolean adminShop) {
        String disabledReason = sellDisabledReason(player, data, owner, offer, adminShop);
        if (disabledReason != null) {
            player.sendMessage(Utils.colorify("&c" + disabledReason));
            return;
        }

        double total = offer.getSellPrice() * amount;
        if (adminShop) {
            ShopContainer.sellServerItem(containerBlock, total, amount, offer.getItem(), player, data);
        } else if (owner != null) {
            ShopContainer.sellItem(containerBlock, total, amount, offer.getItem(), player, owner, data);
        }
        refreshTradeLater(player, containerBlock, offer.getId());
    }

    private String buyDisabledReason(Player player, PersistentDataContainer data, OfflinePlayer owner,
                                     ShopOffer offer, boolean adminShop) {
        if (flag(data, "dbuy", false)) {
            return "Buying is disabled for this shop.";
        }
        if (!offer.isBuyingEnabled() || offer.getBuyPrice() <= 0D) {
            return "Buying is disabled for this listing.";
        }
        if (!adminShop && owner == null) {
            return "The shop owner could not be resolved.";
        }
        if (!adminShop && owner.getUniqueId().equals(player.getUniqueId())) {
            return "You cannot buy from your own shop.";
        }
        return null;
    }

    private String sellDisabledReason(Player player, PersistentDataContainer data, OfflinePlayer owner,
                                      ShopOffer offer, boolean adminShop) {
        if (flag(data, "dsell", false)) {
            return "Selling is disabled for this shop.";
        }
        if (!offer.isSellingEnabled() || offer.getSellPrice() <= 0D) {
            return "Selling is disabled for this listing.";
        }
        if (!adminShop && owner == null) {
            return "The shop owner could not be resolved.";
        }
        if (!adminShop && owner.getUniqueId().equals(player.getUniqueId())) {
            return "You cannot sell to your own shop.";
        }
        return null;
    }

    private ItemStack listingDisplay(Block containerBlock, PersistentDataContainer data,
                                     ShopOffer offer, boolean managementView) {
        ItemStack display = offer.getItem();
        display.setAmount(1);
        ItemMeta meta = display.getItemMeta();
        if (meta == null) {
            return display;
        }

        List<String> lore = meta.hasLore() && meta.getLore() != null
                ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        if (!lore.isEmpty()) lore.add("");

        boolean globalBuyDisabled = flag(data, "dbuy", false);
        boolean globalSellDisabled = flag(data, "dsell", false);
        boolean adminShop = flag(data, "adminshop", false);
        int stock = countSimilar(Utils.getBlockInventory(containerBlock), offer.getItem());

        lore.add(Utils.colorify("&8&m--------------------"));
        lore.add(Utils.colorify("&aBuy: " + ((!globalBuyDisabled && offer.isBuyingEnabled())
                ? "&f$" + price(offer.getBuyPrice()) + " each" : "&cDisabled")));
        lore.add(Utils.colorify("&6Sell: " + ((!globalSellDisabled && offer.isSellingEnabled())
                ? "&f$" + price(offer.getSellPrice()) + " each" : "&cDisabled")));
        lore.add(Utils.colorify("&bStock: &f" + (adminShop ? "Unlimited" : stock)));
        lore.add("");

        if (managementView) {
            lore.add(Utils.colorify("&eTap &7to manage this listing."));
            lore.add(Utils.colorify("&7Prices and toggles are item-specific."));
        } else {
            lore.add(Utils.colorify("&eTap &7to open the trade menu."));
            lore.add(Utils.colorify("&7Choose Buy or Sell from separate buttons."));
            lore.add(Utils.colorify("&8No right-click required."));
        }

        meta.setLore(lore);
        display.setItemMeta(meta);
        return display;
    }

    private void addManagementControls(PaginatedGui gui, Player player, Block containerBlock) {
        ItemStack settings = namedItem(Material.COMPARATOR, "&b&lShop Settings",
                Arrays.asList(
                        "&7Global buy/sell switches, alerts,",
                        "&7shared income and shop staff.",
                        "",
                        "&eTap to open settings."
                ));
        gui.setItem(6, 3, new GuiItem(settings, event -> {
            event.setCancelled(true);
            showSettings(player, containerBlock);
        }));

        ItemStack add = namedItem(Material.EMERALD_BLOCK, "&a&lAdd Listing",
                Arrays.asList(
                        "&7Put an item on your cursor or",
                        "&7hold it in your main hand.",
                        "",
                        "&fEach exact item can have its own prices.",
                        "&7Listings: &f" + ShopItemUtils.getOffers(containerBlock).size()
                                + "&7/&f" + ShopItemUtils.getOfferCapacity(containerBlock),
                        "",
                        "&eTap to add the item."
                ));
        gui.setItem(6, 7, new GuiItem(add, event -> {
            event.setCancelled(true);
            ItemStack selected = selectedItem(player, event.getCursor());
            if (selected == null) {
                player.sendMessage(Utils.colorify("&ePut the item on your cursor or hold it in your main hand."));
                return;
            }
            if (ShopItemUtils.getOffers(containerBlock).size() >= ShopItemUtils.getOfferCapacity(containerBlock)) {
                player.sendMessage(Utils.colorify("&cThis shop already has as many listings as its container can hold."));
                return;
            }
            if (ShopItemUtils.findMatchingOffer(containerBlock, selected) != null) {
                player.sendMessage(Utils.colorify("&cThat exact item already has a listing."));
                return;
            }
            ShopOffer offer = ShopItemUtils.addOffer(containerBlock, selected);
            if (offer == null) {
                player.sendMessage(Utils.colorify("&cPebbleShop could not add that item."));
                return;
            }
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.25f);
            player.sendMessage(Utils.colorify("&aListing added. Now set its individual prices."));
            ChatListener.startOfferPriceEditor(player, containerBlock, offer.getId());
        }));

        addStorageButton(gui, player, containerBlock, 6, 8);
    }

    private void addStorageButton(PaginatedGui gui, Player player, Block containerBlock, int row, int column) {
        ItemStack storage = namedItem(Material.CHEST, "&6&lOpen Shop Storage",
                Arrays.asList(
                        "&7Open the real container inventory.",
                        "&7All listed item types can be stocked together.",
                        "",
                        "&eTap to open storage."
                ));
        gui.setItem(row, column, new GuiItem(storage, event -> {
            event.setCancelled(true);
            Inventory inventory = Utils.getBlockInventory(containerBlock);
            if (inventory == null) {
                player.sendMessage(Utils.colorify("&cThe shop storage could not be opened."));
                return;
            }
            if (inventory instanceof DoubleChestInventory) {
                DoubleChest doubleChest = (DoubleChest) inventory.getHolder();
                if (doubleChest != null) inventory = doubleChest.getInventory();
            }
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.6f, 1.1f);
            player.openInventory(inventory);
        }));
    }

    private void addNavigation(PaginatedGui gui) {
        ItemStack previous = namedItem(Material.ARROW, "&ePrevious Page",
                Arrays.asList("&7View earlier listings.", "", "&eTap to navigate."));
        gui.setItem(6, 1, new GuiItem(previous, event -> {
            event.setCancelled(true);
            gui.previous();
        }));

        ItemStack next = namedItem(Material.ARROW, "&eNext Page",
                Arrays.asList("&7View more listings.", "", "&eTap to navigate."));
        gui.setItem(6, 9, new GuiItem(next, event -> {
            event.setCancelled(true);
            gui.next();
        }));
    }

    private void addInformation(PaginatedGui gui, int listings, int capacity, boolean canManage) {
        ItemStack info = namedItem(Material.BOOK, "&d&lMulti-Item PebbleShop",
                Arrays.asList(
                        "&7Listings: &f" + listings + "&7/&f" + capacity,
                        "&7Every listing has independent prices.",
                        "&7Custom names and metadata stay distinct.",
                        "",
                        canManage
                                ? "&fTap a listing to manage it."
                                : "&fTap a listing to choose Buy or Sell.",
                        "&8All controls work with normal taps."
                ));
        gui.setItem(6, 5, new GuiItem(info, event -> event.setCancelled(true)));
    }

    private void showRemoveConfirmation(Player player, Block containerBlock, String offerId) {
        ShopOffer offer = ShopItemUtils.getOffer(containerBlock, offerId);
        if (offer == null) {
            showGUI(player, containerBlock);
            return;
        }

        Gui gui = new Gui(3, Utils.colorify("&0Remove Listing?"));
        gui.setDefaultClickAction(event -> event.setCancelled(true));
        gui.getFiller().fill(filler());

        ItemStack confirm = namedItem(Material.LIME_CONCRETE, "&a&lConfirm Removal",
                Arrays.asList(
                        "&7Remove the listing for:",
                        "&f" + cleanItemName(offer.getItem()),
                        "",
                        "&7Items already in storage are not deleted.",
                        "",
                        "&eTap to confirm."
                ));
        gui.setItem(2, 4, new GuiItem(confirm, event -> {
            event.setCancelled(true);
            if (ShopItemUtils.removeOffer(containerBlock, offerId)) {
                player.sendMessage(Utils.colorify("&aListing removed. Stock was left in the chest."));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
            }
            showGUI(player, containerBlock);
        }));

        ItemStack cancel = namedItem(Material.RED_CONCRETE, "&c&lCancel",
                Arrays.asList("&7Keep this listing.", "", "&eTap to return."));
        gui.setItem(2, 6, new GuiItem(cancel, event -> {
            event.setCancelled(true);
            showOfferEditor(player, containerBlock, offerId);
        }));

        gui.open(player);
    }

    private void beginStaffUpdate(Player player, Block containerBlock, String type) {
        ChatListener.chatmap.put(player.getUniqueId(), new ChatWaitObject("none", type, containerBlock));
        player.closeInventory();
        if ("add".equalsIgnoreCase(type)) {
            player.sendMessage(Utils.colorify("&b&lAdd Shop Staff &d━━━━━━━━━━━━"));
            player.sendMessage(Utils.colorify("&fType the player's exact name in chat."));
        } else {
            player.sendMessage(Utils.colorify("&c&lRemove Shop Staff &d━━━━━━━━━━━━"));
            player.sendMessage(Utils.colorify("&fType the player's exact name in chat."));
        }
        player.sendMessage(Utils.colorify("&7Type &cCANCEL &7to return without changing anything."));
    }

    private List<String> staffLore(PersistentDataContainer data) {
        List<UUID> staff = Utils.getAdminsList(data);
        List<String> lore = new ArrayList<>();
        if (staff.isEmpty()) {
            lore.add(Utils.colorify("&7No staff members are assigned."));
        } else {
            lore.add(Utils.colorify("&7Assigned staff:"));
            for (UUID uuid : staff) {
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
                String name = offlinePlayer.getName();
                lore.add(Utils.colorify("&f• &b" + (name == null ? uuid.toString() : name)));
            }
        }
        lore.add("");
        lore.add(Utils.colorify("&7Use the separate Add and Remove buttons below."));
        return lore;
    }

    private ItemStack selectedItem(Player player, ItemStack cursor) {
        if (cursor != null && cursor.getType() != Material.AIR) {
            ItemStack selected = cursor.clone();
            selected.setAmount(1);
            return selected;
        }

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand != null && mainHand.getType() != Material.AIR) {
            ItemStack selected = mainHand.clone();
            selected.setAmount(1);
            return selected;
        }

        return null;
    }

    private ItemStack tradeActionItem(boolean available, Material material, String name,
                                      List<String> lore, String disabledReason) {
        if (available) {
            return namedItem(material, name, lore);
        }

        List<String> disabledLore = new ArrayList<>();
        disabledLore.add("&7This action is currently unavailable.");
        if (disabledReason != null && !disabledReason.trim().isEmpty()) {
            disabledLore.add("&c" + disabledReason);
        }
        disabledLore.add("");
        disabledLore.add("&7Tap for details.");
        return namedItem(Material.BARRIER, "&c&lUnavailable", disabledLore);
    }

    private void refreshTradeLater(Player player, Block containerBlock, String offerId) {
        EzChestShop.getScheduler().scheduleSyncDelayedTask(() -> {
            if (!player.isOnline() || !isValidShop(containerBlock)) {
                return;
            }
            if (ShopItemUtils.getOffer(containerBlock, offerId) == null) {
                showGUI(player, containerBlock);
            } else {
                showTradeMenu(player, containerBlock, offerId);
            }
        }, 1);
    }

    private boolean isValidShop(Block block) {
        if (block == null || !(block.getState() instanceof TileState)) {
            return false;
        }
        return data(block).has(key("owner"), PersistentDataType.STRING);
    }

    private PersistentDataContainer data(Block block) {
        return ((TileState) block.getState()).getPersistentDataContainer();
    }

    private OfflinePlayer getOwner(PersistentDataContainer data) {
        String ownerId = data.get(key("owner"), PersistentDataType.STRING);
        if (ownerId == null) return null;
        try {
            return Bukkit.getOfflinePlayer(UUID.fromString(ownerId));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean canManage(Player player, PersistentDataContainer data) {
        String ownerId = data.get(key("owner"), PersistentDataType.STRING);
        if (ownerId != null && ownerId.equalsIgnoreCase(player.getUniqueId().toString())) {
            return true;
        }
        if (player.isOp() || player.hasPermission("ecs.admin")) {
            return true;
        }
        return Utils.getAdminsList(data).contains(player.getUniqueId());
    }

    private void setGlobalFlag(Block containerBlock, String name, boolean value) {
        TileState state = (TileState) containerBlock.getState();
        state.getPersistentDataContainer().set(key(name), PersistentDataType.INTEGER, value ? 1 : 0);
        state.update();
    }

    private boolean flag(PersistentDataContainer data, String name, boolean fallback) {
        Integer value = data.get(key(name), PersistentDataType.INTEGER);
        return value == null ? fallback : value == 1;
    }

    private NamespacedKey key(String value) {
        return new NamespacedKey(EzChestShop.getPlugin(), value);
    }

    private int countSimilar(Inventory inventory, ItemStack target) {
        if (inventory == null || target == null) return 0;
        int amount = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack != null && Utils.isSimilar(stack, target)) {
                amount += stack.getAmount();
            }
        }
        return amount;
    }

    private String cleanItemName(ItemStack item) {
        String name = ChatColor.stripColor(Utils.getFinalItemName(item));
        return name == null || name.trim().isEmpty() ? item.getType().name() : name;
    }

    private String price(double amount) {
        synchronized (PRICE_FORMAT) {
            return PRICE_FORMAT.format(amount);
        }
    }

    private GuiItem filler() {
        return new GuiItem(namedItem(Material.BLACK_STAINED_GLASS_PANE, "&8", new ArrayList<>()),
                event -> event.setCancelled(true));
    }

    private ItemStack toggleItem(Material material, String name, String lineOne, String lineTwo) {
        return namedItem(material, name, Arrays.asList(lineOne, lineTwo, "", "&eTap to toggle."));
    }

    private ItemStack namedItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Utils.colorify(name));
            List<String> coloredLore = new ArrayList<>();
            if (lore != null) {
                for (String line : lore) {
                    coloredLore.add(Utils.colorify(line));
                }
            }
            meta.setLore(coloredLore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
