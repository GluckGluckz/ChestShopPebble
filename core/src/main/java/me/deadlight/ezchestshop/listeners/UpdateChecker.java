package me.deadlight.ezchestshop.listeners;

import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.data.Config;
import me.deadlight.ezchestshop.data.LanguageManager;
import me.deadlight.ezchestshop.data.gui.ContainerGui;
import me.deadlight.ezchestshop.data.gui.ContainerGuiItem;
import me.deadlight.ezchestshop.data.gui.GuiData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class UpdateChecker implements Listener {

    LanguageManager lm = new LanguageManager();

    private static String newVersion = EzChestShop.getPlugin().getDescription().getVersion();

    private static boolean isSpigotUpdateAvailable;
    public static boolean isSpigotUpdateAvailable() {
        return isSpigotUpdateAvailable;
    }

    private static boolean isGuiUpdateAvailable;
    public static boolean isGuiUpdateAvailable() {
        return isGuiUpdateAvailable;
    }

    private static final HashMap<GuiData.GuiType, List<List<String>>> overlappingItems = new HashMap<>();
    private static final HashMap<GuiData.GuiType, Integer> requiredOverflowRows = new HashMap<>();

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (event.getPlayer().isOp()) {
            if (Config.notify_updates && isSpigotUpdateAvailable) {
                EzChestShop.getScheduler().runTaskLater(EzChestShop.getPlugin(), () -> {
                    event.getPlayer().spigot().sendMessage(lm.updateNotification(EzChestShop.getPlugin().getDescription().getVersion(), newVersion));
                }, 10L);
            }
            if (isGuiUpdateAvailable) {
                if (Config.notify_overflowing_gui_items && !requiredOverflowRows.isEmpty()) {
                    EzChestShop.getScheduler().runTaskLater(EzChestShop.getPlugin(), () -> {
                        event.getPlayer().spigot().sendMessage(lm.overflowingGuiItemsNotification(requiredOverflowRows));
                    }, 10L);
                }
                if (Config.notify_overlapping_gui_items && !overlappingItems.isEmpty()) {
                    EzChestShop.getScheduler().runTaskLater(EzChestShop.getPlugin(), () -> {
                        event.getPlayer().spigot().sendMessage(lm.overlappingItemsNotification(overlappingItems));
                    }, 10L);
                }
            }
        }
    }

    public void check() {
        isSpigotUpdateAvailable = checkUpdate();
        checkGuiUpdate();
    }

    public void resetGuiCheck() {
        overlappingItems.clear();
        requiredOverflowRows.clear();
        isGuiUpdateAvailable = false;
        checkGuiUpdate();
    }

    public static int getGuiOverflow(GuiData.GuiType guiType) {
        if (requiredOverflowRows.containsKey(guiType)) {
            return requiredOverflowRows.get(guiType);
        } else {
            return -1;
        }
    }

    /**
     * PebbleShop is privately branded for Pebble Quest, so it must not check the public
     * EzChestShop Spigot resource and warn operators about unrelated upstream releases.
     */
    private boolean checkUpdate() {
        newVersion = EzChestShop.getPlugin().getDescription().getVersion();
        return false;
    }

    private void checkGuiUpdate() {
        for (GuiData.GuiType type : GuiData.GuiType.values()) {
            ContainerGui container = GuiData.getViaType(type);
            if (container == null) continue;

            HashMap<Integer, List<String>> items = new HashMap<>();

            container.getItemKeys().forEach(key -> {
                ContainerGuiItem item = container.getItem(key);
                if (item == null) return;
                if (item.getRow() > container.getRows()) {
                    Integer row = requiredOverflowRows.get(type);
                    if (row == null) {
                        row = item.getRow();
                    } else {
                        row = Math.max(row, item.getRow());
                    }
                    requiredOverflowRows.put(type, row);
                    isGuiUpdateAvailable = true;
                }
                if (items.containsKey(item.getSlot())) {
                    List<String> list = new ArrayList<>(items.get(item.getSlot()));
                    list.add(key);
                    items.put(item.getSlot(), list);
                } else {
                    items.put(item.getSlot(), Arrays.asList(key));
                }
            });

            items.entrySet().removeIf(entry -> entry.getValue().size() == 1);

            if (GuiData.getAllowedDefaultOverlappingItems(type) != null) {
                List<List<String>> overlapping = items.entrySet().stream().filter(entry -> {
                    List<String> list = entry.getValue();
                    if (list.isEmpty()) return false;

                    List<String> containing = GuiData.getAllowedDefaultOverlappingItems(type).stream().flatMap(List::stream).collect(Collectors.toList());
                    List<String> subtractList = new ArrayList<>(list);
                    subtractList.removeAll(containing);
                    if (!subtractList.isEmpty()) {
                        return true;
                    }

                    AtomicBoolean returnValue = new AtomicBoolean(false);
                    GuiData.getAllowedDefaultOverlappingItems(type).forEach(allowedList -> {
                        List<String> subtractList2 = new ArrayList<>(list);
                        if (!Collections.disjoint(allowedList, subtractList2)) {
                            subtractList2.removeAll(allowedList);
                            if (!subtractList2.isEmpty()) {
                                returnValue.set(true);
                            }
                        }
                    });
                    return returnValue.get();
                }).map(entry -> entry.getValue()).collect(Collectors.toList());

                if (!overlapping.isEmpty()) {
                    overlappingItems.put(type, overlapping);
                    isGuiUpdateAvailable = true;
                }
            }
        }
    }
}
