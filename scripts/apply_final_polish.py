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


def regex_once(text: str, pattern: str, replacement: str, label: str) -> str:
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one regex match, found {count}")
    return updated


# ---------------------------------------------------------------------------
# GUI: report persistence failures and calculate every toggle from current PDC
# state rather than from values captured when a possibly stale GUI was opened.
# ---------------------------------------------------------------------------
path = "core/src/main/java/me/deadlight/ezchestshop/guis/MultiItemShopGUI.java"
text = read(path)
text = replace_once(text,
    "            ShopItemUtils.toggleOfferBuying(containerBlock, offerId);\n"
    "            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);",
    "            if (!ShopItemUtils.toggleOfferBuying(containerBlock, offerId)) {\n"
    "                player.sendMessage(Utils.colorify(\"&cPebbleShop could not update that listing.\"));\n"
    "                return;\n"
    "            }\n"
    "            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);",
    "buy toggle result")
text = replace_once(text,
    "            ShopItemUtils.toggleOfferSelling(containerBlock, offerId);\n"
    "            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);",
    "            if (!ShopItemUtils.toggleOfferSelling(containerBlock, offerId)) {\n"
    "                player.sendMessage(Utils.colorify(\"&cPebbleShop could not update that listing.\"));\n"
    "                return;\n"
    "            }\n"
    "            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);",
    "sell toggle result")

text = replace_once(text,
    "            setGlobalFlag(containerBlock, \"dbuy\", !buyDisabled);\n"
    "            ShopSettings settings = ShopContainer.getShopSettings(containerBlock.getLocation());\n"
    "            if (settings != null) settings.setDbuy(!buyDisabled);",
    "            boolean newDisabled = !flag(data(containerBlock), \"dbuy\", false);\n"
    "            setGlobalFlag(containerBlock, \"dbuy\", newDisabled);\n"
    "            ShopSettings settings = ShopContainer.getShopSettings(containerBlock.getLocation());\n"
    "            if (settings != null) settings.setDbuy(newDisabled);",
    "fresh global buy toggle")
text = replace_once(text,
    "            setGlobalFlag(containerBlock, \"dsell\", !sellDisabled);\n"
    "            ShopSettings settings = ShopContainer.getShopSettings(containerBlock.getLocation());\n"
    "            if (settings != null) settings.setDsell(!sellDisabled);",
    "            boolean newDisabled = !flag(data(containerBlock), \"dsell\", false);\n"
    "            setGlobalFlag(containerBlock, \"dsell\", newDisabled);\n"
    "            ShopSettings settings = ShopContainer.getShopSettings(containerBlock.getLocation());\n"
    "            if (settings != null) settings.setDsell(newDisabled);",
    "fresh global sell toggle")
text = replace_once(text,
    "            setGlobalFlag(containerBlock, \"msgtoggle\", !messages);\n"
    "            ShopSettings settings = ShopContainer.getShopSettings(containerBlock.getLocation());\n"
    "            if (settings != null) settings.setMsgtoggle(!messages);",
    "            boolean newValue = !flag(data(containerBlock), \"msgtoggle\", true);\n"
    "            setGlobalFlag(containerBlock, \"msgtoggle\", newValue);\n"
    "            ShopSettings settings = ShopContainer.getShopSettings(containerBlock.getLocation());\n"
    "            if (settings != null) settings.setMsgtoggle(newValue);",
    "fresh message toggle")
text = replace_once(text,
    "            setGlobalFlag(containerBlock, \"shareincome\", !shareIncome);\n"
    "            ShopSettings settings = ShopContainer.getShopSettings(containerBlock.getLocation());\n"
    "            if (settings != null) settings.setShareincome(!shareIncome);",
    "            boolean newValue = !flag(data(containerBlock), \"shareincome\", false);\n"
    "            setGlobalFlag(containerBlock, \"shareincome\", newValue);\n"
    "            ShopSettings settings = ShopContainer.getShopSettings(containerBlock.getLocation());\n"
    "            if (settings != null) settings.setShareincome(newValue);",
    "fresh shared-income toggle")
text = replace_once(text,
    "            if (ShopItemUtils.removeOffer(containerBlock, offerId)) {\n"
    "                player.sendMessage(Utils.colorify(\"&aListing removed. Stock was left in the chest.\"));\n"
    "                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);\n"
    "            }\n"
    "            showGUI(player, containerBlock);",
    "            if (ShopItemUtils.removeOffer(containerBlock, offerId)) {\n"
    "                player.sendMessage(Utils.colorify(\"&aListing removed. Stock was left in the chest.\"));\n"
    "                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);\n"
    "                showGUI(player, containerBlock);\n"
    "            } else {\n"
    "                player.sendMessage(Utils.colorify(\"&cPebbleShop could not remove that listing.\"));\n"
    "                showOfferEditor(player, containerBlock, offerId);\n"
    "            }",
    "remove listing result")
write(path, text)


