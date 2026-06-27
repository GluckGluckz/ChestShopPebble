package me.deadlight.ezchestshop.utils.holograms;

import me.deadlight.ezchestshop.utils.ASHologram;
import me.deadlight.ezchestshop.utils.FloatingItem;
import me.deadlight.ezchestshop.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Central cleanup for PebbleShop's packet/entity holograms.
 *
 * <p>PebbleShop has two hologram paths: the newer block-bound cache and the older transient
 * looking-at-shop renderer. Config reloads, deleted config files, and plugin disable need to clean
 * both paths. Otherwise the client can keep seeing stale armor stands/floating items such as an
 * orphaned "empty shop" line even after the server-side config/shop cache has changed.</p>
 */
public final class HologramCleanup {

    private HologramCleanup() {}

    public static void resetAll(String reason) {
        try {
            for (Player player : Bukkit.getOnlinePlayers()) {
                try {
                    ShopHologram.hideAll(player);
                } catch (Throwable ignored) {
                    // Continue; packet cleanup below is the hard fallback.
                }
            }
            clearShopHologramCaches();
            destroyAllOnlinePackets();
        } catch (Throwable throwable) {
            Bukkit.getLogger().log(Level.WARNING, "[PebbleShop] Failed to reset holograms during " + reason + ".", throwable);
        }
    }

    public static void resetAt(Location location, String reason) {
        if (location == null || location.getWorld() == null) return;
        try {
            try {
                ShopHologram.hideForAll(location);
            } catch (Throwable ignored) {
                // Fall through to direct cache/packet cleanup.
            }
            removeLocationFromShopHologramCaches(location);
            destroyOnlinePacketsNear(location, 5.0D);
        } catch (Throwable throwable) {
            Bukkit.getLogger().log(Level.WARNING, "[PebbleShop] Failed to reset hologram at " + location + " during " + reason + ".", throwable);
        }
    }

    private static void destroyAllOnlinePackets() {
        List<Object> copy = new ArrayList<Object>(Utils.onlinePackets);
        for (Object object : copy) destroyPacket(object);
        Utils.onlinePackets.clear();
    }

    private static void destroyOnlinePacketsNear(Location location, double radius) {
        double radiusSquared = radius * radius;
        Iterator<Object> iterator = Utils.onlinePackets.iterator();
        while (iterator.hasNext()) {
            Object object = iterator.next();
            Location packetLocation = packetLocation(object);
            if (packetLocation == null || packetLocation.getWorld() == null) continue;
            if (!packetLocation.getWorld().equals(location.getWorld())) continue;
            if (packetLocation.distanceSquared(location) > radiusSquared) continue;
            destroyPacket(object);
            iterator.remove();
        }
    }

    private static void destroyPacket(Object object) {
        try {
            if (object instanceof ASHologram) {
                ASHologram hologram = (ASHologram) object;
                hologram.destroy();
            } else if (object instanceof FloatingItem) {
                FloatingItem floatingItem = (FloatingItem) object;
                floatingItem.destroy();
            }
        } catch (Throwable ignored) {
            // Best-effort packet cleanup. Stale packets should never break reload/disable.
        }
    }

    private static Location packetLocation(Object object) {
        if (object instanceof ASHologram) return ((ASHologram) object).getLocation();
        if (object instanceof FloatingItem) return ((FloatingItem) object).getLocation();
        return null;
    }

    private static void clearShopHologramCaches() throws ReflectiveOperationException {
        Object playerMap = staticField("playerLocationShopHoloMap").get(null);
        if (playerMap instanceof Map) ((Map<?, ?>) playerMap).clear();

        Object blockMap = staticField("locationBlockHoloMap").get(null);
        if (blockMap instanceof Map) ((Map<?, ?>) blockMap).clear();

        Object inspections = staticField("hologramInspections").get(null);
        if (inspections instanceof Map) ((Map<?, ?>) inspections).clear();
    }

    @SuppressWarnings("unchecked")
    private static void removeLocationFromShopHologramCaches(Location location) throws ReflectiveOperationException {
        Object playerMapRaw = staticField("playerLocationShopHoloMap").get(null);
        if (playerMapRaw instanceof Map) {
            Map<UUID, Map<Location, ShopHologram>> playerMap = (Map<UUID, Map<Location, ShopHologram>>) playerMapRaw;
            Iterator<Map.Entry<UUID, Map<Location, ShopHologram>>> iterator = playerMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, Map<Location, ShopHologram>> entry = iterator.next();
                entry.getValue().remove(location);
                if (entry.getValue().isEmpty()) iterator.remove();
            }
        }

        Object blockMapRaw = staticField("locationBlockHoloMap").get(null);
        if (blockMapRaw instanceof Map) {
            ((Map<Location, BlockBoundHologram>) blockMapRaw).remove(location);
        }

        Object inspectionsRaw = staticField("hologramInspections").get(null);
        if (inspectionsRaw instanceof Map) {
            Map<UUID, ShopHologram> inspections = (Map<UUID, ShopHologram>) inspectionsRaw;
            inspections.entrySet().removeIf(entry -> entry.getValue() != null && location.equals(entry.getValue().getLocation()));
        }
    }

    private static Field staticField(String name) throws NoSuchFieldException {
        Field field = ShopHologram.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
