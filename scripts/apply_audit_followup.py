#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def load(path):
    return (ROOT / path).read_text(encoding="utf-8")


def save(path, text):
    (ROOT / path).write_text(text, encoding="utf-8")


def once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)


def rx(text, pattern, replacement, label):
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise RuntimeError(f"{label}: expected 1 regex match, found {count}")
    return updated


# GUI permission boundaries and stale-menu mutation checks.
path = "core/src/main/java/me/deadlight/ezchestshop/guis/MultiItemShopGUI.java"
text = load(path)
text = once(text,
    "    public void showOfferEditor(Player player, Block containerBlock, String offerId) {\n"
    "        if (!isValidShop(containerBlock) || !canManageSettings(player, data(containerBlock))) {",
    "    public void showOfferEditor(Player player, Block containerBlock, String offerId) {\n"
    "        if (!isValidShop(containerBlock) || !canManage(player, data(containerBlock))) {",
    "restore listing editor access for shop staff")
text = once(text,
    "    public void showStaffManager(Player player, Block containerBlock) {\n"
    "        if (!isValidShop(containerBlock) || !canManage(player, data(containerBlock))) {",
    "    public void showStaffManager(Player player, Block containerBlock) {\n"
    "        if (!isValidShop(containerBlock) || !canManageSettings(player, data(containerBlock))) {",
    "restrict staff manager")

# Resolve current listing state and permissions for toggle actions.
text = once(text,
    "        gui.setItem(2, 4, new GuiItem(buyToggle, event -> {\n"
    "            event.setCancelled(true);\n"
    "            if (!offer.isBuyingEnabled() && offer.getBuyPrice() <= 0D) {",
    "        gui.setItem(2, 4, new GuiItem(buyToggle, event -> {\n"
    "            event.setCancelled(true);\n"
    "            if (!requireListingAccess(player, containerBlock)) {\n"
    "                return;\n"
    "            }\n"
    "            ShopOffer current = ShopItemUtils.getOffer(containerBlock, offerId);\n"
    "            if (current == null) {\n"
    "                player.sendMessage(Utils.colorify(\"&cThat listing no longer exists.\"));\n"
    "                showGUI(player, containerBlock);\n"
    "                return;\n"
    "            }\n"
    "            if (!current.isBuyingEnabled() && current.getBuyPrice() <= 0D) {",
    "fresh buy toggle state")
text = once(text,
    "        gui.setItem(2, 6, new GuiItem(sellToggle, event -> {\n"
    "            event.setCancelled(true);\n"
    "            if (!offer.isSellingEnabled() && offer.getSellPrice() <= 0D) {",
    "        gui.setItem(2, 6, new GuiItem(sellToggle, event -> {\n"
    "            event.setCancelled(true);\n"
    "            if (!requireListingAccess(player, containerBlock)) {\n"
    "                return;\n"
    "            }\n"
    "            ShopOffer current = ShopItemUtils.getOffer(containerBlock, offerId);\n"
    "            if (current == null) {\n"
    "                player.sendMessage(Utils.colorify(\"&cThat listing no longer exists.\"));\n"
    "                showGUI(player, containerBlock);\n"
    "                return;\n"
    "            }\n"
    "            if (!current.isSellingEnabled() && current.getSellPrice() <= 0D) {",
    "fresh sell toggle state")
text = once(text,
    "        gui.setItem(2, 7, new GuiItem(replace, event -> {\n"
    "            event.setCancelled(true);\n"
    "            ItemStack selected = selectedItem(player, event.getCursor());",
    "        gui.setItem(2, 7, new GuiItem(replace, event -> {\n"
    "            event.setCancelled(true);\n"
    "            if (!requireListingAccess(player, containerBlock)) {\n"
    "                return;\n"
    "            }\n"
    "            ItemStack selected = selectedItem(player, event.getCursor());",
    "replace listing permission recheck")

# Settings callbacks must revalidate owner/full-admin access at click time.
for key in ("dbuy", "dsell", "msgtoggle", "shareincome"):
    old = f"            event.setCancelled(true);\n            setGlobalFlag(containerBlock, \"{key}\""
    new = ("            event.setCancelled(true);\n"
           "            if (!requireSettingsAccess(player, containerBlock)) {\n"
           "                return;\n"
           "            }\n"
           f"            setGlobalFlag(containerBlock, \"{key}\"")
    text = once(text, old, new, f"settings permission recheck {key}")

