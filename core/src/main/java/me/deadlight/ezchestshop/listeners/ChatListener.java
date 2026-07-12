package me.deadlight.ezchestshop.listeners;

import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.data.Config;
import me.deadlight.ezchestshop.data.LanguageManager;
import me.deadlight.ezchestshop.data.ShopContainer;
import me.deadlight.ezchestshop.guis.MultiItemShopGUI;
import me.deadlight.ezchestshop.guis.SettingsGUI;
import me.deadlight.ezchestshop.utils.ShopItemUtils;
import me.deadlight.ezchestshop.utils.Utils;
import me.deadlight.ezchestshop.utils.objects.ChatWaitObject;
import me.deadlight.ezchestshop.utils.objects.ShopOffer;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChatListener implements Listener {

    public static final Map<UUID, ChatWaitObject> chatmap = new ConcurrentHashMap<>();
    public static LanguageManager lm = new LanguageManager();

    public static void updateLM(LanguageManager languageManager) {
        ChatListener.lm = languageManager;
    }

    /**
     * Compatibility entry point used by the inherited settings menu. A single
     * listing opens directly; multi-item shops return to the listing manager so
     * the owner can choose which exact item to edit.
     */
    public static void startPriceEditor(Player player, Block containerBlock) {
        List<ShopOffer> offers = ShopItemUtils.getOffers(containerBlock);
        if (offers.isEmpty()) {
            player.sendMessage(Utils.colorify("&eAdd a listing before setting prices."));
            new MultiItemShopGUI().showGUI(player, containerBlock);
            return;
        }
        if (offers.size() > 1) {
            player.sendMessage(Utils.colorify("&eSelect the exact listing whose prices you want to edit."));
            new MultiItemShopGUI().showGUI(player, containerBlock);
            return;
        }
        startOfferPriceEditor(player, containerBlock, offers.get(0).getId());
    }

    public static void startOfferPriceEditor(Player player, Block containerBlock, String offerId) {
        if (!canManageListings(player, containerBlock)) {
            player.sendMessage(Utils.colorify("&cYou no longer have permission to edit this shop."));
            return;
        }
        ShopOffer offer = ShopItemUtils.getOffer(containerBlock, offerId);
        if (offer == null) {
            player.sendMessage(Utils.colorify("&cThat listing no longer exists."));
            return;
        }
        player.closeInventory();
        chatmap.put(player.getUniqueId(), new ChatWaitObject("none", "offer-price-buy:" + offerId, containerBlock));
        sendOfferBuyPricePrompt(player, offer);
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

    private static void sendOfferBuyPricePrompt(Player player, ShopOffer offer) {
        player.sendMessage("");
        player.sendMessage(Utils.colorify("&b&lListing Prices &d━━━━━━━━━━━━"));
        player.sendMessage(Utils.colorify("&7Item: &f" + safeItemName(offer)));
        player.sendMessage(Utils.colorify("&eStep 1/2 &fType the price customers pay to &aBUY &fone item."));
        player.sendMessage(Utils.colorify("&7Current: &a$" + offer.getBuyPrice() + " &8• &7Use &f0 &7to disable"));
        player.sendMessage(Utils.colorify("&7Type &cCANCEL&7 to stop."));
    }

    private static void sendOfferSellPricePrompt(Player player, ShopOffer offer, double buyPrice) {
        player.sendMessage("");
        player.sendMessage(Utils.colorify("&b&lListing Prices &d━━━━━━━━━━━━"));
        player.sendMessage(Utils.colorify("&7Item: &f" + safeItemName(offer)));
        player.sendMessage(Utils.colorify("&eStep 2/2 &fType the price customers receive when they &cSELL &fone item."));
        player.sendMessage(Utils.colorify("&7New buy price: &a$" + buyPrice));
        player.sendMessage(Utils.colorify("&7Current sell price: &c$" + offer.getSellPrice() + " &8• &7Use &f0 &7to disable"));
        player.sendMessage(Utils.colorify("&7Type &cCANCEL&7 to stop."));
    }

    @EventHandler
    public void onAsyncChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        ChatWaitObject waitObject = chatmap.remove(player.getUniqueId());
        if (waitObject == null) {
            return;
        }

        event.setCancelled(true);
        String input = event.getMessage() == null ? "" : event.getMessage().trim();
        EzChestShop.getScheduler().scheduleSyncDelayedTask(
                () -> processChatInput(player, waitObject, input), 0);
    }

    private void processChatInput(Player player, ChatWaitObject waitObject, String input) {
        if (!player.isOnline() || waitObject == null || waitObject.containerBlock == null
                || !(waitObject.containerBlock.getState() instanceof TileState)) {
            return;
        }

        Block chest = waitObject.containerBlock;
        String type = waitObject.type == null ? "" : waitObject.type;
        if (type.startsWith("offer-price-buy:") || type.startsWith("offer-price-sell:")) {
            handleOfferPriceInput(player, waitObject, type, input);
            return;
        }
        if (type.equalsIgnoreCase("price-buy") || type.equalsIgnoreCase("price-sell")) {
            handleLegacyPriceInput(player, waitObject, type, input);
            return;
        }

        if (!canManageSettings(player, chest)) {
            player.sendMessage(Utils.colorify("&cYou no longer have permission to manage shop staff."));
            return;
        }
        if (input.equalsIgnoreCase("cancel") || input.equalsIgnoreCase("[cancel]")) {
            player.sendMessage(ChatColor.RED + "Shop staff update cancelled.");
            new MultiItemShopGUI().showStaffManager(player, chest);
            return;
        }
        if (!type.equalsIgnoreCase("add") && !type.equalsIgnoreCase("remove")) {
            return;
        }
        if (!checkIfPlayerExists(input)) {
            player.sendMessage(lm.noPlayer());
            new MultiItemShopGUI().showStaffManager(player, chest);
            return;
        }
        if (type.equalsIgnoreCase("add")) {
            addThePlayer(input, chest, player);
        } else {
            removeThePlayer(input, chest, player);
        }
        new MultiItemShopGUI().showStaffManager(player, chest);
    }

    private void handleOfferPriceInput(Player player, ChatWaitObject waitObject, String type, String input) {
        String offerId = type.substring(type.indexOf(':') + 1);
        Block chest = waitObject.containerBlock;
        if (!canManageListings(player, chest)) {
            player.sendMessage(Utils.colorify("&cYou no longer have permission to edit this shop."));
            return;
        }

        if (input.equalsIgnoreCase("cancel") || input.equalsIgnoreCase("[cancel]")) {
            player.sendMessage(ChatColor.RED + "Listing price update cancelled.");
            new MultiItemShopGUI().showOfferEditor(player, chest, offerId);
            return;
        }

        Double amount = parsePrice(input);
        if (amount == null) {
            chatmap.put(player.getUniqueId(), waitObject);
            player.sendMessage(ChatColor.RED + "Please type a valid non-negative number, or type CANCEL.");
            return;
        }

        ShopOffer current = ShopItemUtils.getOffer(chest, offerId);
        if (current == null) {
            player.sendMessage(ChatColor.RED + "That listing no longer exists.");
            return;
        }

        if (type.startsWith("offer-price-buy:")) {
            ChatWaitObject next = new ChatWaitObject(String.valueOf(amount),
                    "offer-price-sell:" + offerId, chest);
            chatmap.put(player.getUniqueId(), next);
            sendOfferSellPricePrompt(player, current, amount);
            return;
        }

        Double buyPrice = parsePrice(waitObject.answer);
        if (buyPrice == null) {
            ChatWaitObject restart = new ChatWaitObject("none", "offer-price-buy:" + offerId, chest);
            chatmap.put(player.getUniqueId(), restart);
            player.sendMessage(ChatColor.RED + "The saved buy price was invalid. Starting over.");
            sendOfferBuyPricePrompt(player, current);
            return;
        }

        double sellPrice = amount;
        if (Config.settings_buy_greater_than_sell && buyPrice != 0D && sellPrice > buyPrice) {
            chatmap.put(player.getUniqueId(), waitObject);
            player.sendMessage(lm.buyGreaterThanSellRequired());
            return;
        }
        if (!ShopItemUtils.updateOfferPrices(chest, offerId, buyPrice, sellPrice)) {
            chatmap.put(player.getUniqueId(), waitObject);
            player.sendMessage(ChatColor.RED + "PebbleShop could not save those listing prices.");
            return;
        }
        player.sendMessage(Utils.colorify("&aListing prices updated: &fBuy $" + buyPrice
                + " &8• &fSell $" + sellPrice));
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.25f);
        new MultiItemShopGUI().showOfferEditor(player, chest, offerId);
    }

    private void handleLegacyPriceInput(Player player, ChatWaitObject waitObject, String type, String input) {
        Block chest = waitObject.containerBlock;
        if (!canManageListings(player, chest)) {
            player.sendMessage(Utils.colorify("&cYou no longer have permission to edit this shop."));
            return;
        }
        if (input.equalsIgnoreCase("cancel") || input.equalsIgnoreCase("[cancel]")) {
            player.sendMessage(ChatColor.RED + "Price update cancelled.");
            new MultiItemShopGUI().showGUI(player, chest);
            return;
        }

        Double amount = parsePrice(input);
        if (amount == null) {
            chatmap.put(player.getUniqueId(), waitObject);
            player.sendMessage(ChatColor.RED + "Please type a valid non-negative number, or type CANCEL.");
            return;
        }
        if (type.equalsIgnoreCase("price-buy")) {
            ChatWaitObject next = new ChatWaitObject(String.valueOf(amount), "price-sell", chest);
            chatmap.put(player.getUniqueId(), next);
            sendSellPricePrompt(player);
            return;
        }

        Double buyPrice = parsePrice(waitObject.answer);
        if (buyPrice == null) {
            ChatWaitObject restart = new ChatWaitObject("none", "price-buy", chest);
            chatmap.put(player.getUniqueId(), restart);
            player.sendMessage(ChatColor.RED + "The saved buy price was invalid. Starting over.");
            sendBuyPricePrompt(player);
            return;
        }

        SettingsGUI settingsGUI = new SettingsGUI();
        if (!settingsGUI.changePrices(chest, player, buyPrice, amount)) {
            chatmap.put(player.getUniqueId(), waitObject);
            player.sendMessage(ChatColor.YELLOW + "Let's try those prices again.");
            return;
        }
        player.sendMessage(Utils.colorify("&aPebbleShop prices updated."));
        new MultiItemShopGUI().showGUI(player, chest);
    }

    private Double parsePrice(String input) {
        if (!Utils.isNumeric(input)) {
            return null;
        }
        try {
            double amount = Double.parseDouble(input);
            if (amount < 0D || Double.isInfinite(amount) || Double.isNaN(amount)) {
                return null;
            }
            return amount;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static boolean canManageListings(Player player, Block chest) {
        if (player == null || chest == null || !(chest.getState() instanceof TileState)) {
            return false;
        }
        PersistentDataContainer data = ((TileState) chest.getState()).getPersistentDataContainer();
        String ownerId = data.get(new NamespacedKey(EzChestShop.getPlugin(), "owner"), PersistentDataType.STRING);
        return (ownerId != null && ownerId.equalsIgnoreCase(player.getUniqueId().toString()))
                || player.isOp()
                || player.hasPermission("ecs.admin")
                || Utils.getAdminsList(data).contains(player.getUniqueId());
    }

    private static boolean canManageSettings(Player player, Block chest) {
        if (player == null || chest == null || !(chest.getState() instanceof TileState)) {
            return false;
        }
        PersistentDataContainer data = ((TileState) chest.getState()).getPersistentDataContainer();
        String ownerId = data.get(new NamespacedKey(EzChestShop.getPlugin(), "owner"), PersistentDataType.STRING);
        return (ownerId != null && ownerId.equalsIgnoreCase(player.getUniqueId().toString()))
                || player.isOp()
                || player.hasPermission("ecs.admin");
    }

    public boolean checkIfPlayerExists(String name) {
        Player player = Bukkit.getPlayer(name);
        if (player != null && player.isOnline()) {
            return true;
        }
        return Bukkit.getOfflinePlayer(name).hasPlayedBefore();
    }

    public void addThePlayer(String answer, Block chest, Player player) {
        if (!canManageSettings(player, chest)) {
            player.sendMessage(Utils.colorify("&cYou no longer have permission to manage shop staff."));
            return;
        }
        UUID answerUUID = Bukkit.getOfflinePlayer(answer).getUniqueId();
        List<UUID> admins = Utils.getAdminsList(((TileState) chest.getState()).getPersistentDataContainer());
        if (!admins.contains(answerUUID)) {
            admins.add(answerUUID);
            String adminsString = convertListUUIDtoString(admins);
            TileState state = (TileState) chest.getState();
            PersistentDataContainer data = state.getPersistentDataContainer();
            data.set(new NamespacedKey(EzChestShop.getPlugin(), "admins"), PersistentDataType.STRING, adminsString);
            state.update();
            if (ShopContainer.getShopSettings(chest.getLocation()) != null) {
                ShopContainer.getShopSettings(chest.getLocation()).setAdmins(adminsString);
            }
            player.sendMessage(lm.sucAdminAdded(answer));
        } else {
            player.sendMessage(lm.alreadyAdmin());
        }
    }

    public void removeThePlayer(String answer, Block chest, Player player) {
        if (!canManageSettings(player, chest)) {
            player.sendMessage(Utils.colorify("&cYou no longer have permission to manage shop staff."));
            return;
        }
        UUID answerUUID = Bukkit.getOfflinePlayer(answer).getUniqueId();
        List<UUID> admins = Utils.getAdminsList(((TileState) chest.getState()).getPersistentDataContainer());
        if (admins.contains(answerUUID)) {
            TileState state = (TileState) chest.getState();
            admins.remove(answerUUID);
            String adminsString = convertListUUIDtoString(admins);
            PersistentDataContainer data = state.getPersistentDataContainer();
            data.set(new NamespacedKey(EzChestShop.getPlugin(), "admins"), PersistentDataType.STRING, adminsString);
            state.update();
            if (ShopContainer.getShopSettings(chest.getLocation()) != null) {
                ShopContainer.getShopSettings(chest.getLocation()).setAdmins(adminsString);
            }
            player.sendMessage(lm.sucAdminRemoved(answer));
        } else {
            player.sendMessage(lm.notInAdminList());
        }
    }

    public String convertListUUIDtoString(List<UUID> uuidList) {
        if (uuidList == null || uuidList.isEmpty()) {
            return "none";
        }
        StringBuilder finalString = new StringBuilder();
        for (UUID uuid : uuidList) {
            if (finalString.length() > 0) {
                finalString.append('@');
            }
            finalString.append(uuid.toString());
        }
        return finalString.length() == 0 ? "none" : finalString.toString();
    }

    private static String safeItemName(ShopOffer offer) {
        try {
            String name = ChatColor.stripColor(Utils.getFinalItemName(offer.getItem()));
            return name == null || name.trim().isEmpty() ? offer.getItem().getType().name() : name;
        } catch (Throwable ignored) {
            return offer.getItem().getType().name();
        }
    }
}
