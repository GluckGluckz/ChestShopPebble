package me.deadlight.ezchestshop.utils;

/**
 * Compatibility alias used by the legacy version loader.
 *
 * <p>PebbleShop's released 26.1.2 build no longer ships the old NMS-backed 1.20.4
 * CraftBukkit module. This class keeps the existing reflective loader happy while routing
 * behavior through the Paper-safe Bukkit API implementation.</p>
 */
public class v1_20_R3 extends PaperVersionUtils {
}