text = once(text,
    "        gui.setItem(6, 7, new GuiItem(add, event -> {\n"
    "            event.setCancelled(true);\n"
    "            ItemStack selected = selectedItem(player, event.getCursor());",
    "        gui.setItem(6, 7, new GuiItem(add, event -> {\n"
    "            event.setCancelled(true);\n"
    "            if (!requireListingAccess(player, containerBlock)) {\n"
    "                return;\n"
    "            }\n"
    "            ItemStack selected = selectedItem(player, event.getCursor());",
    "add listing permission recheck")
text = once(text,
    "        gui.setItem(row, column, new GuiItem(storage, event -> {\n"
    "            event.setCancelled(true);\n"
    "            Inventory inventory = Utils.getBlockInventory(containerBlock);",
    "        gui.setItem(row, column, new GuiItem(storage, event -> {\n"
    "            event.setCancelled(true);\n"
    "            if (!requireListingAccess(player, containerBlock)) {\n"
    "                return;\n"
    "            }\n"
    "            Inventory inventory = Utils.getBlockInventory(containerBlock);",
    "storage permission recheck")
text = once(text,
    "    private void showRemoveConfirmation(Player player, Block containerBlock, String offerId) {\n"
    "        ShopOffer offer = ShopItemUtils.getOffer(containerBlock, offerId);",
    "    private void showRemoveConfirmation(Player player, Block containerBlock, String offerId) {\n"
    "        if (!requireListingAccess(player, containerBlock)) {\n"
    "            return;\n"
    "        }\n"
    "        ShopOffer offer = ShopItemUtils.getOffer(containerBlock, offerId);",
    "remove confirmation permission")
text = once(text,
    "        gui.setItem(2, 4, new GuiItem(confirm, event -> {\n"
    "            event.setCancelled(true);\n"
    "            if (ShopItemUtils.removeOffer(containerBlock, offerId)) {",
    "        gui.setItem(2, 4, new GuiItem(confirm, event -> {\n"
    "            event.setCancelled(true);\n"
    "            if (!requireListingAccess(player, containerBlock)) {\n"
    "                return;\n"
    "            }\n"
    "            if (ShopItemUtils.removeOffer(containerBlock, offerId)) {",
    "remove listing permission recheck")
text = once(text,
    "    private void beginStaffUpdate(Player player, Block containerBlock, String type) {\n"
    "        ChatListener.chatmap.put(player.getUniqueId(), new ChatWaitObject(\"none\", type, containerBlock));",
    "    private void beginStaffUpdate(Player player, Block containerBlock, String type) {\n"
    "        if (!requireSettingsAccess(player, containerBlock)) {\n"
    "            return;\n"
    "        }\n"
    "        ChatListener.chatmap.put(player.getUniqueId(), new ChatWaitObject(\"none\", type, containerBlock));",
    "staff prompt permission recheck")
text = once(text,
    "    private boolean canManageSettings(Player player, PersistentDataContainer data) {",
    "    private boolean requireListingAccess(Player player, Block containerBlock) {\n"
    "        if (isValidShop(containerBlock) && canManage(player, data(containerBlock))) {\n"
    "            return true;\n"
    "        }\n"
    "        player.closeInventory();\n"
    "        player.sendMessage(Utils.colorify(\"&cYou no longer have permission to manage this shop.\"));\n"
    "        return false;\n"
    "    }\n\n"
    "    private boolean requireSettingsAccess(Player player, Block containerBlock) {\n"
    "        if (isValidShop(containerBlock) && canManageSettings(player, data(containerBlock))) {\n"
    "            return true;\n"
    "        }\n"
    "        player.closeInventory();\n"
    "        player.sendMessage(Utils.colorify(\"&cOnly the shop owner or a full administrator can change these settings.\"));\n"
    "        return false;\n"
    "    }\n\n"
    "    private boolean canManageSettings(Player player, PersistentDataContainer data) {",
    "GUI permission helper methods")
save(path, text)


