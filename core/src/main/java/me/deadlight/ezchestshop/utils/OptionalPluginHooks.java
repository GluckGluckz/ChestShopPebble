package me.deadlight.ezchestshop.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/**
 * Small reflection bridge for optional soft integrations.
 *
 * PebbleShop should not require optional third-party API jars at compile time.
 * These helpers keep the plugin buildable while still using the integration when
 * the matching plugin is present at runtime.
 */
public final class OptionalPluginHooks {

    private OptionalPluginHooks() {
    }

    public static boolean hasSlimefunBlockInfo(Location location) {
        if (location == null || Bukkit.getPluginManager().getPlugin("Slimefun") == null) {
            return false;
        }

        try {
            Class<?> blockStorageClass = Class.forName("me.mrCookieSlime.Slimefun.api.BlockStorage");
            Method hasBlockInfo = blockStorageClass.getMethod("hasBlockInfo", Location.class);
            Object result = hasBlockInfo.invoke(null, location);
            return result instanceof Boolean && (Boolean) result;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static String setPlaceholders(Player player, String command) {
        if (command == null) {
            return null;
        }

        String fallback = command.replace("%player_name%", player.getName());
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return fallback;
        }

        try {
            Class<?> placeholderApiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Method setPlaceholders = placeholderApiClass.getMethod("setPlaceholders", Player.class, String.class);
            Object result = setPlaceholders.invoke(null, player, fallback);
            return result instanceof String ? (String) result : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }
}
