package me.deadlight.ezchestshop.utils;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class FloatingItem {

    private int entityID;
    private Player player;
    private Location location;

    public FloatingItem(Player player, ItemStack itemStack, Location location) {

        this.player = player;
        this.entityID = (int) (Math.random() * Integer.MAX_VALUE);
        this.location = location;
        getVersionUtils().spawnFloatingItem(player, location, itemStack, entityID);

    }

    public void destroy() {
        getVersionUtils().destroyEntity(player, entityID);
    }

    public void teleport(Location location) {
        this.location = location;
        getVersionUtils().teleportEntity(player, entityID, location);
    }

    public Location getLocation() {
        return location;
    }

    private VersionUtils getVersionUtils() {
        if (Utils.versionUtils == null) {
            Utils.versionUtils = new PaperVersionUtils();
        }
        return Utils.versionUtils;
    }

}
