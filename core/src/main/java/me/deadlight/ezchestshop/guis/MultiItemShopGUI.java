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
import org.bukkit.event.inventory.ClickType;
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
 * A listing represents one exact Bukkit item identity. This means two written
 * books, tools or custom items with different metadata are independently priced.
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

        TileState state = (TileState) containerBlock.getState();
        PersistentDataContainer data = state.getPersistentDataContainer();
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
                gui.addItem(createListingItem(gui, player, containerBlock, data, owner, offer, canManage, adminShop));
            }
        }

        addNavigation(gui);
        addInformation(gui, offers.size(), ShopItemUtils.getOfferCapacity(containerBlock), canManage);

        if (canManage) {
            addManagementControls(gui, player, containerBlock, data);
        } else if (player.hasPermission("ecs.admin.view")) {
            addStorageButton(gui, player, containerBlock, 6, 8);
        }

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
                        "&7Sell: &c$" + price(offer.getSellPrice()),
                        "",
                        "&eClick to enter both prices in chat.",
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
                        "&eClick to toggle."
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
                        "&7Price: &c$" + price(offer.getSellPrice()),
                        "",
                        "&eClick to toggle."
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
                        "&7Pick up the replacement item,",
                        "&7then click this button.",
                        "",
                        "&fThe existing prices are preserved.",
                        "&7Exact duplicate listings are blocked."
                ));
        gui.setItem(2, 7, new GuiItem(replace, event -> {
            event.setCancelled(true);
            ItemStack cursor = event.getCursor();
            if (cursor == null || cursor.getType() == Material.AIR) {
                player.sendMessage(Utils.colorify("&ePick up the replacement item on your cursor first."));
                return;
            }
            if (!ShopItemUtils.replaceOfferItem(containerBlock, offerId, cursor)) {
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
                        "&cClick to continue."
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
                "&7Controls owner transaction alerts.", "&eClick to toggle."), event -> {
            event.setCancelled(true);
            setGlobalFlag(containerBlock, "msgtoggle", !messages);
            ShopSettings settings = ShopContainer.getShopSettings(containerBlock.getLocation());
            if (settings != null) settings.setMsgtoggle(!messages);
            showSettings(player, containerBlock);
        }));

        gui.setItem(2, 8, new GuiItem(toggleItem(
                shareIncome ? Material.LIME_DYE : Material.GRAY_DYE,
                shareIncome ? "&a&lShared Income On" : "&7&lShared Income Off",
                "&7Splits eligible income with shop staff.", "&eClick to toggle."), event -> {
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
                            "&aLeft-click &7to add a player.",
                            "&cRight-click &7to remove a player.",
                            "",
                            "&7Staff can manage listings and stock."
                    ));
            gui.setItem(3, 5, new GuiItem(staff, event -> {
                event.setCancelled(true);
                if (event.isRightClick()) {
                    ChatListener.chatmap.put(player.getUniqueId(), new ChatWaitObject("none", "remove", containerBlock));
                    player.closeInventory();
                    player.sendMessage(Utils.colorify("&eType the player name to remove, or type CANCEL."));
                } else {
                    ChatListener.chatmap.put(player.getUniqueId(), new ChatWaitObject("none", "add", containerBlock));
                    player.closeInventory();
                    player.sendMessage(Utils.colorify("&eType the player name to add, or type CANCEL."));
                }
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

    private GuiItem createListingItem(PaginatedGui gui, Player player, Block containerBlock,
                                      PersistentDataContainer data, OfflinePlayer owner, ShopOffer offer,
                                      boolean canManage, boolean adminShop) {
        ItemStack display = listingDisplay(containerBlock, data, offer, canManage);
        return new GuiItem(display, event -> {
            event.setCancelled(true);
            if (canManage) {
                showOfferEditor(player, containerBlock, offer.getId());
                return;
            }

            ClickType click = event.getClick();
            int amount = (click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT)
                    ? offer.getItem().getMaxStackSize() : 1;

            if (click == ClickType.LEFT || click == ClickType.SHIFT_LEFT) {
                handleBuy(player, containerBlock, data, owner, offer, amount, adminShop);
            } else if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
                handleSell(player, containerBlock, data, owner, offer, amount, adminShop);
            }
        });
    }

    private void handleBuy(Player player, Block containerBlock, PersistentDataContainer data,
                           OfflinePlayer owner, ShopOffer offer, int amount, boolean adminShop) {
        if (flag(data, "dbuy", false)) {
            player.sendMessage(Utils.colorify("&cBuying is disabled for this shop."));
            return;
        }
        if (!offer.isBuyingEnabled() || offer.getBuyPrice() <= 0D) {
            player.sendMessage(Utils.colorify("&cBuying is disabled for this listing."));
            return;
        }
        if (!adminShop && owner != null && owner.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(Utils.colorify("&cYou cannot buy from your own shop."));
            return;
        }

        double total = offer.getBuyPrice() * amount;
        if (adminShop) {
            ShopContainer.buyServerItem(containerBlock, total, amount, player, offer.getItem(), data);
        } else if (owner != null) {
            ShopContainer.buyItem(containerBlock, total, amount, offer.getItem(), player, owner, data);
        }
        refreshLater(player, containerBlock);
    }

    private void handleSell(Player player, Block containerBlock, PersistentDataContainer data,
                            OfflinePlayer owner, ShopOffer offer, int amount, boolean adminShop) {
        if (flag(data, "dsell", false)) {
            player.sendMessage(Utils.colorify("&cSelling is disabled for this shop."));
            return;
        }
        if (!offer.isSellingEnabled() || offer.getSellPrice() <= 0D) {
            player.sendMessage(Utils.colorify("&cSelling is disabled for this listing."));
            return;
        }
        if (!adminShop && owner != null && owner.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(Utils.colorify("&cYou cannot sell to your own shop."));
            return;
        }

        double total = offer.getSellPrice() * amount;
        if (adminShop) {
            ShopContainer.sellServerItem(containerBlock, total, amount, offer.getItem(), player, data);
        } else if (owner != null) {
            ShopContainer.sellItem(containerBlock, total, amount, offer.getItem(), player, owner, data);
        }
        refreshLater(player, containerBlock);
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
        int stock = countSimilar(Utils.getBlockInventory(containerBlock), offer.getItem());

        lore.add(Utils.colorify("&8&m--------------------"));
        lore.add(Utils.colorify("&aBuy: " + ((!globalBuyDisabled && offer.isBuyingEnabled())
                ? "&f$" + price(offer.getBuyPrice()) + " each" : "&cDisabled")));
        lore.add(Utils.colorify("&cSell: " + ((!globalSellDisabled && offer.isSellingEnabled())
                ? "&f$" + price(offer.getSellPrice()) + " each" : "&cDisabled")));
        lore.add(Utils.colorify("&bStock: &f" + stock));
        lore.add("");

        if (managementView) {
            lore.add(Utils.colorify("&eClick &7to edit this listing."));
            lore.add(Utils.colorify("&7Prices and toggles are item-specific."));
        } else {
            lore.add(Utils.colorify("&aLeft-click &7Buy 1"));
            lore.add(Utils.colorify("&aShift-left &7Buy a stack"));
            lore.add(Utils.colorify("&cRight-click &7Sell 1"));
            lore.add(Utils.colorify("&cShift-right &7Sell a stack"));
        }

        meta.setLore(lore);
        display.setItemMeta(meta);
        return display;
    }

    private void addManagementControls(PaginatedGui gui, Player player, Block containerBlock,
                                       PersistentDataContainer data) {
        ItemStack settings = namedItem(Material.COMPARATOR, "&b&lShop Settings",
                Arrays.asList(
                        "&7Global buy/sell switches, alerts,",
                        "&7shared income and shop staff."
                ));
        gui.setItem(6, 3, new GuiItem(settings, event -> {
            event.setCancelled(true);
            showSettings(player, containerBlock);
        }));

        ItemStack add = namedItem(Material.EMERALD_BLOCK, "&a&lAdd Listing",
                Arrays.asList(
                        "&7Pick up an item on your cursor,",
                        "&7then click this button.",
                        "",
                        "&fEach exact item can have its own prices.",
                        "&7Listings: &f" + ShopItemUtils.getOffers(containerBlock).size()
                                + "&7/&f" + ShopItemUtils.getOfferCapacity(containerBlock)
                ));
        gui.setItem(6, 7, new GuiItem(add, event -> {
            event.setCancelled(true);
            ItemStack cursor = event.getCursor();
            if (cursor == null || cursor.getType() == Material.AIR) {
                player.sendMessage(Utils.colorify("&ePick up the item you want to list, then click Add Listing."));
                return;
            }
            if (ShopItemUtils.getOffers(containerBlock).size() >= ShopItemUtils.getOfferCapacity(containerBlock)) {
                player.sendMessage(Utils.colorify("&cThis shop already has as many listings as its container can hold."));
                return;
            }
            if (ShopItemUtils.findMatchingOffer(containerBlock, cursor) != null) {
                player.sendMessage(Utils.colorify("&cThat exact item already has a listing."));
                return;
            }
            ShopOffer offer = ShopItemUtils.addOffer(containerBlock, cursor);
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
                        "&7All listed item types can be stocked together."
                ));
        gui.setItem(row, column, new GuiItem(storage, event -> {
            event.setCancelled(true);
            Inventory inventory = Utils.getBlockInventory(containerBlock);
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
                Arrays.asList("&7View earlier listings."));
        gui.setItem(6, 1, new GuiItem(previous, event -> {
            event.setCancelled(true);
            gui.previous();
        }));

        ItemStack next = namedItem(Material.ARROW, "&eNext Page",
                Arrays.asList("&7View more listings."));
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
                        canManage ? "&fClick a listing to manage it." : "&fUse left/right click to trade."
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
                        "&7Items already in storage are not deleted."
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
                Arrays.asList("&7Keep this listing."));
        gui.setItem(2, 6, new GuiItem(cancel, event -> {
            event.setCancelled(true);
            showOfferEditor(player, containerBlock, offerId);
        }));

        gui.open(player);
    }

    private void refreshLater(Player player, Block containerBlock) {
        EzChestShop.getScheduler().scheduleSyncDelayedTask(() -> {
            if (player.isOnline() && isValidShop(containerBlock)) {
                showGUI(player, containerBlock);
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
            if (stack != null && stack.isSimilar(target)) {
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

    private ItemStack filler() {
        return namedItem(Material.BLACK_STAINED_GLASS_PANE, "&8", new ArrayList<>());
    }

    private ItemStack toggleItem(Material material, String name, String lineOne, String lineTwo) {
        return namedItem(material, name, Arrays.asList(lineOne, lineTwo, "", "&eClick to toggle."));
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
