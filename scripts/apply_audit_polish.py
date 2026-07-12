#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str, text: str) -> None:
    (ROOT / path).write_text(text, encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


def regex_once(text: str, pattern: str, replacement: str, label: str, flags=re.S) -> str:
    updated, count = re.subn(pattern, replacement, text, count=1, flags=flags)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one regex match, found {count}")
    return updated


# ---------------------------------------------------------------------------
# ShopOffer: never allow NaN/Infinity to enter persisted prices.
# ---------------------------------------------------------------------------
path = "core/src/main/java/me/deadlight/ezchestshop/utils/objects/ShopOffer.java"
text = read(path)
text = replace_once(text,
    "        this.buyPrice = Math.max(0D, buyPrice);\n        this.sellPrice = Math.max(0D, sellPrice);",
    "        this.buyPrice = sanitizePrice(buyPrice);\n        this.sellPrice = sanitizePrice(sellPrice);",
    "ShopOffer constructor price sanitization")
text = replace_once(text,
    "        this.buyPrice = Math.max(0D, buyPrice);",
    "        this.buyPrice = sanitizePrice(buyPrice);",
    "ShopOffer buy setter sanitization")
text = replace_once(text,
    "        this.sellPrice = Math.max(0D, sellPrice);",
    "        this.sellPrice = sanitizePrice(sellPrice);",
    "ShopOffer sell setter sanitization")
text = replace_once(text,
    "    public boolean matches(ItemStack other) {",
    "    private static double sanitizePrice(double price) {\n"
    "        return Double.isFinite(price) && price >= 0D ? price : 0D;\n"
    "    }\n\n"
    "    public boolean matches(ItemStack other) {",
    "ShopOffer sanitize helper")
write(path, text)


# ---------------------------------------------------------------------------
# ShopItemUtils: validate complete payloads, reject corrupt data, and keep the
# inherited global setting cache synchronized when prices enable directions.
# ---------------------------------------------------------------------------
path = "core/src/main/java/me/deadlight/ezchestshop/utils/ShopItemUtils.java"
text = read(path)
text = replace_once(text,
    "import me.deadlight.ezchestshop.utils.objects.ShopOffer;",
    "import me.deadlight.ezchestshop.utils.objects.ShopOffer;\nimport me.deadlight.ezchestshop.utils.objects.ShopSettings;",
    "ShopItemUtils ShopSettings import")
text = replace_once(text,
    "import java.util.Collections;\nimport java.util.List;",
    "import java.util.Collections;\nimport java.util.HashSet;\nimport java.util.List;\nimport java.util.Set;",
    "ShopItemUtils collection imports")
text = replace_once(text,
    "        if (buyPrice < 0D || sellPrice < 0D) {",
    "        if (!Double.isFinite(buyPrice) || !Double.isFinite(sellPrice)\n"
    "                || buyPrice < 0D || sellPrice < 0D) {",
    "ShopItemUtils finite price validation")
text = replace_once(text,
    "        state.update();\n        return true;\n    }\n\n    public static boolean toggleOfferBuying",
    "        state.update();\n\n"
    "        ShopSettings settings = ShopContainer.getShopSettings(containerBlock.getLocation());\n"
    "        if (settings != null) {\n"
    "            if (buyPrice > 0D && settings.isDbuy()) {\n"
    "                settings.setDbuy(false);\n"
    "            }\n"
    "            if (sellPrice > 0D && settings.isDsell()) {\n"
    "                settings.setDsell(false);\n"
    "            }\n"
    "        }\n"
    "        return true;\n"
    "    }\n\n"
    "    public static boolean toggleOfferBuying",
    "ShopItemUtils global settings synchronization")
text = replace_once(text,
    "        List<ShopOffer> safeOffers = offers == null ? new ArrayList<>() : new ArrayList<>(offers);\n"
    "        String payload = encodeOffers(safeOffers);",
    "        List<ShopOffer> safeOffers = offers == null ? new ArrayList<>() : new ArrayList<>(offers);\n"
    "        if (!validateOffers(containerBlock, safeOffers)) {\n"
    "            return false;\n"
    "        }\n"
    "        String payload = encodeOffers(safeOffers);",
    "ShopItemUtils save payload validation")
text = replace_once(text,
    "    public static boolean isEmptyShopItem(PersistentDataContainer data) {",
    "    private static boolean validateOffers(Block containerBlock, List<ShopOffer> offers) {\n"
    "        int capacity = getOfferCapacity(containerBlock);\n"
    "        if (capacity <= 0 || offers.size() > capacity) {\n"
    "            return false;\n"
    "        }\n\n"
    "        Set<String> ids = new HashSet<>();\n"
    "        List<ItemStack> identities = new ArrayList<>();\n"
    "        for (ShopOffer offer : offers) {\n"
    "            if (offer == null || offer.getId() == null || offer.getId().trim().isEmpty()) {\n"
    "                return false;\n"
    "            }\n"
    "            if (!ids.add(offer.getId())) {\n"
    "                return false;\n"
    "            }\n"
    "            ItemStack item = offer.getItem();\n"
    "            if (!isValidListingItem(containerBlock, item)) {\n"
    "                return false;\n"
    "            }\n"
    "            for (ItemStack identity : identities) {\n"
    "                if (Utils.isSimilar(identity, item)) {\n"
    "                    return false;\n"
    "                }\n"
    "            }\n"
    "            identities.add(item);\n"
    "            if (!Double.isFinite(offer.getBuyPrice()) || !Double.isFinite(offer.getSellPrice())\n"
    "                    || offer.getBuyPrice() < 0D || offer.getSellPrice() < 0D) {\n"
    "                return false;\n"
    "            }\n"
    "            if (offer.getBuyPrice() <= 0D) {\n"
    "                offer.setBuyingEnabled(false);\n"
    "            }\n"
    "            if (offer.getSellPrice() <= 0D) {\n"
    "                offer.setSellingEnabled(false);\n"
    "            }\n"
    "        }\n"
    "        return true;\n"
    "    }\n\n"
    "    public static boolean isEmptyShopItem(PersistentDataContainer data) {",
    "ShopItemUtils validateOffers helper")
text = replace_once(text,
    "            List<ShopOffer> offers = new ArrayList<>();\n"
    "            for (Object entry : (JSONArray) parsed) {",
    "            List<ShopOffer> offers = new ArrayList<>();\n"
    "            Set<String> ids = new HashSet<>();\n"
    "            for (Object entry : (JSONArray) parsed) {",
    "ShopItemUtils decode ID set")
text = replace_once(text,
    "                String encodedItem = stringValue(object.get(\"item\"));\n"
    "                ItemStack item = Utils.decodeItem(encodedItem);",
    "                String encodedItem = stringValue(object.get(\"item\"));\n"
    "                if (encodedItem.trim().isEmpty()) {\n"
    "                    continue;\n"
    "                }\n"
    "                ItemStack item = Utils.decodeItem(encodedItem);",
    "ShopItemUtils empty encoded item guard")
text = replace_once(text,
    "                double buy = numberValue(object.get(\"buy\"));\n"
    "                double sell = numberValue(object.get(\"sell\"));\n"
    "                boolean buyEnabled = booleanValue(object.get(\"buyEnabled\"), buy > 0D);\n"
    "                boolean sellEnabled = booleanValue(object.get(\"sellEnabled\"), sell > 0D);\n"
    "                offers.add(new ShopOffer(id, item, buy, sell, buyEnabled, sellEnabled));",
    "                double buy = numberValue(object.get(\"buy\"));\n"
    "                double sell = numberValue(object.get(\"sell\"));\n"
    "                if (!Double.isFinite(buy) || !Double.isFinite(sell) || buy < 0D || sell < 0D) {\n"
    "                    continue;\n"
    "                }\n"
    "                if (id.trim().isEmpty() || !ids.add(id)) {\n"
    "                    continue;\n"
    "                }\n"
    "                boolean duplicateItem = false;\n"
    "                for (ShopOffer existing : offers) {\n"
    "                    if (existing.matches(item)) {\n"
    "                        duplicateItem = true;\n"
    "                        break;\n"
    "                    }\n"
    "                }\n"
    "                if (duplicateItem) {\n"
    "                    continue;\n"
    "                }\n"
    "                boolean buyEnabled = booleanValue(object.get(\"buyEnabled\"), buy > 0D) && buy > 0D;\n"
    "                boolean sellEnabled = booleanValue(object.get(\"sellEnabled\"), sell > 0D) && sell > 0D;\n"
    "                offers.add(new ShopOffer(id, item, buy, sell, buyEnabled, sellEnabled));",
    "ShopItemUtils robust payload decode")
write(path, text)


# ---------------------------------------------------------------------------
# MultiItemShopGUI: always resolve the current listing at click time, prevent
# view-only permissions from opening stock, and reserve financial/staff settings
# for the owner or a full shop administrator.
# ---------------------------------------------------------------------------
path = "core/src/main/java/me/deadlight/ezchestshop/guis/MultiItemShopGUI.java"
text = read(path)
text = replace_once(text,
    "        if (canManage) {\n"
    "            addManagementControls(gui, player, containerBlock);\n"
    "        } else if (player.hasPermission(\"ecs.admin.view\")) {\n"
    "            addStorageButton(gui, player, containerBlock, 6, 8);\n"
    "        }",
    "        if (canManage) {\n"
    "            addManagementControls(gui, player, containerBlock);\n"
    "        }",
    "GUI remove view-only storage access")
text = text.replace(
    "            handleBuy(player, containerBlock, data(containerBlock), owner, offer, 1, adminShop);",
    "            executeTrade(player, containerBlock, offerId, 1, true);")
text = text.replace(
    "            handleBuy(player, containerBlock, data(containerBlock), owner, offer, stackAmount, adminShop);",
    "            executeTrade(player, containerBlock, offerId, stackAmount, true);")
text = text.replace(
    "            handleSell(player, containerBlock, data(containerBlock), owner, offer, 1, adminShop);",
    "            executeTrade(player, containerBlock, offerId, 1, false);")
text = text.replace(
    "            handleSell(player, containerBlock, data(containerBlock), owner, offer, stackAmount, adminShop);",
    "            executeTrade(player, containerBlock, offerId, stackAmount, false);")
text = regex_once(text,
    r"    private void handleBuy\(Player player, Block containerBlock, PersistentDataContainer data,.*?\n    private String buyDisabledReason",
    "    private void executeTrade(Player player, Block containerBlock, String offerId, int amount, boolean buying) {\n"
    "        if (!isValidShop(containerBlock) || amount <= 0) {\n"
    "            player.sendMessage(Utils.colorify(\"&cThat shop is no longer available.\"));\n"
    "            return;\n"
    "        }\n\n"
    "        ShopOffer current = ShopItemUtils.getOffer(containerBlock, offerId);\n"
    "        if (current == null) {\n"
    "            player.sendMessage(Utils.colorify(\"&cThat listing no longer exists.\"));\n"
    "            showGUI(player, containerBlock);\n"
    "            return;\n"
    "        }\n\n"
    "        PersistentDataContainer currentData = data(containerBlock);\n"
    "        OfflinePlayer currentOwner = getOwner(currentData);\n"
    "        boolean currentAdminShop = flag(currentData, \"adminshop\", false);\n"
    "        String disabledReason = buying\n"
    "                ? buyDisabledReason(player, currentData, currentOwner, current, currentAdminShop)\n"
    "                : sellDisabledReason(player, currentData, currentOwner, current, currentAdminShop);\n"
    "        if (disabledReason != null) {\n"
    "            player.sendMessage(Utils.colorify(\"&c\" + disabledReason));\n"
    "            showTradeMenu(player, containerBlock, offerId);\n"
    "            return;\n"
    "        }\n\n"
    "        double unitPrice = buying ? current.getBuyPrice() : current.getSellPrice();\n"
    "        double total = unitPrice * amount;\n"
    "        if (!Double.isFinite(total) || total < 0D) {\n"
    "            player.sendMessage(Utils.colorify(\"&cThis transaction has an invalid total price.\"));\n"
    "            return;\n"
    "        }\n\n"
    "        if (buying) {\n"
    "            if (currentAdminShop) {\n"
    "                ShopContainer.buyServerItem(containerBlock, total, amount, player, current.getItem(), currentData);\n"
    "            } else {\n"
    "                ShopContainer.buyItem(containerBlock, total, amount, current.getItem(), player, currentOwner, currentData);\n"
    "            }\n"
    "        } else {\n"
    "            if (currentAdminShop) {\n"
    "                ShopContainer.sellServerItem(containerBlock, total, amount, current.getItem(), player, currentData);\n"
    "            } else {\n"
    "                ShopContainer.sellItem(containerBlock, total, amount, current.getItem(), player, currentOwner, currentData);\n"
    "            }\n"
    "        }\n"
    "        refreshTradeLater(player, containerBlock, current.getId());\n"
    "    }\n\n"
    "    private String buyDisabledReason",
    "GUI fresh transaction resolution")
# Restrict both settings entry points.
text = text.replace(
    "        if (!isValidShop(containerBlock) || !canManage(player, data(containerBlock))) {",
    "        if (!isValidShop(containerBlock) || !canManageSettings(player, data(containerBlock))) {",
    2)
# Settings button is owner/full-admin only; staff still retain listing and storage management.
settings_block_pattern = r"        ItemStack settings = namedItem\(Material\.COMPARATOR, \"&b&lShop Settings\",.*?        \}\)\);\n\n        ItemStack add ="
settings_replacement = (
    "        if (canManageSettings(player, data(containerBlock))) {\n"
    "            ItemStack settings = namedItem(Material.COMPARATOR, \"&b&lShop Settings\",\n"
    "                    Arrays.asList(\n"
    "                            \"&7Global buy/sell switches, alerts,\",\n"
    "                            \"&7shared income and shop staff.\",\n"
    "                            \"\",\n"
    "                            \"&eTap to open settings.\"\n"
    "                    ));\n"
    "            gui.setItem(6, 3, new GuiItem(settings, event -> {\n"
    "                event.setCancelled(true);\n"
    "                showSettings(player, containerBlock);\n"
    "            }));\n"
    "        }\n\n"
    "        ItemStack add =")
text = regex_once(text, settings_block_pattern, settings_replacement, "GUI owner-only settings control")
text = replace_once(text,
    "    private void setGlobalFlag(Block containerBlock, String name, boolean value) {",
    "    private boolean canManageSettings(Player player, PersistentDataContainer data) {\n"
    "        String ownerId = data.get(key(\"owner\"), PersistentDataType.STRING);\n"
    "        return (ownerId != null && ownerId.equalsIgnoreCase(player.getUniqueId().toString()))\n"
    "                || player.isOp()\n"
    "                || player.hasPermission(\"ecs.admin\");\n"
    "    }\n\n"
    "    private void setGlobalFlag(Block containerBlock, String name, boolean value) {",
    "GUI settings authorization helper")
write(path, text)


# ---------------------------------------------------------------------------
# ChatListener: AsyncPlayerChatEvent must only capture/cancel input. All Bukkit,
# block, PDC, permissions, and shop mutations are performed on the server thread.
# Revalidate authorization when the prompt is completed.
# ---------------------------------------------------------------------------
path = "core/src/main/java/me/deadlight/ezchestshop/listeners/ChatListener.java"
text = read(path)
text = replace_once(text,
    "import java.util.HashMap;\nimport java.util.List;\nimport java.util.UUID;",
    "import java.util.List;\nimport java.util.Map;\nimport java.util.UUID;\nimport java.util.concurrent.ConcurrentHashMap;",
    "ChatListener concurrent imports")
text = replace_once(text,
    "    public static HashMap<UUID, ChatWaitObject> chatmap = new HashMap<>();",
    "    public static final Map<UUID, ChatWaitObject> chatmap = new ConcurrentHashMap<>();",
    "ChatListener concurrent prompt map")
text = replace_once(text,
    "    public static void startOfferPriceEditor(Player player, Block containerBlock, String offerId) {\n"
    "        ShopOffer offer = ShopItemUtils.getOffer(containerBlock, offerId);",
    "    public static void startOfferPriceEditor(Player player, Block containerBlock, String offerId) {\n"
    "        if (!canManageListings(player, containerBlock)) {\n"
    "            player.sendMessage(Utils.colorify(\"&cYou no longer have permission to edit this shop.\"));\n"
    "            return;\n"
    "        }\n"
    "        ShopOffer offer = ShopItemUtils.getOffer(containerBlock, offerId);",
    "ChatListener price editor authorization")
text = regex_once(text,
    r"    @EventHandler\n    public void onAsyncChat\(AsyncPlayerChatEvent event\) \{.*?\n    private void handleOfferPriceInput",
    "    @EventHandler\n"
    "    public void onAsyncChat(AsyncPlayerChatEvent event) {\n"
    "        Player player = event.getPlayer();\n"
    "        ChatWaitObject waitObject = chatmap.remove(player.getUniqueId());\n"
    "        if (waitObject == null) {\n"
    "            return;\n"
    "        }\n\n"
    "        event.setCancelled(true);\n"
    "        String input = event.getMessage() == null ? \"\" : event.getMessage().trim();\n"
    "        EzChestShop.getScheduler().scheduleSyncDelayedTask(\n"
    "                () -> processChatInput(player, waitObject, input), 0);\n"
    "    }\n\n"
    "    private void processChatInput(Player player, ChatWaitObject waitObject, String input) {\n"
    "        if (!player.isOnline() || waitObject == null || waitObject.containerBlock == null\n"
    "                || !(waitObject.containerBlock.getState() instanceof TileState)) {\n"
    "            return;\n"
    "        }\n\n"
    "        Block chest = waitObject.containerBlock;\n"
    "        String type = waitObject.type == null ? \"\" : waitObject.type;\n"
    "        if (type.startsWith(\"offer-price-buy:\") || type.startsWith(\"offer-price-sell:\")) {\n"
    "            handleOfferPriceInput(player, waitObject, type, input);\n"
    "            return;\n"
    "        }\n"
    "        if (type.equalsIgnoreCase(\"price-buy\") || type.equalsIgnoreCase(\"price-sell\")) {\n"
    "            handleLegacyPriceInput(player, waitObject, type, input);\n"
    "            return;\n"
    "        }\n\n"
    "        if (!canManageSettings(player, chest)) {\n"
    "            player.sendMessage(Utils.colorify(\"&cYou no longer have permission to manage shop staff.\"));\n"
    "            return;\n"
    "        }\n"
    "        if (input.equalsIgnoreCase(\"cancel\") || input.equalsIgnoreCase(\"[cancel]\")) {\n"
    "            player.sendMessage(ChatColor.RED + \"Shop staff update cancelled.\");\n"
    "            new MultiItemShopGUI().showStaffManager(player, chest);\n"
    "            return;\n"
    "        }\n"
    "        if (!type.equalsIgnoreCase(\"add\") && !type.equalsIgnoreCase(\"remove\")) {\n"
    "            return;\n"
    "        }\n"
    "        if (!checkIfPlayerExists(input)) {\n"
    "            player.sendMessage(lm.noPlayer());\n"
    "            new MultiItemShopGUI().showStaffManager(player, chest);\n"
    "            return;\n"
    "        }\n"
    "        if (type.equalsIgnoreCase(\"add\")) {\n"
    "            addThePlayer(input, chest, player);\n"
    "        } else {\n"
    "            removeThePlayer(input, chest, player);\n"
    "        }\n"
    "        new MultiItemShopGUI().showStaffManager(player, chest);\n"
    "    }\n\n"
    "    private void handleOfferPriceInput",
    "ChatListener main-thread dispatch")
text = regex_once(text,
    r"    private void handleOfferPriceInput\(AsyncPlayerChatEvent event, Player player,\n.*?\n    private void handleLegacyPriceInput",
    "    private void handleOfferPriceInput(Player player, ChatWaitObject waitObject, String type, String input) {\n"
    "        String offerId = type.substring(type.indexOf(':') + 1);\n"
    "        Block chest = waitObject.containerBlock;\n"
    "        if (!canManageListings(player, chest)) {\n"
    "            player.sendMessage(Utils.colorify(\"&cYou no longer have permission to edit this shop.\"));\n"
    "            return;\n"
    "        }\n\n"
    "        if (input.equalsIgnoreCase(\"cancel\") || input.equalsIgnoreCase(\"[cancel]\")) {\n"
    "            player.sendMessage(ChatColor.RED + \"Listing price update cancelled.\");\n"
    "            new MultiItemShopGUI().showOfferEditor(player, chest, offerId);\n"
    "            return;\n"
    "        }\n\n"
    "        Double amount = parsePrice(input);\n"
    "        if (amount == null) {\n"
    "            chatmap.put(player.getUniqueId(), waitObject);\n"
    "            player.sendMessage(ChatColor.RED + \"Please type a valid non-negative number, or type CANCEL.\");\n"
    "            return;\n"
    "        }\n\n"
    "        ShopOffer current = ShopItemUtils.getOffer(chest, offerId);\n"
    "        if (current == null) {\n"
    "            player.sendMessage(ChatColor.RED + \"That listing no longer exists.\");\n"
    "            return;\n"
    "        }\n\n"
    "        if (type.startsWith(\"offer-price-buy:\")) {\n"
    "            ChatWaitObject next = new ChatWaitObject(String.valueOf(amount),\n"
    "                    \"offer-price-sell:\" + offerId, chest);\n"
    "            chatmap.put(player.getUniqueId(), next);\n"
    "            sendOfferSellPricePrompt(player, current, amount);\n"
    "            return;\n"
    "        }\n\n"
    "        Double buyPrice = parsePrice(waitObject.answer);\n"
    "        if (buyPrice == null) {\n"
    "            ChatWaitObject restart = new ChatWaitObject(\"none\", \"offer-price-buy:\" + offerId, chest);\n"
    "            chatmap.put(player.getUniqueId(), restart);\n"
    "            player.sendMessage(ChatColor.RED + \"The saved buy price was invalid. Starting over.\");\n"
    "            sendOfferBuyPricePrompt(player, current);\n"
    "            return;\n"
    "        }\n\n"
    "        double sellPrice = amount;\n"
    "        if (Config.settings_buy_greater_than_sell && buyPrice != 0D && sellPrice > buyPrice) {\n"
    "            chatmap.put(player.getUniqueId(), waitObject);\n"
    "            player.sendMessage(lm.buyGreaterThanSellRequired());\n"
    "            return;\n"
    "        }\n"
    "        if (!ShopItemUtils.updateOfferPrices(chest, offerId, buyPrice, sellPrice)) {\n"
    "            chatmap.put(player.getUniqueId(), waitObject);\n"
    "            player.sendMessage(ChatColor.RED + \"PebbleShop could not save those listing prices.\");\n"
    "            return;\n"
    "        }\n"
    "        player.sendMessage(Utils.colorify(\"&aListing prices updated: &fBuy $\" + buyPrice\n"
    "                + \" &8• &fSell $\" + sellPrice));\n"
    "        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.25f);\n"
    "        new MultiItemShopGUI().showOfferEditor(player, chest, offerId);\n"
    "    }\n\n"
    "    private void handleLegacyPriceInput",
    "ChatListener secure listing price flow")
text = regex_once(text,
    r"    private void handleLegacyPriceInput\(AsyncPlayerChatEvent event, Player player,\n.*?\n    private Double parsePrice",
    "    private void handleLegacyPriceInput(Player player, ChatWaitObject waitObject, String type, String input) {\n"
    "        Block chest = waitObject.containerBlock;\n"
    "        if (!canManageListings(player, chest)) {\n"
    "            player.sendMessage(Utils.colorify(\"&cYou no longer have permission to edit this shop.\"));\n"
    "            return;\n"
    "        }\n"
    "        if (input.equalsIgnoreCase(\"cancel\") || input.equalsIgnoreCase(\"[cancel]\")) {\n"
    "            player.sendMessage(ChatColor.RED + \"Price update cancelled.\");\n"
    "            new MultiItemShopGUI().showGUI(player, chest);\n"
    "            return;\n"
    "        }\n\n"
    "        Double amount = parsePrice(input);\n"
    "        if (amount == null) {\n"
    "            chatmap.put(player.getUniqueId(), waitObject);\n"
    "            player.sendMessage(ChatColor.RED + \"Please type a valid non-negative number, or type CANCEL.\");\n"
    "            return;\n"
    "        }\n"
    "        if (type.equalsIgnoreCase(\"price-buy\")) {\n"
    "            ChatWaitObject next = new ChatWaitObject(String.valueOf(amount), \"price-sell\", chest);\n"
    "            chatmap.put(player.getUniqueId(), next);\n"
    "            sendSellPricePrompt(player);\n"
    "            return;\n"
    "        }\n\n"
    "        Double buyPrice = parsePrice(waitObject.answer);\n"
    "        if (buyPrice == null) {\n"
    "            ChatWaitObject restart = new ChatWaitObject(\"none\", \"price-buy\", chest);\n"
    "            chatmap.put(player.getUniqueId(), restart);\n"
    "            player.sendMessage(ChatColor.RED + \"The saved buy price was invalid. Starting over.\");\n"
    "            sendBuyPricePrompt(player);\n"
    "            return;\n"
    "        }\n\n"
    "        SettingsGUI settingsGUI = new SettingsGUI();\n"
    "        if (!settingsGUI.changePrices(chest, player, buyPrice, amount)) {\n"
    "            chatmap.put(player.getUniqueId(), waitObject);\n"
    "            player.sendMessage(ChatColor.YELLOW + \"Let's try those prices again.\");\n"
    "            return;\n"
    "        }\n"
    "        player.sendMessage(Utils.colorify(\"&aPebbleShop prices updated.\"));\n"
    "        new MultiItemShopGUI().showGUI(player, chest);\n"
    "    }\n\n"
    "    private Double parsePrice",
    "ChatListener secure legacy price flow")
text = replace_once(text,
    "    public boolean checkIfPlayerExists(String name) {",
    "    private static boolean canManageListings(Player player, Block chest) {\n"
    "        if (player == null || chest == null || !(chest.getState() instanceof TileState)) {\n"
    "            return false;\n"
    "        }\n"
    "        PersistentDataContainer data = ((TileState) chest.getState()).getPersistentDataContainer();\n"
    "        String ownerId = data.get(new NamespacedKey(EzChestShop.getPlugin(), \"owner\"), PersistentDataType.STRING);\n"
    "        return (ownerId != null && ownerId.equalsIgnoreCase(player.getUniqueId().toString()))\n"
    "                || player.isOp()\n"
    "                || player.hasPermission(\"ecs.admin\")\n"
    "                || Utils.getAdminsList(data).contains(player.getUniqueId());\n"
    "    }\n\n"
    "    private static boolean canManageSettings(Player player, Block chest) {\n"
    "        if (player == null || chest == null || !(chest.getState() instanceof TileState)) {\n"
    "            return false;\n"
    "        }\n"
    "        PersistentDataContainer data = ((TileState) chest.getState()).getPersistentDataContainer();\n"
    "        String ownerId = data.get(new NamespacedKey(EzChestShop.getPlugin(), \"owner\"), PersistentDataType.STRING);\n"
    "        return (ownerId != null && ownerId.equalsIgnoreCase(player.getUniqueId().toString()))\n"
    "                || player.isOp()\n"
    "                || player.hasPermission(\"ecs.admin\");\n"
    "    }\n\n"
    "    public boolean checkIfPlayerExists(String name) {",
    "ChatListener authorization helpers")
text = replace_once(text,
    "    public void addThePlayer(String answer, Block chest, Player player) {\n"
    "        UUID answerUUID = Bukkit.getOfflinePlayer(answer).getUniqueId();",
    "    public void addThePlayer(String answer, Block chest, Player player) {\n"
    "        if (!canManageSettings(player, chest)) {\n"
    "            player.sendMessage(Utils.colorify(\"&cYou no longer have permission to manage shop staff.\"));\n"
    "            return;\n"
    "        }\n"
    "        UUID answerUUID = Bukkit.getOfflinePlayer(answer).getUniqueId();",
    "ChatListener add staff authorization")
text = replace_once(text,
    "    public void removeThePlayer(String answer, Block chest, Player player) {\n"
    "        UUID answerUUID = Bukkit.getOfflinePlayer(answer).getUniqueId();",
    "    public void removeThePlayer(String answer, Block chest, Player player) {\n"
    "        if (!canManageSettings(player, chest)) {\n"
    "            player.sendMessage(Utils.colorify(\"&cYou no longer have permission to manage shop staff.\"));\n"
    "            return;\n"
    "        }\n"
    "        UUID answerUUID = Bukkit.getOfflinePlayer(answer).getUniqueId();",
    "ChatListener remove staff authorization")
write(path, text)


# ---------------------------------------------------------------------------
# ShopContainer: make item/economy mutations ordered and rollback-safe, reject
# invalid transaction totals, and preserve multi-item payloads on shulker moves.
# ---------------------------------------------------------------------------
path = "core/src/main/java/me/deadlight/ezchestshop/data/ShopContainer.java"
text = read(path)
text = replace_once(text,
    "import me.deadlight.ezchestshop.utils.holograms.ShopHologram;",
    "import me.deadlight.ezchestshop.utils.ShopItemUtils;\nimport me.deadlight.ezchestshop.utils.holograms.ShopHologram;",
    "ShopContainer ShopItemUtils import")
text = replace_once(text,
    "import org.bukkit.inventory.InventoryHolder;\nimport org.bukkit.inventory.ItemStack;",
    "import org.bukkit.inventory.Inventory;\nimport org.bukkit.inventory.InventoryHolder;\nimport org.bukkit.inventory.ItemStack;",
    "ShopContainer Inventory import")
text = replace_once(text,
    "        newContainer.set(new NamespacedKey(EzChestShop.getPlugin(), \"item\"), PersistentDataType.STRING,\n"
    "                oldContainer.get(new NamespacedKey(EzChestShop.getPlugin(), \"item\"), PersistentDataType.STRING));",
    "        newContainer.set(new NamespacedKey(EzChestShop.getPlugin(), \"item\"), PersistentDataType.STRING,\n"
    "                oldContainer.get(new NamespacedKey(EzChestShop.getPlugin(), \"item\"), PersistentDataType.STRING));\n"
    "        String offersPayload = oldContainer.get(ShopItemUtils.offersKey(), PersistentDataType.STRING);\n"
    "        if (offersPayload != null) {\n"
    "            newContainer.set(ShopItemUtils.offersKey(), PersistentDataType.STRING, offersPayload);\n"
    "        }\n"
    "        Integer emptyShopItem = oldContainer.get(ShopItemUtils.emptyShopItemKey(), PersistentDataType.INTEGER);\n"
    "        if (emptyShopItem != null) {\n"
    "            newContainer.set(ShopItemUtils.emptyShopItemKey(), PersistentDataType.INTEGER, emptyShopItem);\n"
    "        }",
    "ShopContainer shulker multi-item persistence")
text = regex_once(text,
    r"    public static void buyItem\(Block containerBlock, double price, int count, ItemStack tthatItem, Player player,\n.*?\n    public static void sellItem",
    "    public static void buyItem(Block containerBlock, double price, int count, ItemStack template, Player player,\n"
    "            OfflinePlayer owner, PersistentDataContainer data) {\n"
    "        LanguageManager lm = new LanguageManager();\n"
    "        Inventory storage = Utils.getBlockInventory(containerBlock);\n"
    "        OfflinePlayer buyer = Bukkit.getOfflinePlayer(player.getUniqueId());\n"
    "        if (!validTransaction(price, count) || storage == null || owner == null) {\n"
    "            player.sendMessage(lm.chestShopProblem());\n"
    "            return;\n"
    "        }\n"
    "        if (!Utils.containsAtLeast(storage, template, count)) {\n"
    "            player.sendMessage(lm.outofStock());\n"
    "            failureSound(player);\n"
    "            return;\n"
    "        }\n"
    "        if (!ifHasMoney(buyer, price)) {\n"
    "            player.sendMessage(lm.cannotAfford());\n"
    "            failureSound(player);\n"
    "            return;\n"
    "        }\n"
    "        if (!Utils.hasEnoughSpace(player, count, template)) {\n"
    "            player.sendMessage(lm.fullinv());\n"
    "            failureSound(player);\n"
    "            return;\n"
    "        }\n"
    "        if (!withdraw(price, buyer)) {\n"
    "            player.sendMessage(lm.cannotAfford());\n"
    "            failureSound(player);\n"
    "            return;\n"
    "        }\n"
    "        if (!removeExactItem(storage, template, count)) {\n"
    "            deposit(price, buyer);\n"
    "            player.sendMessage(lm.outofStock());\n"
    "            failureSound(player);\n"
    "            return;\n"
    "        }\n"
    "        addItemStacks(player.getInventory(), template, count);\n"
    "        deposit(price, owner);\n"
    "        sharedIncomeCheck(data, price);\n"
    "        transactionMessage(data, owner, buyer, price, true, template, count, containerBlock);\n"
    "        player.sendMessage(lm.messageSuccBuy(price));\n"
    "        successSound(player);\n"
    "        Config.shopCommandManager.executeCommands(player, containerBlock.getLocation(),\n"
    "                ShopCommandManager.ShopType.SHOP, ShopCommandManager.ShopAction.BUY, count + \"\");\n"
    "    }\n\n"
    "    public static void sellItem",
    "ShopContainer atomic player purchase")
text = regex_once(text,
    r"    public static void sellItem\(Block containerBlock, double price, int count, ItemStack tthatItem, Player player,\n.*?\n    public static void buyServerItem",
    "    public static void sellItem(Block containerBlock, double price, int count, ItemStack template, Player player,\n"
    "            OfflinePlayer owner, PersistentDataContainer data) {\n"
    "        LanguageManager lm = new LanguageManager();\n"
    "        Inventory storage = Utils.getBlockInventory(containerBlock);\n"
    "        OfflinePlayer seller = Bukkit.getOfflinePlayer(player.getUniqueId());\n"
    "        if (!validTransaction(price, count) || storage == null || owner == null) {\n"
    "            player.sendMessage(lm.chestShopProblem());\n"
    "            return;\n"
    "        }\n"
    "        if (!Utils.containsAtLeast(player.getInventory(), template, count)) {\n"
    "            player.sendMessage(lm.notEnoughItemToSell());\n"
    "            failureSound(player);\n"
    "            return;\n"
    "        }\n"
    "        if (!ifHasMoney(owner, price)) {\n"
    "            player.sendMessage(lm.shopCannotAfford());\n"
    "            failureSound(player);\n"
    "            return;\n"
    "        }\n"
    "        if (!Utils.containerHasEnoughSpace(storage, count, template)) {\n"
    "            player.sendMessage(lm.chestIsFull());\n"
    "            failureSound(player);\n"
    "            return;\n"
    "        }\n"
    "        if (!withdraw(price, owner)) {\n"
    "            player.sendMessage(lm.shopCannotAfford());\n"
    "            failureSound(player);\n"
    "            return;\n"
    "        }\n"
    "        if (!removeExactItem(player.getInventory(), template, count)) {\n"
    "            deposit(price, owner);\n"
    "            player.sendMessage(lm.notEnoughItemToSell());\n"
    "            failureSound(player);\n"
    "            return;\n"
    "        }\n"
    "        addItemStacks(storage, template, count);\n"
    "        deposit(price, seller);\n"
    "        transactionMessage(data, owner, seller, price, false, template, count, containerBlock);\n"
    "        player.sendMessage(lm.messageSuccSell(price));\n"
    "        successSound(player);\n"
    "        Config.shopCommandManager.executeCommands(player, containerBlock.getLocation(),\n"
    "                ShopCommandManager.ShopType.SHOP, ShopCommandManager.ShopAction.SELL, count + \"\");\n"
    "    }\n\n"
    "    public static void buyServerItem",
    "ShopContainer atomic player sale")
text = regex_once(text,
    r"    public static void buyServerItem\(Block containerBlock, double price, int count, Player player, ItemStack tthatItem,\n.*?\n    public static void sellServerItem",
    "    public static void buyServerItem(Block containerBlock, double price, int count, Player player, ItemStack template,\n"
    "            PersistentDataContainer data) {\n"
    "        LanguageManager lm = new LanguageManager();\n"
    "        OfflinePlayer buyer = Bukkit.getOfflinePlayer(player.getUniqueId());\n"
    "        if (!validTransaction(price, count)) {\n"
    "            player.sendMessage(lm.cannotAfford());\n"
    "            return;\n"
    "        }\n"
    "        if (!ifHasMoney(buyer, price)) {\n"
    "            player.sendMessage(lm.cannotAfford());\n"
    "            failureSound(player);\n"
    "            return;\n"
    "        }\n"
    "        if (!Utils.hasEnoughSpace(player, count, template)) {\n"
    "            player.sendMessage(lm.fullinv());\n"
    "            failureSound(player);\n"
    "            return;\n"
    "        }\n"
    "        if (!withdraw(price, buyer)) {\n"
    "            player.sendMessage(lm.cannotAfford());\n"
    "            failureSound(player);\n"
    "            return;\n"
    "        }\n"
    "        addItemStacks(player.getInventory(), template, count);\n"
    "        OfflinePlayer owner = ownerFrom(data);\n"
    "        transactionMessage(data, owner, buyer, price, true, template, count, containerBlock);\n"
    "        player.sendMessage(lm.messageSuccBuy(price));\n"
    "        successSound(player);\n"
    "        Config.shopCommandManager.executeCommands(player, containerBlock.getLocation(),\n"
    "                ShopCommandManager.ShopType.ADMINSHOP, ShopCommandManager.ShopAction.BUY, count + \"\");\n"
    "    }\n\n"
    "    public static void sellServerItem",
    "ShopContainer atomic admin purchase")
text = regex_once(text,
    r"    public static void sellServerItem\(Block containerBlock, double price, int count, ItemStack tthatItem, Player player,\n.*?\n    private static void deposit",
    "    public static void sellServerItem(Block containerBlock, double price, int count, ItemStack template, Player player,\n"
    "            PersistentDataContainer data) {\n"
    "        LanguageManager lm = new LanguageManager();\n"
    "        OfflinePlayer seller = Bukkit.getOfflinePlayer(player.getUniqueId());\n"
    "        if (!validTransaction(price, count)) {\n"
    "            player.sendMessage(lm.notEnoughItemToSell());\n"
    "            return;\n"
    "        }\n"
    "        if (!Utils.containsAtLeast(player.getInventory(), template, count)) {\n"
    "            player.sendMessage(lm.notEnoughItemToSell());\n"
    "            failureSound(player);\n"
    "            return;\n"
    "        }\n"
    "        if (!removeExactItem(player.getInventory(), template, count)) {\n"
    "            player.sendMessage(lm.notEnoughItemToSell());\n"
    "            failureSound(player);\n"
    "            return;\n"
    "        }\n"
    "        deposit(price, seller);\n"
    "        transactionMessage(data, ownerFrom(data), seller, price, false, template, count, containerBlock);\n"
    "        player.sendMessage(lm.messageSuccSell(price));\n"
    "        successSound(player);\n"
    "        Config.shopCommandManager.executeCommands(player, containerBlock.getLocation(),\n"
    "                ShopCommandManager.ShopType.ADMINSHOP, ShopCommandManager.ShopAction.SELL, count + \"\");\n"
    "    }\n\n"
    "    private static boolean validTransaction(double price, int count) {\n"
    "        return count > 0 && Double.isFinite(price) && price >= 0D;\n"
    "    }\n\n"
    "    private static boolean removeExactItem(Inventory inventory, ItemStack template, int count) {\n"
    "        if (inventory == null || template == null || count <= 0) {\n"
    "            return false;\n"
    "        }\n"
    "        ItemStack removal = template.clone();\n"
    "        removal.setAmount(count);\n"
    "        return Utils.removeItem(inventory, removal).isEmpty();\n"
    "    }\n\n"
    "    private static void addItemStacks(Inventory inventory, ItemStack template, int count) {\n"
    "        int remaining = count;\n"
    "        int maxStack = Math.max(1, template.getMaxStackSize());\n"
    "        while (remaining > 0) {\n"
    "            ItemStack stack = template.clone();\n"
    "            int amount = Math.min(maxStack, remaining);\n"
    "            stack.setAmount(amount);\n"
    "            inventory.addItem(stack);\n"
    "            remaining -= amount;\n"
    "        }\n"
    "    }\n\n"
    "    private static OfflinePlayer ownerFrom(PersistentDataContainer data) {\n"
    "        if (data == null) {\n"
    "            return null;\n"
    "        }\n"
    "        String owner = data.get(new NamespacedKey(EzChestShop.getPlugin(), \"owner\"), PersistentDataType.STRING);\n"
    "        if (owner == null) {\n"
    "            return null;\n"
    "        }\n"
    "        try {\n"
    "            return Bukkit.getOfflinePlayer(UUID.fromString(owner));\n"
    "        } catch (IllegalArgumentException ignored) {\n"
    "            return null;\n"
    "        }\n"
    "    }\n\n"
    "    private static void successSound(Player player) {\n"
    "        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.5f, 0.5f);\n"
    "    }\n\n"
    "    private static void failureSound(Player player) {\n"
    "        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE, 0.5f, 0.5f);\n"
    "    }\n\n"
    "    private static void deposit",
    "ShopContainer atomic admin sale and helpers")
text = replace_once(text,
    "    private static void deposit(double price, OfflinePlayer deposit) {\n\n"
    "        if (Config.useXP) {",
    "    private static void deposit(double price, OfflinePlayer deposit) {\n\n"
    "        if (deposit == null || !Double.isFinite(price) || price < 0D) {\n"
    "            return;\n"
    "        }\n"
    "        if (Config.useXP) {",
    "ShopContainer deposit validation")
text = replace_once(text,
    "    private static boolean withdraw(double price, OfflinePlayer deposit) {\n\n"
    "        if (Config.useXP) {",
    "    private static boolean withdraw(double price, OfflinePlayer deposit) {\n\n"
    "        if (deposit == null || !Double.isFinite(price) || price < 0D) {\n"
    "            return false;\n"
    "        }\n"
    "        if (price == 0D) {\n"
    "            return true;\n"
    "        }\n"
    "        if (Config.useXP) {",
    "ShopContainer withdraw validation")
text = replace_once(text,
    "    private static boolean ifHasMoney(OfflinePlayer player, double price) {\n"
    "        if (Config.useXP) {",
    "    private static boolean ifHasMoney(OfflinePlayer player, double price) {\n"
    "        if (player == null || !Double.isFinite(price) || price < 0D) {\n"
    "            return false;\n"
    "        }\n"
    "        if (Config.useXP) {",
    "ShopContainer balance validation")
write(path, text)


# ---------------------------------------------------------------------------
# Interaction listener must not mutate/cancel at MONITOR and must respect prior
# protection cancellations.
# ---------------------------------------------------------------------------
path = "core/src/main/java/me/deadlight/ezchestshop/listeners/ChestOpeningListener.java"
text = read(path)
text = replace_once(text,
    "    @EventHandler(priority = EventPriority.MONITOR)",
    "    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)",
    "ChestOpeningListener event priority")
write(path, text)


# Audit record used for release review and live verification.
doc = ROOT / "docs" / "multi-item-shop-audit.md"
doc.parent.mkdir(parents=True, exist_ok=True)
doc.write_text("""# Multi-item shop audit

The multi-item and Bedrock-safe storefront was audited before merging to `main`.

## Hardened during the audit

- GUI trades resolve the current listing, current price, owner, and shop flags at click time.
- View-only administrators can no longer open or modify real shop stock.
- Shop staff can manage listings and stock, but only the owner or a full shop administrator can change financial settings or staff membership.
- Chat prompts move all Bukkit/PDC access back to the server thread and revalidate permissions when completed.
- Player and admin-shop transactions validate finite totals and order economy/item mutations with rollback on failed removals.
- Multi-item payloads are preserved when shulker shops are broken, moved, and placed again.
- Persisted listing payloads reject duplicate IDs, duplicate item identities, invalid capacity, and non-finite prices.
- Shop interaction respects earlier event cancellations and no longer mutates events at MONITOR priority.

## Required live smoke test

1. Test two differently named books with different prices from Java and Bedrock.
2. Test Buy 1, Buy Stack, Sell 1, and Sell Stack.
3. Remove a staff member while they have a price prompt open and confirm completion is denied.
4. Leave an old trade GUI open, change its price, then confirm the new price is charged.
5. Break and replace a multi-listing shulker shop and confirm all listings remain.
6. Restart with a double-chest shop and open either half.
""", encoding="utf-8")

print("Applied multi-item shop audit hardening.")
