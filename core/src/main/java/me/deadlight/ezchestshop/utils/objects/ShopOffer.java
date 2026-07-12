package me.deadlight.ezchestshop.utils.objects;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * One independently priced item listing inside a chest shop.
 *
 * Item identity is based on Bukkit's full ItemStack similarity check, so custom
 * names, lore, enchantments, potion data, book contents, model data and other
 * item metadata remain part of the listing identity.
 */
public class ShopOffer {

    private final String id;
    private ItemStack item;
    private double buyPrice;
    private double sellPrice;
    private boolean buyingEnabled;
    private boolean sellingEnabled;

    public ShopOffer(ItemStack item, double buyPrice, double sellPrice,
                     boolean buyingEnabled, boolean sellingEnabled) {
        this(UUID.randomUUID().toString(), item, buyPrice, sellPrice, buyingEnabled, sellingEnabled);
    }

    public ShopOffer(String id, ItemStack item, double buyPrice, double sellPrice,
                     boolean buyingEnabled, boolean sellingEnabled) {
        this.id = id == null || id.trim().isEmpty() ? UUID.randomUUID().toString() : id;
        setItem(item);
        this.buyPrice = Math.max(0D, buyPrice);
        this.sellPrice = Math.max(0D, sellPrice);
        this.buyingEnabled = buyingEnabled;
        this.sellingEnabled = sellingEnabled;
    }

    public String getId() {
        return id;
    }

    public ItemStack getItem() {
        return item.clone();
    }

    public void setItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            throw new IllegalArgumentException("A shop offer must contain a real item");
        }
        this.item = item.clone();
        this.item.setAmount(1);
    }

    public double getBuyPrice() {
        return buyPrice;
    }

    public void setBuyPrice(double buyPrice) {
        this.buyPrice = Math.max(0D, buyPrice);
    }

    public double getSellPrice() {
        return sellPrice;
    }

    public void setSellPrice(double sellPrice) {
        this.sellPrice = Math.max(0D, sellPrice);
    }

    public boolean isBuyingEnabled() {
        return buyingEnabled;
    }

    public void setBuyingEnabled(boolean buyingEnabled) {
        this.buyingEnabled = buyingEnabled;
    }

    public boolean isSellingEnabled() {
        return sellingEnabled;
    }

    public void setSellingEnabled(boolean sellingEnabled) {
        this.sellingEnabled = sellingEnabled;
    }

    public boolean matches(ItemStack other) {
        if (other == null || other.getType() == Material.AIR) {
            return false;
        }
        ItemStack normalized = other.clone();
        normalized.setAmount(1);
        return item.isSimilar(normalized);
    }
}