# Harden malformed staff metadata instead of throwing from every GUI/permission path.
path = "core/src/main/java/me/deadlight/ezchestshop/utils/Utils.java"
text = load(path)
text = rx(text,
    r"    public static List<UUID> getAdminsList\(PersistentDataContainer data\) \{.*?\n    \}\n\n    public static List<TransactionLogObject>",
    "    public static List<UUID> getAdminsList(PersistentDataContainer data) {\n"
    "        List<UUID> admins = new ArrayList<>();\n"
    "        if (data == null) {\n"
    "            return admins;\n"
    "        }\n"
    "        String adminsString = data.get(new NamespacedKey(EzChestShop.getPlugin(), \"admins\"),\n"
    "                PersistentDataType.STRING);\n"
    "        if (adminsString == null || adminsString.trim().isEmpty()\n"
    "                || adminsString.equalsIgnoreCase(\"none\")) {\n"
    "            return admins;\n"
    "        }\n"
    "        for (String raw : adminsString.split(\"@\")) {\n"
    "            if (raw == null || raw.trim().isEmpty()) {\n"
    "                continue;\n"
    "            }\n"
    "            try {\n"
    "                UUID uuid = UUID.fromString(raw.trim());\n"
    "                if (!admins.contains(uuid)) {\n"
    "                    admins.add(uuid);\n"
    "                }\n"
    "            } catch (IllegalArgumentException ignored) {\n"
    "                // Ignore one malformed legacy entry rather than breaking the shop.\n"
    "            }\n"
    "        }\n"
    "        return admins;\n"
    "    }\n\n"
    "    public static List<TransactionLogObject>",
    "robust shop staff metadata")
save(path, text)


# Chat prompt queue: suppress accidental public chat while a prior prompt input is
# being processed, clean up on quit, and authorize legacy price entry before reads.
path = "core/src/main/java/me/deadlight/ezchestshop/listeners/ChatListener.java"
text = load(path)
text = once(text,
    "import org.bukkit.event.player.AsyncPlayerChatEvent;",
    "import org.bukkit.event.player.AsyncPlayerChatEvent;\nimport org.bukkit.event.player.PlayerQuitEvent;",
    "ChatListener quit event import")
text = once(text,
    "import java.util.Map;\nimport java.util.UUID;",
    "import java.util.Map;\nimport java.util.Set;\nimport java.util.UUID;",
    "ChatListener Set import")
text = once(text,
    "    public static final Map<UUID, ChatWaitObject> chatmap = new ConcurrentHashMap<>();\n"
    "    public static LanguageManager lm",
    "    public static final Map<UUID, ChatWaitObject> chatmap = new ConcurrentHashMap<>();\n"
    "    private static final Set<UUID> processingInputs = ConcurrentHashMap.newKeySet();\n"
    "    public static LanguageManager lm",
    "ChatListener processing set")
text = once(text,
    "    public static void startPriceEditor(Player player, Block containerBlock) {\n"
    "        List<ShopOffer> offers = ShopItemUtils.getOffers(containerBlock);",
    "    public static void startPriceEditor(Player player, Block containerBlock) {\n"
    "        if (!canManageListings(player, containerBlock)) {\n"
    "            player.sendMessage(Utils.colorify(\"&cYou no longer have permission to edit this shop.\"));\n"
    "            return;\n"
    "        }\n"
    "        List<ShopOffer> offers = ShopItemUtils.getOffers(containerBlock);",
    "legacy price editor authorization")
text = rx(text,
    r"    @EventHandler\n    public void onAsyncChat\(AsyncPlayerChatEvent event\) \{.*?\n    \}\n\n    private void processChatInput",
    "    @EventHandler\n"
    "    public void onAsyncChat(AsyncPlayerChatEvent event) {\n"
    "        Player player = event.getPlayer();\n"
    "        UUID playerId = player.getUniqueId();\n"
    "        ChatWaitObject waitObject = chatmap.remove(playerId);\n"
    "        if (waitObject == null) {\n"
    "            if (processingInputs.contains(playerId)) {\n"
    "                event.setCancelled(true);\n"
    "            }\n"
    "            return;\n"
    "        }\n\n"
    "        event.setCancelled(true);\n"
    "        processingInputs.add(playerId);\n"
    "        String input = event.getMessage() == null ? \"\" : event.getMessage().trim();\n"
    "        EzChestShop.getScheduler().scheduleSyncDelayedTask(() -> {\n"
    "            try {\n"
    "                processChatInput(player, waitObject, input);\n"
    "            } finally {\n"
    "                processingInputs.remove(playerId);\n"
    "            }\n"
    "        }, 0);\n"
    "    }\n\n"
    "    @EventHandler\n"
    "    public void onQuit(PlayerQuitEvent event) {\n"
    "        UUID playerId = event.getPlayer().getUniqueId();\n"
    "        chatmap.remove(playerId);\n"
    "        processingInputs.remove(playerId);\n"
    "    }\n\n"
    "    private void processChatInput",
    "ChatListener prompt queue and cleanup")
