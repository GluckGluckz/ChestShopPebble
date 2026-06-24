package me.deadlight.ezchestshop.utils;

import me.deadlight.ezchestshop.EzChestShop;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Paper/Bukkit API implementation used by PebbleShop on Pebble Quest Paper 26.1.2.
 *
 * <p>The original upstream plugin used version-specific NMS packet classes. Those classes are
 * fragile across custom Paper builds, so this implementation intentionally avoids direct NMS and
 * CraftBukkit references. Visual helpers are backed by normal Bukkit entities where possible and
 * unsupported packet-only features degrade safely instead of disabling the whole shop plugin.</p>
 */
public class PaperVersionUtils extends VersionUtils {

    private static final Map<Integer, Entity> ENTITIES = new ConcurrentHashMap<>();
    private static final AtomicBoolean SIGN_WARNING_SENT = new AtomicBoolean(false);

    @Override
    String ItemToTextCompoundString(ItemStack itemStack) {
        if (itemStack == null) {
            return "{}";
        }
        return itemStack.serialize().toString();
    }

    @Override
    int getItemIndex() {
        return 0;
    }

    @Override
    int getArmorStandIndex() {
        return 0;
    }

    @Override
    void destroyEntity(Player player, int entityID) {
        Entity entity = ENTITIES.remove(entityID);
        if (entity != null && !entity.isDead()) {
            entity.remove();
        }
    }

    @Override
    void spawnHologram(Player player, Location location, String line, int ID) {
        if (location == null || location.getWorld() == null) {
            return;
        }

        Entity entity = location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        if (entity instanceof ArmorStand) {
            ArmorStand armorStand = (ArmorStand) entity;
            armorStand.setVisible(false);
            armorStand.setMarker(true);
            armorStand.setGravity(false);
            armorStand.setInvulnerable(true);
            armorStand.setCustomName(line);
            armorStand.setCustomNameVisible(true);
        }
        ENTITIES.put(ID, entity);
    }

    @Override
    void spawnFloatingItem(Player player, Location location, ItemStack itemStack, int ID) {
        if (location == null || location.getWorld() == null || itemStack == null) {
            return;
        }

        Item item = location.getWorld().dropItem(location, itemStack.clone());
        item.setGravity(false);
        item.setVelocity(new Vector(0, 0, 0));
        item.setPickupDelay(Integer.MAX_VALUE);
        item.setInvulnerable(true);
        ENTITIES.put(ID, item);
    }

    @Override
    void renameEntity(Player player, int entityID, String name) {
        Entity entity = ENTITIES.get(entityID);
        if (entity != null) {
            entity.setCustomName(name);
        }
    }

    @Override
    void teleportEntity(Player player, int entityID, Location location) {
        Entity entity = ENTITIES.get(entityID);
        if (entity != null && location != null) {
            entity.teleport(location);
        }
    }

    @Override
    void signFactoryListen(SignMenuFactory signMenuFactory) {
        // The old implementation listened for NMS sign update packets. PebbleShop keeps running
        // without that packet hook on 26.1.2; command/GUI flows continue to work normally.
    }

    @Override
    void removeSignMenuFactoryListen(SignMenuFactory signMenuFactory) {
        // No listener was registered in the Paper-safe implementation.
    }

    @Override
    void openMenu(SignMenuFactory.Menu menu, Player player) {
        if (SIGN_WARNING_SENT.compareAndSet(false, true)) {
            Bukkit.getLogger().log(Level.WARNING,
                    "[PebbleShop] Packet-based sign menus are not available on this Paper 26.1.2 build. "
                            + "Use PebbleShop commands/GUI flows instead of legacy sign input where possible.");
        }
        if (player != null) {
            player.sendMessage(Utils.colorify("&d[&bPebbleShop&d] &cLegacy sign input is not available on this server build."));
        }
    }

    @Override
    public void injectConnection(Player player) {
        // No packet injection is required for the Paper-safe implementation.
    }

    @Override
    public void ejectConnection(Player player) {
        // No packet injection is required for the Paper-safe implementation.
    }

    @Override
    void showOutline(Player player, Block block, int eID) {
        // The upstream outline was packet/NMS based. Skip it safely on 26.1.2 instead of crashing.
        if (EzChestShop.getPlugin() != null) {
            EzChestShop.logDebug("Skipping packet-based block outline on Paper-safe version utilities.");
        }
    }
}
