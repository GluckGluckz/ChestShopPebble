package me.deadlight.ezchestshop.integrations;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;

/**
 * Minimal economy surface PebbleShop needs for shop transactions.
 *
 * <p>Keeping this local prevents Paper from requiring Vault classes when PebbleShop is deployed
 * on Pebble Quest with PebbleCore as the economy provider.</p>
 */
public interface ShopEconomy extends Economy {

    boolean isEnabled();

    String getName();

    double getBalance(OfflinePlayer player);

    boolean has(OfflinePlayer player, double amount);

    boolean withdraw(OfflinePlayer player, double amount);

    boolean deposit(OfflinePlayer player, double amount);

    String format(double amount);

    @Override
    default EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        boolean success = withdraw(player, amount);
        return response(player, amount, success);
    }

    @Override
    default EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        boolean success = deposit(player, amount);
        return response(player, amount, success);
    }

    default EconomyResponse response(OfflinePlayer player, double amount, boolean success) {
        double balance = getBalance(player);
        String error = success ? null : PebbleEconomyBridge.lastError();
        if (error == null && !success) {
            error = "PebbleCore Cash transaction failed.";
        }
        return new EconomyResponse(
                amount,
                balance,
                success ? EconomyResponse.ResponseType.SUCCESS : EconomyResponse.ResponseType.FAILURE,
                error
        );
    }
}