save(path, text)


# Listing utility API invariants and legacy in-memory item synchronization.
path = "core/src/main/java/me/deadlight/ezchestshop/utils/ShopItemUtils.java"
text = load(path)
text = once(text,
    "        if (offer == null) {\n"
    "            return false;\n"
    "        }\n"
    "        offer.setBuyingEnabled(!offer.isBuyingEnabled());",
    "        if (offer == null) {\n"
    "            return false;\n"
    "        }\n"
    "        if (!offer.isBuyingEnabled() && offer.getBuyPrice() <= 0D) {\n"
    "            return false;\n"
    "        }\n"
    "        offer.setBuyingEnabled(!offer.isBuyingEnabled());",
    "buy toggle zero-price guard")
text = once(text,
    "        if (offer == null) {\n"
    "            return false;\n"
    "        }\n"
    "        offer.setSellingEnabled(!offer.isSellingEnabled());",
    "        if (offer == null) {\n"
    "            return false;\n"
    "        }\n"
    "        if (!offer.isSellingEnabled() && offer.getSellPrice() <= 0D) {\n"
    "            return false;\n"
    "        }\n"
    "        offer.setSellingEnabled(!offer.isSellingEnabled());",
    "sell toggle zero-price guard")
text = once(text,
    "            if (shop != null) {\n"
    "                shop.setBuyPrice(buy);\n"
    "                shop.setSellPrice(sell);\n"
    "            }",
    "            if (shop != null) {\n"
    "                shop.setShopItem(item);\n"
    "                shop.setBuyPrice(buy);\n"
    "                shop.setSellPrice(sell);\n"
    "            }",
    "legacy in-memory item synchronization")
save(path, text)

path = "core/src/main/java/me/deadlight/ezchestshop/utils/objects/EzShop.java"
text = load(path)
text = once(text,
    "    public ItemStack getShopItem() {\n"
    "        return shopItem.clone();\n"
    "    }",
    "    public ItemStack getShopItem() {\n"
    "        return shopItem.clone();\n"
    "    }\n\n"
    "    public void setShopItem(ItemStack shopItem) {\n"
    "        if (shopItem != null && shopItem.getType() != org.bukkit.Material.AIR) {\n"
    "            this.shopItem = shopItem.clone();\n"
    "            this.shopItem.setAmount(1);\n"
    "        }\n"
    "    }",
    "EzShop shop item setter")
save(path, text)


# Economy and inventory rollback completion.
path = "core/src/main/java/me/deadlight/ezchestshop/data/ShopContainer.java"
text = load(path)
text = once(text,
    "import java.util.List;\nimport java.util.UUID;",
    "import java.util.List;\nimport java.util.Map;\nimport java.util.UUID;",
    "ShopContainer Map import")
text = once(text,
    "        addItemStacks(player.getInventory(), template, count);\n"
    "        deposit(price, owner);\n"
    "        sharedIncomeCheck(data, price);",
    "        if (!addItemStacks(player.getInventory(), template, count)) {\n"
    "            addItemStacks(storage, template, count);\n"
    "            deposit(price, buyer);\n"
    "            player.sendMessage(lm.fullinv());\n"
    "            failureSound(player);\n"
    "            return;\n"
    "        }\n"
    "        if (!deposit(price, owner)) {\n"
    "            removeExactItem(player.getInventory(), template, count);\n"
    "            addItemStacks(storage, template, count);\n"
    "            deposit(price, buyer);\n"
    "            player.sendMessage(lm.chestShopProblem());\n"
    "            failureSound(player);\n"
    "            return;\n"
    "        }\n"
    "        sharedIncomeCheck(data, price);",
    "player purchase deposit rollback")