# ---------------------------------------------------------------------------
# Listing prices: only auto-enable a globally disabled trade direction when the
# shop receives its first usable listing in that direction. Later edits must not
# silently undo an owner's explicit global disable setting.
# ---------------------------------------------------------------------------
path = "core/src/main/java/me/deadlight/ezchestshop/utils/ShopItemUtils.java"
text = read(path)
text = replace_once(text,
    "        ShopOffer target = findById(offers, offerId);\n"
    "        if (target == null) {\n"
    "            return false;\n"
    "        }\n"
    "        target.setBuyPrice(buyPrice);",
    "        ShopOffer target = findById(offers, offerId);\n"
    "        if (target == null) {\n"
    "            return false;\n"
    "        }\n\n"
    "        boolean hadUsableBuying = false;\n"
    "        boolean hadUsableSelling = false;\n"
    "        for (ShopOffer existing : offers) {\n"
    "            if (existing.isBuyingEnabled() && existing.getBuyPrice() > 0D) {\n"
    "                hadUsableBuying = true;\n"
    "            }\n"
    "            if (existing.isSellingEnabled() && existing.getSellPrice() > 0D) {\n"
    "                hadUsableSelling = true;\n"
    "            }\n"
    "        }\n\n"
    "        target.setBuyPrice(buyPrice);",
    "capture usable directions")
text = replace_once(text,
    "        // New shops start globally disabled. Setting a usable per-item price should\n"
    "        // make that direction available unless the owner later disables it globally.",
    "        // New shops start globally disabled. Only the first usable listing in a\n"
    "        // direction enables it; later edits preserve an explicit global disable.",
    "global enable comment")
text = text.replace("if (buyPrice > 0D) {", "if (buyPrice > 0D && !hadUsableBuying) {", 1)
text = text.replace("if (sellPrice > 0D) {", "if (sellPrice > 0D && !hadUsableSelling) {", 1)
text = text.replace("if (buyPrice > 0D && settings.isDbuy()) {",
                    "if (buyPrice > 0D && !hadUsableBuying && settings.isDbuy()) {", 1)
text = text.replace("if (sellPrice > 0D && settings.isDsell()) {",
                    "if (sellPrice > 0D && !hadUsableSelling && settings.isDsell()) {", 1)
write(path, text)


# ---------------------------------------------------------------------------
# Transactions: reject invalid templates, guard absent economy providers, and
# make shared-income parsing resilient to corrupt legacy PDC values.
# ---------------------------------------------------------------------------
path = "core/src/main/java/me/deadlight/ezchestshop/data/ShopContainer.java"
text = read(path)
for marker in (
    "if (!validTransaction(price, count) || storage == null || owner == null) {",
    "if (!validTransaction(price, count)) {",
):
    pass
text = text.replace(
    "if (!validTransaction(price, count) || storage == null || owner == null) {",
    "if (!validTransaction(price, count) || !isRealItem(template) || storage == null || owner == null) {",
    2)
text = text.replace(
    "if (!validTransaction(price, count)) {",
    "if (!validTransaction(price, count) || !isRealItem(template)) {",
    2)
text = replace_once(text,
    "    private static boolean validTransaction(double price, int count) {\n"
    "        return count > 0 && Double.isFinite(price) && price >= 0D;\n"
    "    }",
    "    private static boolean validTransaction(double price, int count) {\n"
    "        return count > 0 && Double.isFinite(price) && price >= 0D;\n"
    "    }\n\n"
    "    private static boolean isRealItem(ItemStack item) {\n"
    "        return item != null && item.getType() != Material.AIR;\n"
    "    }",
    "real item helper")
text = replace_once(text,
    "        } else {\n"
    "            return econ.withdrawPlayer(deposit, price).transactionSuccess();\n"
    "        }",
    "        } else {\n"
    "            return econ != null && econ.withdrawPlayer(deposit, price).transactionSuccess();\n"
    "        }",
    "economy withdrawal guard")
text = regex_once(text,
    r"    private static void sharedIncomeCheck\(PersistentDataContainer data, double price\) \{.*?\n    \}\n\n    public static void transferOwner",
    "    private static void sharedIncomeCheck(PersistentDataContainer data, double price) {\n"
    "        if (data == null || !Double.isFinite(price) || price <= 0D) {\n"
    "            return;\n"
    "        }\n"
    "        Integer enabled = data.get(new NamespacedKey(EzChestShop.getPlugin(), \"shareincome\"),\n"
    "                PersistentDataType.INTEGER);\n"
    "        if (enabled == null || enabled != 1) {\n"
    "            return;\n"
    "        }\n\n"
    "        OfflinePlayer owner = ownerFrom(data);\n"
    "        List<UUID> admins = Utils.getAdminsList(data);\n"
    "        if (owner == null || admins.isEmpty()) {\n"
    "            return;\n"
    "        }\n\n"
    "        double share = price / (admins.size() + 1D);\n"
    "        double totalStaffShare = share * admins.size();\n"
    "        if (!Double.isFinite(share) || share <= 0D || !withdraw(totalStaffShare, owner)) {\n"
    "            return;\n"
    "        }\n"
    "        for (UUID adminId : admins) {\n"
    "            if (!deposit(share, Bukkit.getOfflinePlayer(adminId))) {\n"
    "                deposit(share, owner);\n"
    "            }\n"
    "        }\n"
    "    }\n\n"
    "    public static void transferOwner",
    "safe shared income")
write(path, text)


# Extend the retained audit record.
doc = ROOT / "docs" / "multi-item-shop-audit.md"
with doc.open("a", encoding="utf-8") as handle:
    handle.write("\n## Final polish\n\n"
                 "- Global settings derive their new value from current PDC state, so stale GUIs cannot reverse another administrator's change.\n"
                 "- Listing toggles and removal now surface persistence failures instead of presenting false success.\n"
                 "- Editing an existing listing no longer silently re-enables a direction that the owner globally disabled.\n"
                 "- Transactions reject null/AIR templates, guard missing economy providers, and parse shared-income metadata defensively.\n")

print("Applied final audited shop polish.")
