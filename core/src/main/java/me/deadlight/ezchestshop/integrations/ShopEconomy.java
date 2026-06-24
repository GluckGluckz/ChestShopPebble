package me.deadlight.ezchestshop.integrations;

import org.bukkit.OfflinePlayer;

/**
 * Minimal economy surface PebbleShop needs for shop transactions.
 *
 * <p>Keeping this local prevents Paper from requiring Vault classes when PebbleShop is deployed
 * on Pebble Quest with PebbleCore as the economy provider.</p>
 */
public interface ShopEconomy {

    boolean isEnabled();

    String getName();

    double getBalance(OfflinePlayer player);

    boolean has(OfflinePlayer player, double amount);

    boolean withdraw(OfflinePlayer player, double amount);

    boolean deposit(OfflinePlayer player, double amount);

    String format(double amount);
}