text = once(text,
    "        addItemStacks(storage, template, count);\n"
    "        deposit(price, seller);\n"
    "        transactionMessage(data, owner, seller, price, false, template, count, containerBlock);",
    "        if (!addItemStacks(storage, template, count)) {\n"
    "            addItemStacks(player.getInventory(), template, count);\n"
    "            deposit(price, owner);\n"
    "            player.sendMessage(lm.chestIsFull());\n"
    "            failureSound(player);\n"
    "            return;\n"
    "        }\n"
    "        if (!deposit(price, seller)) {\n"
    "            removeExactItem(storage, template, count);\n"
    "            addItemStacks(player.getInventory(), template, count);\n"
    "            deposit(price, owner);\n"
    "            player.sendMessage(lm.chestShopProblem());\n"
    "            failureSound(player);\n"
    "            return;\n"
    "        }\n"
    "        transactionMessage(data, owner, seller, price, false, template, count, containerBlock);",
    "player sale deposit rollback")
text = once(text,
    "        addItemStacks(player.getInventory(), template, count);\n"
    "        OfflinePlayer owner = ownerFrom(data);",
    "        if (!addItemStacks(player.getInventory(), template, count)) {\n"
    "            deposit(price, buyer);\n"
    "            player.sendMessage(lm.fullinv());\n"
    "            failureSound(player);\n"
    "            return;\n"
    "        }\n"
    "        OfflinePlayer owner = ownerFrom(data);",
    "admin purchase inventory rollback")
text = once(text,
    "        deposit(price, seller);\n"
    "        transactionMessage(data, ownerFrom(data), seller, price, false, template, count, containerBlock);",
    "        if (!deposit(price, seller)) {\n"
    "            addItemStacks(player.getInventory(), template, count);\n"
    "            player.sendMessage(lm.chestShopProblem());\n"
    "            failureSound(player);\n"
    "            return;\n"
    "        }\n"
    "        transactionMessage(data, ownerFrom(data), seller, price, false, template, count, containerBlock);",
    "admin sale deposit rollback")
text = rx(text,
    r"    private static boolean removeExactItem\(Inventory inventory, ItemStack template, int count\) \{.*?\n    \}\n\n    private static void addItemStacks\(Inventory inventory, ItemStack template, int count\) \{.*?\n    \}",
    "    private static boolean removeExactItem(Inventory inventory, ItemStack template, int count) {\n"
    "        if (inventory == null || template == null || count <= 0) {\n"
    "            return false;\n"
    "        }\n"
    "        int remaining = count;\n"
    "        for (int slot = 0; slot < inventory.getSize() && remaining > 0; slot++) {\n"
    "            ItemStack stack = inventory.getItem(slot);\n"
    "            if (stack == null || !Utils.isSimilar(stack, template)) {\n"
    "                continue;\n"
    "            }\n"
    "            int take = Math.min(stack.getAmount(), remaining);\n"
    "            remaining -= take;\n"
    "            if (take == stack.getAmount()) {\n"
    "                inventory.setItem(slot, null);\n"
    "            } else {\n"
    "                stack.setAmount(stack.getAmount() - take);\n"
    "                inventory.setItem(slot, stack);\n"
    "            }\n"
    "        }\n"
    "        if (remaining == 0) {\n"
    "            return true;\n"
    "        }\n"
    "        int removed = count - remaining;\n"
    "        if (removed > 0) {\n"
    "            addItemStacks(inventory, template, removed);\n"
    "        }\n"
    "        return false;\n"
    "    }\n\n"
    "    private static boolean addItemStacks(Inventory inventory, ItemStack template, int count) {\n"
    "        if (inventory == null || template == null || count < 0) {\n"
    "            return false;\n"
    "        }\n"
    "        int remaining = count;\n"
    "        int added = 0;\n"
    "        int maxStack = Math.max(1, template.getMaxStackSize());\n"
    "        while (remaining > 0) {\n"
    "            ItemStack stack = template.clone();\n"
    "            int amount = Math.min(maxStack, remaining);\n"
    "            stack.setAmount(amount);\n"
    "            Map<Integer, ItemStack> leftovers = inventory.addItem(stack);\n"
    "            int leftoverAmount = 0;\n"
    "            for (ItemStack leftover : leftovers.values()) {\n"
    "                if (leftover != null) {\n"
    "                    leftoverAmount += leftover.getAmount();\n"
    "                }\n"
    "            }\n"
    "            int inserted = amount - leftoverAmount;\n"
    "            added += inserted;\n"
    "            remaining -= amount;\n"
    "            if (leftoverAmount > 0) {\n"
    "                if (added > 0) {\n"
    "                    removeExactItem(inventory, template, added);\n"
    "                }\n"
    "                return false;\n"
    "            }\n"
    "        }\n"
    "        return true;\n"
    "    }",
    "exact inventory mutation helpers")
