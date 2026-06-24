package me.mrCookieSlime.Slimefun.api;

import org.bukkit.Location;

/**
 * PebbleShop does not integrate with Slimefun.
 *
 * <p>The upstream EzChestShop source had optional Slimefun checks wired directly
 * into command/listener code. Keeping this tiny compatibility shim lets the
 * legacy code compile without carrying Slimefun as a dependency. It always
 * returns false, meaning PebbleShop treats containers normally.</p>
 */
public final class BlockStorage {

    private BlockStorage() {
    }

    public static boolean hasBlockInfo(Location location) {
        return false;
    }
}
