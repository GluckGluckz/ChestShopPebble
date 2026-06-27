package me.deadlight.ezchestshop.utils;

import me.deadlight.ezchestshop.EzChestShop;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

/**
 * Utilities for the sign-first shop setup flow.
 *
 * A newly placed [shop] sign creates a safe, empty shop. The actual trade item is
 * selected later from the owner GUI, so the shop needs a stable placeholder item
 * for storage/database compatibility while trading remains disabled.
 */
public final class ShopItemUtils {

    private ShopItemUtils() {}

    public static NamespacedKey emptyShopItemKey() {
        return new NamespacedKey(EzChestShop.getPlugin(), "empty_shop_item");
    }

    private static NamespacedKey placeholderItemKey() {
        return new NamespacedKey(EzChestShop.getPlugin(), "empty_shop_placeholder");
    }

    public static ItemStack emptyShopItem() {
        ItemStack item = new ItemStack(Material.CHEST, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Utils.colorify("&e&lSelect Shop Item"));
            meta.setLore(Arrays.asList(
                    Utils.colorify("&7This shop is not selling anything yet."),
                    Utils.colorify(""),
                    Utils.colorify("&fPick up an item from your inventory,"),
                    Utils.colorify("&fthen click this slot to select it."),
                    Utils.colorify(""),
                    Utils.colorify("&aAfter that, set buy/sell prices in settings.")
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
                    Utils.colorify("&7The owner still needs to choose"),
                    Utils.colorify("&7an item and set prices."),
                    Utils.colorify(""),
                    Utils.colorify("&fCheck back soon.")
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isEmptyShopItem(PersistentDataContainer data) {
        if (data == null) return true;
        Integer emptyFlag = data.get(emptyShopItemKey(), PersistentDataType.INTEGER);
        if (emptyFlag != null && emptyFlag == 1) return true;

        String encodedItem = data.get(new NamespacedKey(EzChestShop.getPlugin(), "item"), PersistentDataType.STRING);
        if (encodedItem == null || encodedItem.trim().isEmpty()) return true;

        try {
            return isEmptyPlaceholder(Utils.decodeItem(encodedItem));
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static ItemStack getShopItem(PersistentDataContainer data) {
        if (isEmptyShopItem(data)) return emptyShopItem();
        String encodedItem = data.get(new NamespacedKey(EzChestShop.getPlugin(), "item"), PersistentDataType.STRING);
        try {
            ItemStack item = Utils.decodeItem(encodedItem);
            if (item == null || item.getType() == Material.AIR) return emptyShopItem();
            return item;
        } catch (Throwable ignored) {
            return emptyShopItem();
        }
    }

    public static boolean setShopItem(Block containerBlock, ItemStack sourceItem) {
        if (containerBlock == null || !(containerBlock.getState() instanceof TileState)) return false;
        if (sourceItem == null || sourceItem.getType() == Material.AIR || isEmptyPlaceholder(sourceItem)) return false;

        ItemStack shopItem = sourceItem.clone();
        shopItem.setAmount(1);
        String encodedItem = Utils.encodeItem(shopItem);
        if (encodedItem == null || encodedItem.trim().isEmpty()) return false;

        TileState state = (TileState) containerBlock.getState();
        PersistentDataContainer data = state.getPersistentDataContainer();
        data.set(new NamespacedKey(EzChestShop.getPlugin(), "item"), PersistentDataType.STRING, encodedItem);
        data.set(emptyShopItemKey(), PersistentDataType.INTEGER, 0);
        state.update();

        EzChestShop.getPlugin().getDatabase().setString(
                "location",
                Utils.LocationtoString(containerBlock.getLocation()),
                "item",
                "shopdata",
                encodedItem
        );
        return true;
    }

    public static boolean isEmptyPlaceholder(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        if (meta.getPersistentDataContainer().has(placeholderItemKey(), PersistentDataType.INTEGER)) return true;
        if (!meta.hasDisplayName()) return false;
        String name = ChatColor.stripColor(meta.getDisplayName());
        return name != null && name.equalsIgnoreCase("Select Shop Item");
    }
}