text = once(text,
    "    private static void deposit(double price, OfflinePlayer deposit) {",
    "    private static boolean deposit(double price, OfflinePlayer deposit) {",
    "deposit boolean signature")
text = once(text,
    "        if (deposit == null || !Double.isFinite(price) || price < 0D) {\n"
    "            return;\n"
    "        }\n"
    "        if (Config.useXP) {\n"
    "            XPEconomy.depositPlayer(deposit, price);\n"
    "        } else {\n"
    "            econ.depositPlayer(deposit, price);\n"
    "        }\n\n"
    "    }",
    "        if (deposit == null || !Double.isFinite(price) || price < 0D) {\n"
    "            return false;\n"
    "        }\n"
    "        if (price == 0D) {\n"
    "            return true;\n"
    "        }\n"
    "        if (Config.useXP) {\n"
    "            return XPEconomy.depositPlayer(deposit, price);\n"
    "        }\n"
    "        return econ != null && econ.depositPlayer(deposit, price).transactionSuccess();\n"
    "    }",
    "deposit result handling")
text = once(text,
    "        } else {\n"
    "            double balance = econ.getBalance(player);\n"
    "            return !(balance < price);\n"
    "        }",
    "        } else {\n"
    "            if (econ == null) {\n"
    "                return false;\n"
    "            }\n"
    "            double balance = econ.getBalance(player);\n"
    "            return Double.isFinite(balance) && balance >= price;\n"
    "        }",
    "finite balance validation")
text = rx(text,
    r"\n    private static void getandgive\(OfflinePlayer withdraw, double price, OfflinePlayer deposit\) \{.*?\n    \}\n",
    "\n",
    "remove unsafe transfer helper")
text = once(text,
    "                        for (UUID adminUUID : adminsList) {\n"
    "                            deposit(profit, Bukkit.getOfflinePlayer(adminUUID));\n"
    "                        }",
    "                        for (UUID adminUUID : adminsList) {\n"
    "                            if (!deposit(profit, Bukkit.getOfflinePlayer(adminUUID))) {\n"
    "                                deposit(profit, Bukkit.getOfflinePlayer(ownerUUID));\n"
    "                            }\n"
    "                        }",
    "shared income failed-deposit rollback")
save(path, text)


# XP economy deposits now report whether offline data was actually saved.
path = "core/src/main/java/me/deadlight/ezchestshop/utils/XPEconomy.java"
text = load(path)
text = rx(text,
    r"    public static void depositPlayer\(OfflinePlayer player, double price\) \{.*?\n    \}",
    "    public static boolean depositPlayer(OfflinePlayer player, double price) {\n"
    "        ImprovedOfflinePlayer iop = ImprovedOfflinePlayer.improvedOfflinePlayer.fromOfflinePlayer(player);\n"
    "        if (!iop.hasPlayedBefore() || !Double.isFinite(price) || price < 0D) {\n"
    "            return false;\n"
    "        }\n"
    "        if (price == 0D) {\n"
    "            return true;\n"
    "        }\n"
    "        if (iop.getLevel() > 200000000) {\n"
    "            return updatePlayerXp(iop, calculateLevelPointDifference(iop, price));\n"
    "        }\n"
    "        return updatePlayerXp(iop, getPlayerXpPoints(iop) + price);\n"
    "    }",
    "XP deposit result")
save(path, text)

# Extend audit notes.
doc = ROOT / "docs" / "multi-item-shop-audit.md"
with doc.open("a", encoding="utf-8") as handle:
    handle.write("\n## Follow-up verification\n\n"
                 "- Stale management GUIs revalidate listing or settings permissions before every mutation.\n"
                 "- Inventory additions and economy deposits now expose failure and trigger rollback.\n"
                 "- Malformed staff UUID data is ignored safely instead of breaking shop access.\n"
                 "- Prompt state is cleaned on disconnect and rapid duplicate prompt input cannot leak into public chat.\n")

print("Applied final audit follow-up fixes.")
