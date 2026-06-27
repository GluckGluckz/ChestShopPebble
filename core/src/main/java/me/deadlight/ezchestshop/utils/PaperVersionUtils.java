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
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    private static final Map<UUID, SignMenuFactory.Menu> CHAT_SIGN_INPUTS = new ConcurrentHashMap<>();
    private static final AtomicBoolean CHAT_LISTENER_REGISTERED = new AtomicBoolean(false);
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
        // The legacy sign editor used NMS packet listeners that do not exist on Pebble Quest's
        // Paper 26.1.2 build. Register one chat-based fallback listener instead; each opened sign
        // prompt stores its active Menu in CHAT_SIGN_INPUTS.
        if (CHAT_LISTENER_REGISTERED.compareAndSet(false, true)) {
            Bukkit.getPluginManager().registerEvents(new ChatSignInputListener(), EzChestShop.getPlugin());
            Bukkit.getLogger().log(Level.INFO,
                    "[PebbleShop] Legacy sign menus will use chat input on this server build.");
        }
    }

    @Override
    void removeSignMenuFactoryListen(SignMenuFactory signMenuFactory) {
        // Global listener is plugin-owned and is automatically unregistered by Bukkit on disable.
    }

    @Override
    void openMenu(SignMenuFactory.Menu menu, Player player) {
        if (player == null || menu == null) {
            return;
        }
        if (SIGN_WARNING_SENT.compareAndSet(false, true)) {
            Bukkit.getLogger().log(Level.WARNING,
                    "[PebbleShop] Packet-based sign menus are not available on this Paper 26.1.2 build. "
                            + "Falling back to chat input for legacy sign prompts.");
        }

        CHAT_SIGN_INPUTS.put(player.getUniqueId(), menu);
        menu.getFactory().getInputs().put(player, menu);

        player.closeInventory();
        player.sendMessage(Utils.colorify("&d[&bPebbleShop&d] &eType your input in chat. Type &ccancel &eto cancel."));
        player.sendMessage(Utils.colorify("&d[&bPebbleShop&d] &7For multi-line hologram text, separate lines with &f|&7."));

        for (String line : cleanPromptLines(menu.getText())) {
            player.sendMessage(Utils.colorify("&8» &7" + line));
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

    private static List<String> cleanPromptLines(List<String> rawLines) {
        List<String> lines = new ArrayList<>();
        if (rawLines == null) {
            return lines;
        }
        for (String line : rawLines) {
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            lines.add(line);
        }
        return lines;
    }

    private static String[] chatToSignLines(String message) {
        String[] lines = new String[SignMenuFactory.SIGN_LINES];
        for (int i = 0; i < lines.length; i++) {
            lines[i] = "";
        }

        if (message == null) {
            return lines;
        }

        String[] split = message.split("\\|", SignMenuFactory.SIGN_LINES);
        for (int i = 0; i < split.length && i < lines.length; i++) {
            lines[i] = split[i].trim();
        }
        return lines;
    }

    private static void removeInput(Player player, SignMenuFactory.Menu menu) {
        if (player == null) {
            return;
        }
        CHAT_SIGN_INPUTS.remove(player.getUniqueId());
        if (menu != null) {
            menu.getFactory().getInputs().remove(player);
        }
    }

    private static final class ChatSignInputListener implements Listener {

        @EventHandler
        public void onChat(AsyncPlayerChatEvent event) {
            final Player player = event.getPlayer();
            final SignMenuFactory.Menu menu = CHAT_SIGN_INPUTS.get(player.getUniqueId());
            if (menu == null) {
                return;
            }

            event.setCancelled(true);
            final String message = event.getMessage();
            Bukkit.getScheduler().runTask(EzChestShop.getPlugin(), new Runnable() {
                @Override
                public void run() {
                    handleInput(player, menu, message);
                }
            });
        }

        private void handleInput(Player player, SignMenuFactory.Menu menu, String message) {
            if (player == null || menu == null || !player.isOnline()) {
                return;
            }
            if (message != null && message.equalsIgnoreCase("cancel")) {
                removeInput(player, menu);
                player.sendMessage(Utils.colorify("&d[&bPebbleShop&d] &cInput cancelled."));
                return;
            }

            boolean accepted = false;
            try {
                accepted = menu.getResponse() == null || menu.getResponse().test(player, chatToSignLines(message));
            } catch (Throwable throwable) {
                Bukkit.getLogger().log(Level.WARNING,
                        "[PebbleShop] Chat fallback for sign input failed for " + player.getName() + ".", throwable);
            }

            if (accepted || menu.isForceClose()) {
                removeInput(player, menu);
                return;
            }

            player.sendMessage(Utils.colorify("&d[&bPebbleShop&d] &cInvalid input. Try again or type cancel."));
            if (menu.isReopenIfFail()) {
                CHAT_SIGN_INPUTS.put(player.getUniqueId(), menu);
                menu.getFactory().getInputs().put(player, menu);
            } else {
                removeInput(player, menu);
            }
        }
    }
}
