package me.deadlight.ezchestshop.listeners;

import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.data.LanguageManager;
import me.deadlight.ezchestshop.data.ShopContainer;
import me.deadlight.ezchestshop.guis.SettingsGUI;
import me.deadlight.ezchestshop.utils.Utils;
import me.deadlight.ezchestshop.utils.objects.ChatWaitObject;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class ChatListener implements Listener {

    public static HashMap<UUID, ChatWaitObject> chatmap = new HashMap<>();
    public static LanguageManager lm = new LanguageManager();

    public static void updateLM(LanguageManager languageManager) {
        ChatListener.lm = languageManager;
    }

    public static void startPriceEditor(Player player, Block containerBlock) {
        chatmap.put(player.getUniqueId(), new ChatWaitObject("none", "price-buy", containerBlock));
        sendBuyPricePrompt(player);
    }

    private static void sendBuyPricePrompt(Player player) {
        player.sendMessage("");
        player.sendMessage(Utils.colorify("&b&lUpdate Shop Prices &d━━━━━━━━━━━━"));
        player.sendMessage(Utils.colorify("&eStep 1/2 &fType the &aBUY&f price in chat. &7Example: &f1000"));
        player.sendMessage(Utils.colorify("&7Type &cCANCEL&7 to stop."));
        player.sendMessage(Utils.colorify("&8Prompt: &bBuy price"));
    }

    private static void sendSellPricePrompt(Player player) {
        player.sendMessage("");
        player.sendMessage(Utils.colorify("&b&lUpdate Shop Prices &d━━━━━━━━━━━━"));
        player.sendMessage(Utils.colorify("&eStep 2/2 &fType the &aSELL&f price in chat. &7Example: &f500"));
        player.sendMessage(Utils.colorify("&7Type &cCANCEL&7 to stop."));
        player.sendMessage(Utils.colorify("&8Prompt: &bSell price"));
    }

    @EventHandler
    public void onAsyncChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!chatmap.containsKey(player.getUniqueId())) return;

        event.setCancelled(true);
        ChatWaitObject waitObject = chatmap.get(player.getUniqueId());
        Block waitChest = waitObject.containerBlock;
        if (waitChest == null) {
            chatmap.remove(player.getUniqueId());
            return;
        }

        String type = waitObject.type;
        if (type != null && (type.equalsIgnoreCase("price-buy") || type.equalsIgnoreCase("price-sell"))) {
            handlePriceInput(event, player, waitObject, type);
            return;
        }

        String owneruuid = waitObject.dataContainer.get(new NamespacedKey(EzChestShop.getPlugin(), "owner"), PersistentDataType.STRING);
        if (event.getMessage().equalsIgnoreCase(player.getName())) {
            OfflinePlayer ofplayer = Bukkit.getOfflinePlayer(UUID.fromString(owneruuid));
            if (ofplayer.getName().equalsIgnoreCase(player.getName())) {
                chatmap.remove(player.getUniqueId());
                player.sendMessage(lm.selfAdmin());
                return;
            }
        }

        Block chest = waitObject.containerBlock;
        chatmap.put(player.getUniqueId(), new ChatWaitObject(event.getMessage(), type, chest, waitObject.dataContainer));
        SettingsGUI guiInstance = new SettingsGUI();

        if (checkIfPlayerExists(event.getMessage())) {
            if (type.equalsIgnoreCase("add")) {
                chatmap.remove(player.getUniqueId());
                EzChestShop.getScheduler().scheduleSyncDelayedTask(() -> {
                    addThePlayer(event.getMessage(), chest, player);
                    guiInstance.showGUI(player, chest, false);
                }, 0);
            } else {
                chatmap.remove(player.getUniqueId());
                EzChestShop.getScheduler().scheduleSyncDelayedTask(() -> {
                    removeThePlayer(event.getMessage(), chest, player);
                    guiInstance.showGUI(player, chest, false);
                }, 0);
            }
        } else {
            player.sendMessage(lm.noPlayer());
            chatmap.remove(player.getUniqueId());
        }
    }

    private void handlePriceInput(AsyncPlayerChatEvent event, Player player, ChatWaitObject waitObject, String type) {
        String input = event.getMessage() == null ? "" : event.getMessage().trim();
        if (input.equalsIgnoreCase("cancel") || input.equalsIgnoreCase("[cancel]")) {
            chatmap.remove(player.getUniqueId());
            player.sendMessage(ChatColor.RED + "Price update cancelled.");
            return;
        }

        if (!Utils.isNumeric(input)) {
            player.sendMessage(ChatColor.RED + "Please type a valid number, or type CANCEL to stop.");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(input);
        } catch (NumberFormatException exception) {
            player.sendMessage(ChatColor.RED + "Please type a valid number, or type CANCEL to stop.");
            return;
        }

        if (amount < 0) {
            player.sendMessage(lm.negativePrice());
            return;
        }

        Block chest = waitObject.containerBlock;
        EzChestShop.getScheduler().scheduleSyncDelayedTask(() -> {
            SettingsGUI settingsGUI = new SettingsGUI();
            boolean isBuy = type.equalsIgnoreCase("price-buy");
            if (!settingsGUI.changePrice(chest.getState(), isBuy, amount, player, chest)) {
                return;
            }

            ShopContainer.changePrice(chest.getState(), amount, isBuy);
            player.sendMessage(isBuy ? lm.shopBuyPriceUpdated() : lm.shopSellPriceUpdated());

            if (isBuy) {
                PersistentDataContainer freshData = ((TileState) chest.getState()).getPersistentDataContainer();
                chatmap.put(player.getUniqueId(), new ChatWaitObject(String.valueOf(amount), "price-sell", chest, freshData));
                sendSellPricePrompt(player);
            } else {
                chatmap.remove(player.getUniqueId());
                player.sendMessage(Utils.colorify("&aPebbleShop prices updated. Returning to settings..."));
                settingsGUI.showGUI(player, chest, false);
            }
        }, 0);
    }

    public boolean checkIfPlayerExists(String name) {
        Player player = Bukkit.getPlayer(name);
        if (player != null) {
            if (player.isOnline()) {
                return true;
            } else {
                OfflinePlayer thaPlayer = Bukkit.getOfflinePlayer(name);
                return thaPlayer.hasPlayedBefore();
            }
        } else {
            OfflinePlayer thaPlayer = Bukkit.getOfflinePlayer(name);
            return thaPlayer.hasPlayedBefore();
        }
    }

    public void addThePlayer(String answer, Block chest, Player player) {
        UUID answerUUID = Bukkit.getOfflinePlayer(answer).getUniqueId();
        List<UUID> admins = Utils.getAdminsList(((TileState)chest.getState()).getPersistentDataContainer());
        if (!admins.contains(answerUUID)) {
            admins.add(answerUUID);
            String adminsString = convertListUUIDtoString(admins);
            TileState state = ((TileState)chest.getState());
            PersistentDataContainer data = state.getPersistentDataContainer();
            data.set(new NamespacedKey(EzChestShop.getPlugin(), "admins"), PersistentDataType.STRING, adminsString);
            state.update();
            ShopContainer.getShopSettings(chest.getLocation()).setAdmins(adminsString);
            player.sendMessage(lm.sucAdminAdded(answer));
        } else {
            player.sendMessage(lm.alreadyAdmin());
        }
    }

    public void removeThePlayer(String answer, Block chest, Player player) {
        UUID answerUUID = Bukkit.getOfflinePlayer(answer).getUniqueId();
        List<UUID> admins = Utils.getAdminsList(((TileState)chest.getState()).getPersistentDataContainer());
        if (admins.contains(answerUUID)) {
            TileState state = ((TileState)chest.getState());
            admins.remove(answerUUID);
            if (admins.size() == 0) {
                PersistentDataContainer data = state.getPersistentDataContainer();
                data.set(new NamespacedKey(EzChestShop.getPlugin(), "admins"), PersistentDataType.STRING, "none");
                state.update();
                player.sendMessage(lm.sucAdminRemoved(answer));
                return;
            }
            String adminsString = convertListUUIDtoString(admins);
            PersistentDataContainer data = state.getPersistentDataContainer();
            data.set(new NamespacedKey(EzChestShop.getPlugin(), "admins"), PersistentDataType.STRING, adminsString);
            state.update();
            ShopContainer.getShopSettings(chest.getLocation()).setAdmins(adminsString);
            player.sendMessage(lm.sucAdminRemoved(answer));
        } else {
            player.sendMessage(lm.notInAdminList());
        }
    }

    public String convertListUUIDtoString(List<UUID> uuidList) {
        StringBuilder finalString = new StringBuilder();
        boolean first = false;
        if (uuidList.size() == 0) {
            return "none";
        }
        for (UUID uuid : uuidList) {
            if (first) {
                finalString.append("@").append(uuid.toString());
            } else {
                first = true;
                finalString = new StringBuilder(uuid.toString());
            }
        }
        if (finalString.toString().equalsIgnoreCase("")) {
            finalString = new StringBuilder("none");
        }
        return finalString.toString();
    }
}
