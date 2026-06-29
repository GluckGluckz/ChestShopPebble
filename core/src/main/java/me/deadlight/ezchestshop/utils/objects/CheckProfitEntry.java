package me.deadlight.ezchestshop.utils.objects;

import me.deadlight.ezchestshop.utils.Utils;
import org.bukkit.inventory.ItemStack;

public class CheckProfitEntry {


    public static String itemSpacer = "#&#";
    public static String itemInlineSpacer = "#,#";

    // Id,ItemStack,BuyAmount,BuyPrice,SellAmount,SellPrice
    private String id;
    private ItemStack item;
    private Integer buyAmount;
    private Double buyPrice;
    private Double buyUnitPrice;
    private Integer sellAmount;
    private Double sellPrice;
    private Double sellUnitPrice;

    // Constructors:
    public CheckProfitEntry(String id, ItemStack item, Integer buyAmount, Double buyPrice, Double buyUnitPrice, Integer sellAmount,
                            Double sellPrice, Double sellUnitPrice) {
        this.id = id;
        this.item = item;
        this.buyAmount = safeInt(buyAmount);
        this.buyPrice = safeDouble(buyPrice);
        this.buyUnitPrice = safeDouble(buyUnitPrice);
        this.sellAmount = safeInt(sellAmount);
        this.sellPrice = safeDouble(sellPrice);
        this.sellUnitPrice = safeDouble(sellUnitPrice);
    }

    public CheckProfitEntry(String string) {
        if (string != null && !string.trim().isEmpty()) {
            try {
                String[] split = string.split(itemInlineSpacer);
                if (split.length < 8) return;
                id = split[0];
                item = Utils.decodeItem(split[1]);
                buyAmount = safeInt(parseInt(split[2]));
                buyPrice = safeDouble(parseDouble(split[3]));
                buyUnitPrice = safeDouble(parseDouble(split[4]));
                sellAmount = safeInt(parseInt(split[5]));
                sellPrice = safeDouble(parseDouble(split[6]));
                sellUnitPrice = safeDouble(parseDouble(split[7]));
            } catch (RuntimeException ignored) {
                id = null;
                item = null;
                buyAmount = 0;
                buyPrice = 0.0D;
                buyUnitPrice = 0.0D;
                sellAmount = 0;
                sellPrice = 0.0D;
                sellUnitPrice = 0.0D;
            }
        }

    }

    public String toString() {
        return id + itemInlineSpacer + Utils.encodeItem(item) + itemInlineSpacer + getBuyAmount() + itemInlineSpacer
                + getBuyPrice() + itemInlineSpacer + getBuyUnitPrice() + itemInlineSpacer + getSellAmount() + itemInlineSpacer
                + getSellPrice() + itemInlineSpacer + getSellUnitPrice();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ItemStack getItem() {
        return item;
    }

    public void setItem(ItemStack item) {
        this.item = item;
    }

    public Integer getBuyAmount() {
        return safeInt(buyAmount);
    }

    public void setBuyAmount(Integer buyAmount) {
        this.buyAmount = safeInt(buyAmount);
    }

    public Double getBuyPrice() {
        return safeDouble(buyPrice);
    }

    public void setBuyPrice(Double buyPrice) {
        this.buyPrice = safeDouble(buyPrice);
    }

    public Integer getSellAmount() {
        return safeInt(sellAmount);
    }

    public void setSellAmount(Integer sellAmount) {
        this.sellAmount = safeInt(sellAmount);
    }

    public Double getSellPrice() {
        return safeDouble(sellPrice);
    }

    public void setSellPrice(Double sellPrice) {
        this.sellPrice = safeDouble(sellPrice);
    }

    public Double getBuyUnitPrice() {
        return safeDouble(buyUnitPrice);
    }

    public Double getSellUnitPrice() {
        return safeDouble(sellUnitPrice);
    }

    private static Integer parseInt(String raw) {
        return raw == null || raw.equalsIgnoreCase("null") || raw.isEmpty() ? 0 : Integer.valueOf(raw);
    }

    private static Double parseDouble(String raw) {
        return raw == null || raw.equalsIgnoreCase("null") || raw.isEmpty() ? 0.0D : Double.valueOf(raw);
    }

    private static int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private static double safeDouble(Double value) {
        return value == null ? 0.0D : value;
    }
}
