package net.milkbowl.vault.economy;

import org.bukkit.OfflinePlayer;

/**
 * Minimal Vault Economy compatibility surface used by legacy PebbleShop call sites.
 *
 * <p>This is intentionally tiny. PebbleShop does not require the Vault plugin at runtime; money
 * is routed through PebbleCore via ShopEconomy.</p>
 */
public interface Economy {

    boolean isEnabled();

    String getName();

    double getBalance(OfflinePlayer player);

    EconomyResponse withdrawPlayer(OfflinePlayer player, double amount);

    EconomyResponse depositPlayer(OfflinePlayer player, double amount);
}
