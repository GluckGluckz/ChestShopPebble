package me.deadlight.ezchestshop.utils;

import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.data.Config;
import me.deadlight.ezchestshop.data.ShopContainer;
import me.deadlight.ezchestshop.utils.objects.EzShop;
import me.deadlight.ezchestshop.utils.objects.ShopOffer;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.TileState;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Persists and manages the independently priced listings attached to a shop.
 *
 * The original single-item PDC fields are kept in sync with the first listing
 * for backwards compatibility with database rows, commands and inherited code.
 * The complete listing collection is stored in the container PDC as JSON.
 */
public final class ShopItemUtils {

    private static final String OFFERS_KEY = "offers_v2";

    private ShopItemUtils() {
    }

    public static NamespacedKey offersKey() {
        return new NamespacedKey(EzChestShop.getPlugin(), OFFERS_KEY);
    }

    public static NamespacedKey emptyShopItemKey() {
        return new NamespacedKey(EzChestShop.getPlugin(), "empty_shop_item");
    }

    private static NamespacedKey placeholderItemKey() {
        return new NamespacedKey(EzChestShop.getPlugin(), "empty_shop_placeholder");
    }

    private static NamespacedKey key(String value) {
        return new NamespacedKey(EzChestShop.getPlugin(), value);
    }

