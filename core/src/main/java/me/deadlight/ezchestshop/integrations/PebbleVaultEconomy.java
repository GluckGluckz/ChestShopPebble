package me.deadlight.ezchestshop.integrations;

import org.bukkit.OfflinePlayer;

/**
 * PebbleCore-backed economy adapter used by PebbleShop.
 *
 * <p>This class intentionally does not implement Vault's Economy interface. PebbleShop must be
 * able to load on Pebble Quest servers where Vault is not installed; direct Vault type references
 * in plugin startup classes make Paper fail before onEnable().</p>
 */
public final class PebbleVaultEconomy implements ShopEconomy {

    @Override
    public boolean isEnabled() {
        return PebbleEconomyBridge.economyAvailable();
    }

    @Override
    public String getName() {
        return "PebbleCore";
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return PebbleEconomyBridge.getBalance(player);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return PebbleEconomyBridge.has(player, amount);
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        return PebbleEconomyBridge.withdraw(player, amount);
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        return PebbleEconomyBridge.deposit(player, amount);
    }

    @Override
    public String format(double amount) {
        return PebbleEconomyBridge.format(amount);
    }
}