    public static ItemStack emptyShopItem() {
        ItemStack item = new ItemStack(Material.CHEST, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Utils.colorify("&e&lAdd Shop Listings"));
            meta.setLore(Arrays.asList(
                    Utils.colorify("&7This shop has no listings yet."),
                    Utils.colorify(""),
                    Utils.colorify("&fPick up an item and click"),
                    Utils.colorify("&fthe &aAdd Listing &fbutton."),
                    Utils.colorify(""),
                    Utils.colorify("&7Each item can have its own prices.")
            ));
            meta.getPersistentDataContainer().set(placeholderItemKey(), PersistentDataType.INTEGER, 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack shopNotReadyItem() {
        ItemStack item = new ItemStack(Material.BARRIER, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Utils.colorify("&c&lShop Not Ready"));
            meta.setLore(Arrays.asList(
                    Utils.colorify("&7The owner has not added any"),
                    Utils.colorify("&7item listings to this shop yet."),
                    Utils.colorify(""),
                    Utils.colorify("&fCheck back soon.")
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    public static List<ShopOffer> getOffers(Block containerBlock) {
        if (containerBlock == null || !(containerBlock.getState() instanceof TileState)) {
            return Collections.emptyList();
        }
        PersistentDataContainer data = ((TileState) containerBlock.getState()).getPersistentDataContainer();
        boolean hadOfferPayload = data.has(offersKey(), PersistentDataType.STRING);
        List<ShopOffer> offers = getOffers(data);
        if (!hadOfferPayload && !offers.isEmpty()) {
            saveOffers(containerBlock, offers);
        }
        return offers;
    }

    public static List<ShopOffer> getOffers(PersistentDataContainer data) {
        if (data == null) {
            return new ArrayList<>();
        }

        String payload = data.get(offersKey(), PersistentDataType.STRING);
        if (payload != null && !payload.trim().isEmpty()) {
            List<ShopOffer> parsed = decodeOffers(payload);
            if (parsed != null) {
                return parsed;
            }
        }

        return migrateLegacyOffer(data);
    }

    public static ShopOffer getOffer(Block containerBlock, String offerId) {
        if (offerId == null) {
            return null;
        }
        for (ShopOffer offer : getOffers(containerBlock)) {
            if (offerId.equals(offer.getId())) {
                return offer;
            }
        }
        return null;
    }

    public static ShopOffer findMatchingOffer(Block containerBlock, ItemStack item) {
        for (ShopOffer offer : getOffers(containerBlock)) {
            if (offer.matches(item)) {
                return offer;
            }
        }
        return null;
    }

    public static int getOfferCapacity(Block containerBlock) {
        if (containerBlock == null) {
            return 0;
        }
        try {
            Inventory inventory = Utils.getBlockInventory(containerBlock);
            return inventory == null ? 0 : inventory.getSize();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public static ShopOffer addOffer(Block containerBlock, ItemStack sourceItem) {
        if (!isValidListingItem(containerBlock, sourceItem)) {
            return null;
        }

        List<ShopOffer> offers = getOffers(containerBlock);
        if (offers.size() >= getOfferCapacity(containerBlock)) {
            return null;
        }
        for (ShopOffer offer : offers) {
            if (offer.matches(sourceItem)) {
                return null;
            }
        }

        ShopOffer offer = new ShopOffer(sourceItem, 0D, 0D, false, false);
        offers.add(offer);
        return saveOffers(containerBlock, offers) ? offer : null;
    }

    public static boolean removeOffer(Block containerBlock, String offerId) {
        List<ShopOffer> offers = getOffers(containerBlock);
        boolean removed = false;
        for (int index = offers.size() - 1; index >= 0; index--) {
            if (offers.get(index).getId().equals(offerId)) {
                offers.remove(index);
                removed = true;
            }
        }
        return removed && saveOffers(containerBlock, offers);
    }

    public static boolean replaceOfferItem(Block containerBlock, String offerId, ItemStack sourceItem) {
        if (!isValidListingItem(containerBlock, sourceItem)) {
            return false;
        }
        List<ShopOffer> offers = getOffers(containerBlock);
        ShopOffer target = null;
        for (ShopOffer offer : offers) {
            if (offer.getId().equals(offerId)) {
                target = offer;
            } else if (offer.matches(sourceItem)) {
                return false;
            }
        }
        if (target == null) {
            return false;
        }
        target.setItem(sourceItem);
        return saveOffers(containerBlock, offers);
    }

    public static boolean updateOfferPrices(Block containerBlock, String offerId, double buyPrice, double sellPrice) {
        if (buyPrice < 0D || sellPrice < 0D) {
            return false;
        }
        if (Config.settings_buy_greater_than_sell && buyPrice != 0D && sellPrice > buyPrice) {
            return false;
        }

        List<ShopOffer> offers = getOffers(containerBlock);
        ShopOffer target = findById(offers, offerId);
        if (target == null) {
            return false;
        }
        target.setBuyPrice(buyPrice);
        target.setSellPrice(sellPrice);
        target.setBuyingEnabled(buyPrice > 0D);
        target.setSellingEnabled(sellPrice > 0D);

        if (!saveOffers(containerBlock, offers)) {
            return false;
        }

        // New shops start globally disabled. Setting a usable per-item price should
        // make that direction available unless the owner later disables it globally.
        TileState state = (TileState) containerBlock.getState();
        PersistentDataContainer data = state.getPersistentDataContainer();
        if (buyPrice > 0D) {
            data.set(key("dbuy"), PersistentDataType.INTEGER, 0);
        }
        if (sellPrice > 0D) {
            data.set(key("dsell"), PersistentDataType.INTEGER, 0);
        }
        state.update();
        return true;
    }

    public static boolean toggleOfferBuying(Block containerBlock, String offerId) {
        List<ShopOffer> offers = getOffers(containerBlock);
        ShopOffer offer = findById(offers, offerId);
        if (offer == null) {
            return false;
        }
        offer.setBuyingEnabled(!offer.isBuyingEnabled());
        return saveOffers(containerBlock, offers);
    }

    public static boolean toggleOfferSelling(Block containerBlock, String offerId) {
        List<ShopOffer> offers = getOffers(containerBlock);
        ShopOffer offer = findById(offers, offerId);
        if (offer == null) {
            return false;
        }
        offer.setSellingEnabled(!offer.isSellingEnabled());
        return saveOffers(containerBlock, offers);
    }

    public static boolean saveOffers(Block containerBlock, List<ShopOffer> offers) {
        if (containerBlock == null || !(containerBlock.getState() instanceof TileState)) {
            return false;
        }

        List<ShopOffer> safeOffers = offers == null ? new ArrayList<>() : new ArrayList<>(offers);
        String payload = encodeOffers(safeOffers);
        if (payload == null) {
            return false;
        }

        writeOfferPayload((TileState) containerBlock.getState(), payload, safeOffers);
        mirrorPayloadToDoubleChest(containerBlock, payload);
        syncLegacyDatabase(containerBlock, safeOffers);
        return true;
    }

    public static boolean isEmptyShopItem(PersistentDataContainer data) {
        return getOffers(data).isEmpty();
    }

    public static ItemStack getShopItem(PersistentDataContainer data) {
        List<ShopOffer> offers = getOffers(data);
        return offers.isEmpty() ? emptyShopItem() : offers.get(0).getItem();
    }

    /**
     * Compatibility method for inherited single-item GUIs. It replaces the first
     * listing while preserving that listing's prices, or creates the first listing.
     */
    public static boolean setShopItem(Block containerBlock, ItemStack sourceItem) {
        if (!isValidListingItem(containerBlock, sourceItem)) {
            return false;
        }
        List<ShopOffer> offers = getOffers(containerBlock);
        if (offers.isEmpty()) {
            PersistentDataContainer data = ((TileState) containerBlock.getState()).getPersistentDataContainer();
            double buy = readDouble(data, "buy", 0D);
            double sell = readDouble(data, "sell", 0D);
            boolean buyEnabled = !readBooleanFlag(data, "dbuy", true);
            boolean sellEnabled = !readBooleanFlag(data, "dsell", true);
            offers.add(new ShopOffer(sourceItem, buy, sell, buyEnabled, sellEnabled));
        } else {
            offers.get(0).setItem(sourceItem);
        }
        return saveOffers(containerBlock, offers);
    }

    public static boolean isEmptyPlaceholder(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (meta.getPersistentDataContainer().has(placeholderItemKey(), PersistentDataType.INTEGER)) {
            return true;
        }
        if (!meta.hasDisplayName()) {
            return false;
        }
        String name = ChatColor.stripColor(meta.getDisplayName());
        return name != null && (name.equalsIgnoreCase("Select Shop Item")
                || name.equalsIgnoreCase("Add Shop Listings"));
    }

    private static boolean isValidListingItem(Block containerBlock, ItemStack sourceItem) {
        if (containerBlock == null || !(containerBlock.getState() instanceof TileState)) {
            return false;
        }
        if (sourceItem == null || sourceItem.getType() == Material.AIR || isEmptyPlaceholder(sourceItem)) {
            return false;
        }
        return !(Utils.isShulkerBox(sourceItem.getType()) && Utils.isShulkerBox(containerBlock));
    }

    private static ShopOffer findById(List<ShopOffer> offers, String offerId) {
        if (offerId == null) {
            return null;
        }
        for (ShopOffer offer : offers) {
            if (offerId.equals(offer.getId())) {
                return offer;
            }
        }
        return null;
    }

    private static List<ShopOffer> migrateLegacyOffer(PersistentDataContainer data) {
        String encodedItem = data.get(key("item"), PersistentDataType.STRING);
        if (encodedItem == null || encodedItem.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            ItemStack item = Utils.decodeItem(encodedItem);
            if (item == null || item.getType() == Material.AIR || isEmptyPlaceholder(item)) {
                return new ArrayList<>();
            }
            double buy = readDouble(data, "buy", 0D);
            double sell = readDouble(data, "sell", 0D);
            boolean buyingEnabled = !readBooleanFlag(data, "dbuy", false);
            boolean sellingEnabled = !readBooleanFlag(data, "dsell", false);
            List<ShopOffer> migrated = new ArrayList<>();
            migrated.add(new ShopOffer(item, buy, sell, buyingEnabled, sellingEnabled));
            return migrated;
        } catch (Throwable ignored) {
            return new ArrayList<>();
        }
    }

    @SuppressWarnings("unchecked")
    private static String encodeOffers(List<ShopOffer> offers) {
        try {
            JSONArray array = new JSONArray();
            for (ShopOffer offer : offers) {
                String encodedItem = Utils.encodeItem(offer.getItem());
                if (encodedItem == null || encodedItem.trim().isEmpty()) {
                    continue;
                }
                JSONObject object = new JSONObject();
                object.put("id", offer.getId());
                object.put("item", encodedItem);
                object.put("buy", offer.getBuyPrice());
                object.put("sell", offer.getSellPrice());
                object.put("buyEnabled", offer.isBuyingEnabled());
                object.put("sellEnabled", offer.isSellingEnabled());
                array.add(object);
            }
            return array.toJSONString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static List<ShopOffer> decodeOffers(String payload) {
        try {
            Object parsed = new JSONParser().parse(payload);
            if (!(parsed instanceof JSONArray)) {
                return null;
            }
            List<ShopOffer> offers = new ArrayList<>();
            for (Object entry : (JSONArray) parsed) {
                if (!(entry instanceof JSONObject)) {
                    continue;
                }
                JSONObject object = (JSONObject) entry;
                String id = stringValue(object.get("id"));
                String encodedItem = stringValue(object.get("item"));
                ItemStack item = Utils.decodeItem(encodedItem);
                if (item == null || item.getType() == Material.AIR || isEmptyPlaceholder(item)) {
                    continue;
                }
                double buy = numberValue(object.get("buy"));
                double sell = numberValue(object.get("sell"));
                boolean buyEnabled = booleanValue(object.get("buyEnabled"), buy > 0D);
                boolean sellEnabled = booleanValue(object.get("sellEnabled"), sell > 0D);
                offers.add(new ShopOffer(id, item, buy, sell, buyEnabled, sellEnabled));
            }
            return offers;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void writeOfferPayload(TileState state, String payload, List<ShopOffer> offers) {
        PersistentDataContainer data = state.getPersistentDataContainer();
        data.set(offersKey(), PersistentDataType.STRING, payload);
        data.set(emptyShopItemKey(), PersistentDataType.INTEGER, offers.isEmpty() ? 1 : 0);

        if (offers.isEmpty()) {
            String placeholder = Utils.encodeItem(emptyShopItem());
            if (placeholder != null) {
                data.set(key("item"), PersistentDataType.STRING, placeholder);
            }
            data.set(key("buy"), PersistentDataType.DOUBLE, 0D);
            data.set(key("sell"), PersistentDataType.DOUBLE, 0D);
        } else {
            ShopOffer first = offers.get(0);
            String encodedItem = Utils.encodeItem(first.getItem());
            if (encodedItem != null) {
                data.set(key("item"), PersistentDataType.STRING, encodedItem);
            }
            data.set(key("buy"), PersistentDataType.DOUBLE, first.getBuyPrice());
            data.set(key("sell"), PersistentDataType.DOUBLE, first.getSellPrice());
        }
        state.update();
    }

    private static void mirrorPayloadToDoubleChest(Block containerBlock, String payload) {
        try {
            Inventory inventory = Utils.getBlockInventory(containerBlock);
            if (!(inventory instanceof DoubleChestInventory)) {
                return;
            }
            DoubleChest doubleChest = (DoubleChest) inventory.getHolder();
            if (doubleChest == null) {
                return;
            }
            if (doubleChest.getLeftSide() instanceof Chest) {
                Chest left = (Chest) doubleChest.getLeftSide();
                left.getPersistentDataContainer().set(offersKey(), PersistentDataType.STRING, payload);
                left.update();
            }
            if (doubleChest.getRightSide() instanceof Chest) {
                Chest right = (Chest) doubleChest.getRightSide();
                right.getPersistentDataContainer().set(offersKey(), PersistentDataType.STRING, payload);
                right.update();
            }
        } catch (Throwable ignored) {
            // The canonical shop half was already saved; mirroring is best-effort.
        }
    }

    private static void syncLegacyDatabase(Block containerBlock, List<ShopOffer> offers) {
        try {
            String sloc = Utils.LocationtoString(containerBlock.getLocation());
            ItemStack item = offers.isEmpty() ? emptyShopItem() : offers.get(0).getItem();
            double buy = offers.isEmpty() ? 0D : offers.get(0).getBuyPrice();
            double sell = offers.isEmpty() ? 0D : offers.get(0).getSellPrice();
            String encodedItem = Utils.encodeItem(item);
            if (encodedItem != null) {
                EzChestShop.getPlugin().getDatabase().setString("location", sloc, "item", "shopdata", encodedItem);
            }
            EzChestShop.getPlugin().getDatabase().setDouble("location", sloc, "buyPrice", "shopdata", buy);
            EzChestShop.getPlugin().getDatabase().setDouble("location", sloc, "sellPrice", "shopdata", sell);

            // Keep the inherited in-memory first-listing prices current for legacy commands.
            EzShop shop = ShopContainer.getShop(containerBlock.getLocation());
            if (shop != null) {
                shop.setBuyPrice(buy);
                shop.setSellPrice(sell);
            }
        } catch (Throwable ignored) {
            // Container PDC remains the source of truth for the multi-item system.
        }
    }

    private static double readDouble(PersistentDataContainer data, String name, double fallback) {
        Double value = data.get(key(name), PersistentDataType.DOUBLE);
        return value == null ? fallback : value;
    }

    private static boolean readBooleanFlag(PersistentDataContainer data, String name, boolean fallback) {
        Integer value = data.get(key(name), PersistentDataType.INTEGER);
        return value == null ? fallback : value == 1;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static double numberValue(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : 0D;
    }

    private static boolean booleanValue(Object value, boolean fallback) {
        return value instanceof Boolean ? (Boolean) value : fallback;
    }
}
